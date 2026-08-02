package com.hepdd.backpackenhance.integration;

import net.minecraft.item.ItemStack;

public class BackpackTab {

    public final int tabId;
    public final int playerSlot;
    public final BackpackKind kind;
    public final ItemStack stack;
    public final String displayName;
    public final int columns;
    public final int storageSlots;
    public final int modeId;
    public final int nextModeId;
    public final boolean modeCycleAvailable;
    public final boolean resupplyEnabled;
    private ItemStack[] slotStacks;

    public BackpackTab(int tabId, int playerSlot, BackpackKind kind, ItemStack stack, int columns, int storageSlots) {
        this(tabId, playerSlot, kind, stack, columns, storageSlots, -1, -1, false, false);
    }

    public BackpackTab(int tabId, int playerSlot, BackpackKind kind, ItemStack stack, int columns, int storageSlots,
        int modeId, int nextModeId, boolean modeCycleAvailable, boolean resupplyEnabled) {
        this.tabId = tabId;
        this.playerSlot = playerSlot;
        this.kind = kind;
        this.stack = stack;
        this.displayName = stack.getDisplayName();
        this.columns = columns;
        this.storageSlots = storageSlots;
        this.modeId = modeId;
        this.nextModeId = nextModeId;
        this.modeCycleAvailable = modeCycleAvailable;
        this.resupplyEnabled = resupplyEnabled;
    }

    public int getRows() {
        return (storageSlots + columns - 1) / columns;
    }

    /** Tab accent strip: dye/skin body color from the backpack item when possible. */
    public int getAccentColor() {
        return BackpackTabColors.resolve(kind, stack);
    }

    public void setSlotStacks(ItemStack[] slotStacks) {
        this.slotStacks = slotStacks;
    }

    public ItemStack getSlotStack(int slot) {
        if (slotStacks == null || slot < 0 || slot >= slotStacks.length) {
            return null;
        }
        return slotStacks[slot];
    }

    /** Client-side prediction / local rewrite of a single slot. */
    public void setSlotStack(int slot, ItemStack stack) {
        if (slot < 0 || slot >= storageSlots) {
            return;
        }
        if (slotStacks == null) {
            slotStacks = new ItemStack[storageSlots];
        }
        slotStacks[slot] = stack == null ? null : stack.copy();
    }
}
