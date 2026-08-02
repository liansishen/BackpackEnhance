package com.hepdd.backpackenhance.integration;

public enum BackpackKind {

    ADVENTURE("adventurebackpack", 8, 48, 0xFF4C8C2B),
    BRADS("backpack", 9, 27, 0xFF7A4C2A),
    BRADS_WORKBENCH("backpack", 9, 27, 0xFF9A6A34),
    BRADS_ENDER("backpack", 9, 27, 0xFF6B3FA0),
    FORESTRY("forestry", 5, 15, 0xFF967047);

    public final String modKey;
    public final int columns;
    public final int storageSlots;
    public final int color;

    BackpackKind(String modKey, int columns, int storageSlots, int color) {
        this.modKey = modKey;
        this.columns = columns;
        this.storageSlots = storageSlots;
        this.color = color;
    }
}
