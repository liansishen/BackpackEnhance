package com.hepdd.backpackenhance.server.overlay;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.integration.BackpackKind;

public class OverlayTabSnapshot {

    public final int tabId;
    public final int playerSlot;
    public final BackpackKind kind;
    public final String displayName;
    public final int columns;
    public final int storageSlots;
    public final ItemStack backpackStack;
    public final List<OverlaySlotSnapshot> slots;

    public OverlayTabSnapshot(int tabId, int playerSlot, BackpackKind kind, String displayName, int columns,
        int storageSlots, ItemStack backpackStack, List<OverlaySlotSnapshot> slots) {
        this.tabId = tabId;
        this.playerSlot = playerSlot;
        this.kind = kind;
        this.displayName = displayName;
        this.columns = columns;
        this.storageSlots = storageSlots;
        this.backpackStack = backpackStack;
        this.slots = slots;
    }
}
