package com.hepdd.backpackenhance.integration;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** A stored item identity and its aggregate ME quantity. */
public final class WirelessItemEntry {

    public final ItemStack template;
    public final long amount;

    public WirelessItemEntry(ItemStack stack, long amount) {
        this.template = stack.copy();
        this.template.stackSize = 1;
        this.amount = amount;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof WirelessItemEntry)) return false;
        ItemStack stack = ((WirelessItemEntry) other).template;
        return template.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(template, stack);
    }

    @Override
    public int hashCode() {
        int hash = 31 * Item.getIdFromItem(template.getItem()) + template.getItemDamage();
        return 31 * hash + (template.hasTagCompound() ? template.getTagCompound()
            .hashCode() : 0);
    }
}
