package me.darkeyedragon.randomtp.bukkit.validator;

public enum Validator {
    FACTIONS("Factions"), WORLD_GUARD("WorldGuard"), GRIEF_PREVENTION("GriefPrevention");

    final String name;

    Validator(String name) {
        this.name = name;
    }

    public static Validator getValidator(String name){
        for (Validator validator : Validator.values()) {
            if(validator.name.equalsIgnoreCase(name)){
                return validator;
            }
        }
        throw new IllegalArgumentException("Invalid name");
    }
}
