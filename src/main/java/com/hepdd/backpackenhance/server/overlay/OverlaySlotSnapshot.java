package com.hepdd.backpackenhance.server.overlay;

import net.minecraft.item.ItemStack;

public class OverlaySlotSnapshot {

    public final int slot;
    public final ItemStack stack;

    public OverlaySlotSnapshot(int slot, ItemStack stack) {
        this.slot = slot;
        this.stack = stack;
    }
}
