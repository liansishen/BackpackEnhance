package com.hepdd.backpackenhance.client.overlay;

import net.minecraft.item.ItemStack;

public class OverlaySlotClick {

    public final int tabId;
    public final int slotIndex;
    public final int generation;
    public final ItemStack template;

    public OverlaySlotClick(int tabId, int slotIndex) {
        this(tabId, slotIndex, 0, null);
    }

    public OverlaySlotClick(int tabId, int slotIndex, int generation, ItemStack template) {
        this.tabId = tabId;
        this.slotIndex = slotIndex;
        this.generation = generation;
        this.template = template == null ? null : template.copy();
    }
}
