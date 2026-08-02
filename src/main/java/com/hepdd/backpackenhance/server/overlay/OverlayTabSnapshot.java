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
    public final int modeId;
    public final int nextModeId;
    public final boolean modeCycleAvailable;
    public final boolean resupplyEnabled;

    public OverlayTabSnapshot(int tabId, int playerSlot, BackpackKind kind, String displayName, int columns,
        int storageSlots, ItemStack backpackStack, List<OverlaySlotSnapshot> slots) {
        this(tabId, playerSlot, kind, displayName, columns, storageSlots, backpackStack, slots, -1, -1, false, false);
    }

    public OverlayTabSnapshot(int tabId, int playerSlot, BackpackKind kind, String displayName, int columns,
        int storageSlots, ItemStack backpackStack, List<OverlaySlotSnapshot> slots, int modeId, int nextModeId,
        boolean modeCycleAvailable, boolean resupplyEnabled) {
        this.tabId = tabId;
        this.playerSlot = playerSlot;
        this.kind = kind;
        this.displayName = displayName;
        this.columns = columns;
        this.storageSlots = storageSlots;
        this.backpackStack = backpackStack;
        this.slots = slots;
        this.modeId = modeId;
        this.nextModeId = nextModeId;
        this.modeCycleAvailable = modeCycleAvailable;
        this.resupplyEnabled = resupplyEnabled;
    }
}
