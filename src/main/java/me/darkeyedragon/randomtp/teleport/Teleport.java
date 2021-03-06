package me.darkeyedragon.randomtp.teleport;

import io.papermc.lib.PaperLib;
import me.darkeyedragon.randomtp.RandomTeleport;
import me.darkeyedragon.randomtp.config.ConfigHandler;
import me.darkeyedragon.randomtp.config.data.ConfigMessage;
import me.darkeyedragon.randomtp.eco.EcoFactory;
import me.darkeyedragon.randomtp.eco.EcoHandler;
import me.darkeyedragon.randomtp.exception.EcoNotSupportedException;
import me.darkeyedragon.randomtp.failsafe.DeathTracker;
import me.darkeyedragon.randomtp.util.MessageUtil;
import me.darkeyedragon.randomtp.world.location.WorldConfigSection;
import me.darkeyedragon.randomtp.world.location.search.LocationSearcher;
import me.darkeyedragon.randomtp.world.location.search.LocationSearcherFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.concurrent.atomic.AtomicBoolean;

public class Teleport {

    private final RandomTeleport plugin;
    private final TeleportProperty property;
    private final ConfigHandler configHandler;
    private final Player player;
    private EcoHandler ecoHandler;

    public Teleport(RandomTeleport plugin, TeleportProperty property) {
        this.plugin = plugin;
        this.property = property;
        this.configHandler = property.getConfigHandler();
        this.player = property.getPlayer();
    }

    public void random() {
        final long delay;
        double price = configHandler.getConfigEconomy().getPrice();
        ConfigMessage configMessage = configHandler.getConfigMessage();

        if (property.isUseEco()) {
            try {
                ecoHandler = EcoFactory.getInstance();
                if (!ecoHandler.hasEnough(player, price)) {
                    MessageUtil.sendMessage(player, configMessage.getEconomy().getInsufficientFunds());
                    return;
                }
            } catch (EcoNotSupportedException e) {
                property.getCommandSender().sendMessage(ChatColor.RED + "Economy based features are disabled. Vault not found. Set the rtp cost to 0 or install vault.");
                Bukkit.getLogger().severe("Economy based features are disabled. Vault not found. Set the rtp cost to 0 or install vault.");
            }

        }
        //Teleport instantly if the command sender has bypass permission
        if (property.isIgnoreTeleportDelay()) {
            delay = 0;
        } else {
            delay = configHandler.getConfigTeleport().getDelay();
        }
        // Check if the player still has a cooldown active.
        if (property.getCooldown() > 0 && plugin.getCooldowns().containsKey(player.getUniqueId()) && !property.isBypassCooldown()) {
            long lastTp = plugin.getCooldowns().get(player.getUniqueId());
            long remaining = lastTp + property.getCooldown() - System.currentTimeMillis();
            boolean ableToTp = remaining < 0;
            if (!ableToTp) {
                MessageUtil.sendMessage(player, configMessage.getCountdown(remaining));
                return;
            }
        }
        if (delay == 0) {
            teleport();
        } else {
            MessageUtil.sendMessage(player, configMessage.getInitTeleportDelay(delay));
            AtomicBoolean complete = new AtomicBoolean(false);
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                complete.set(true);
                teleport();
            }, delay).getTaskId();
            Location originalLoc = player.getLocation().clone();
            if (configHandler.getConfigTeleport().isCancelOnMove()) {
                Bukkit.getScheduler().runTaskTimer(plugin, bukkitTask -> {
                    Location currentLoc = player.getLocation();
                    if (complete.get()) {
                        bukkitTask.cancel();
                    } else if ((originalLoc.getX() != currentLoc.getX() || originalLoc.getY() != currentLoc.getY() || originalLoc.getZ() != currentLoc.getZ())) {
                        Bukkit.getScheduler().cancelTask(taskId);
                        bukkitTask.cancel();
                        MessageUtil.sendMessage(player, configMessage.getTeleportCanceled());
                    }
                }, 0, 5L);
            }
        }
    }

    private void addToDeathTimer(Player player) {
        DeathTracker tracker = plugin.getDeathTracker();
        if (tracker.contains(player)) {
            tracker.getBukkitTask(player).cancel();
            tracker.remove(player);
        }
        plugin.getDeathTracker().add(player, configHandler.getConfigTeleport().getDeathTimer());
    }

    private void drawWarpParticles(Player player) {
        Location spawnLoc = player.getEyeLocation().add(player.getLocation().getDirection());
        player.getWorld().spawnParticle(Particle.CLOUD, spawnLoc, 20);
    }

    private void teleport() {
        Location location = plugin.getWorldQueue().popLocation(property.getWorld());
        ConfigMessage configMessage = configHandler.getConfigMessage();
        if (location == null) {
            MessageUtil.sendMessage(property.getCommandSender(), configMessage.getDepletedQueue());
            return;
        }
        PaperLib.getChunkAtAsync(location).thenAccept(chunk -> {
            LocationSearcher baseLocationSearcher = LocationSearcherFactory.getLocationSearcher(property.getWorld(), plugin);
            if (!baseLocationSearcher.isSafe(location)) {
                random();
                return;
            }
            Block block = chunk.getWorld().getBlockAt(location);
            Location loc = block.getLocation().add(0.5, 1.5, 0.5);
            plugin.getCooldowns().put(player.getUniqueId(), System.currentTimeMillis());
            drawWarpParticles(player);
            PaperLib.teleportAsync(player, loc);
            if (configHandler.getConfigTeleport().getDeathTimer() > 0) {
                addToDeathTimer(player);
            }
            if (property.isUseEco() && EcoFactory.isUseEco()) {
                ecoHandler.makePayment(player, configHandler.getConfigEconomy().getPrice());
                MessageUtil.sendMessage(player, configMessage.getEconomy().getPayment());
            }
            drawWarpParticles(player);
            MessageUtil.sendMessage(player, configMessage.getTeleport(location));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                WorldConfigSection worldConfigSection = plugin.getLocationFactory().getWorldConfigSection(property.getWorld());
                plugin.getWorldQueue().get(property.getWorld()).generate(worldConfigSection, 1);
            }, configHandler.getConfigQueue().getInitDelay());
        });
    }
}


