package me.darkeyedragon.randomtp.world.location;

import io.papermc.lib.PaperLib;
import me.darkeyedragon.randomtp.RandomTeleport;
import me.darkeyedragon.randomtp.validator.ChunkValidator;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class LocationSearcher {

    private static final String OCEAN = "ocean";
    private final EnumSet<Material> blacklistMaterial;
    private final Set<Biome> blacklistBiome;
    private final RandomTeleport plugin;
    private boolean useWorldBorder;

    /**
     * A simple utility class to help with {@link Location}
     */
    public LocationSearcher(RandomTeleport plugin) {
        this.plugin = plugin;
        //Illegal material types
        blacklistMaterial = EnumSet.of(
                Material.LAVA,
                Material.CACTUS,
                Material.FIRE,
                Material.TRIPWIRE,
                Material.ACACIA_PRESSURE_PLATE,
                Material.BIRCH_PRESSURE_PLATE,
                Material.JUNGLE_PRESSURE_PLATE,
                Material.OAK_PRESSURE_PLATE,
                Material.SPRUCE_PRESSURE_PLATE,
                Material.STONE_PRESSURE_PLATE,
                Material.DARK_OAK_PRESSURE_PLATE,
                Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
                Material.LIGHT_WEIGHTED_PRESSURE_PLATE
        );
        blacklistBiome = new HashSet<>();

        //Illegal biomes
        for (Biome biome : Biome.values()) {
            if (biome.toString().toLowerCase().contains(OCEAN)) {
                blacklistBiome.add(biome);
            }
        }
    }

    /* This is the final method that will be called from the other end, to get a location */
    public CompletableFuture<Location> getRandomLocation(WorldConfigSection worldConfigSection) {
        CompletableFuture<Location> location = pickRandomLocation(worldConfigSection);
        return location.thenCompose((loc) -> {
            if (loc == null) {
                return getRandomLocation(worldConfigSection);
            } else return CompletableFuture.completedFuture(loc);
        });
    }

    /*Pick a random location based on chunks*/
    private CompletableFuture<Location> pickRandomLocation(WorldConfigSection worldConfigSection) {
        CompletableFuture<Chunk> chunk = getRandomChunk(worldConfigSection);
        return chunk.thenApply(this::getRandomLocationFromChunk);
    }

    /* Will search through the chunk to find a location that is safe, returning null if none is found. */
    public Location getRandomLocationFromChunk(Chunk chunk) {
        for (int x = 8; x < 16; x++) {
            for (int z = 8; z < 16; z++) {
                Block block = chunk.getWorld().getHighestBlockAt((chunk.getX() << 4) + x, (chunk.getZ() << 4) + z);
                if (isSafeLocation(block.getLocation())) {
                    return block.getLocation();
                }
            }
        }
        return null;
    }

    private CompletableFuture<Chunk> getRandomChunk(WorldConfigSection worldConfigSection) {
        CompletableFuture<Chunk> chunkFuture = getRandomChunkAsync(worldConfigSection);
        return chunkFuture.thenCompose((chunk) -> {
            boolean isSafe = isSafeChunk(chunk);
            if (!isSafe) {
                return getRandomChunk(worldConfigSection);
            } else return CompletableFuture.completedFuture(chunk);
        });
    }

    private CompletableFuture<Chunk> getRandomChunkAsync(WorldConfigSection worldConfigSection) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int chunkRadius = worldConfigSection.getRadius() / 16;
        int chunkOffsetX = worldConfigSection.getX() / 16;
        int chunkOffsetZ = worldConfigSection.getZ() / 16;
        int x = rnd.nextInt(-chunkRadius, chunkRadius);
        int z = rnd.nextInt(-chunkRadius, chunkRadius);
        return PaperLib.getChunkAtAsync(worldConfigSection.getWorld(), x + chunkOffsetX, z + chunkOffsetZ);
    }

    public boolean isSafeLocation(Location loc) {
        World world = loc.getWorld();
        if (world == null) return false;
        //Check 2 blocks to see if its safe for the player to stand. Since getHighestBlockAt doesnt include trees
        if (loc.clone().add(0, 2, 0).getBlock().getType() != Material.AIR) return false;
        if (blacklistMaterial.contains(loc.getBlock().getType())) return false;
        for (ChunkValidator validator : plugin.getValidatorList()) {
            if (!validator.isValid(loc)) {
                return false;
            }
        }
        Bukkit.getLogger().info("Block : " + loc.getBlock().getBiome().name() + ": " + loc.getBlock().getType().name());
        Block offsetDown = loc.getBlock().getRelative(BlockFace.DOWN);
        Block offsetUp = loc.getBlock().getRelative(BlockFace.UP);
        Bukkit.getLogger().info("Block Down: " + offsetDown.getBiome().name() + ": " + offsetDown.getType().name());
        Bukkit.getLogger().info("Block Up: " + offsetUp.getBiome().name() + ": " + offsetUp.getType().name());
        return true;
    }

    private boolean isSafeChunk(Chunk chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Block block = chunk.getWorld().getHighestBlockAt((chunk.getX() << 4) + x, (chunk.getZ() << 4) + z);
                if (blacklistBiome.contains(block.getBiome())) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean isUseWorldBorder() {
        return useWorldBorder;
    }

    public void setUseWorldBorder(boolean useWorldBorder) {
        this.useWorldBorder = useWorldBorder;
    }

    public RandomTeleport getPlugin() {
        return plugin;
    }
}
