package com.hepdd.backpackenhance.integration.nei;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

/**
 * Session ledger of items borrowed from the active overlay tab into the player inventory
 * during NEI bookmark auto-craft. Settled when {@code AutoCraftingManager.processing()} ends.
 */
public final class NeiBorrowLedger {

    private final List<Entry> entries = new ArrayList<Entry>();

    public void beginSession() {
        entries.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Record a borrow. Merges into an existing entry when tab/slot match and stacks are
     * craft-compatible.
     */
    public void record(int tabId, int sourceSlot, ItemStack template, int amount) {
        if (template == null || amount <= 0) {
            return;
        }
        for (Entry existing : entries) {
            if (existing.tabId == tabId && existing.sourceSlot == sourceSlot && sameKind(existing.template, template)) {
                existing.amount += amount;
                return;
            }
        }
        ItemStack copy = template.copy();
        copy.stackSize = Math.max(1, Math.min(amount, Math.max(1, template.getMaxStackSize())));
        entries.add(new Entry(tabId, sourceSlot, copy, amount));
    }

    /** Snapshot current entries and clear the ledger. */
    public List<Entry> drain() {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Entry> out = new ArrayList<Entry>(entries);
        entries.clear();
        return out;
    }

    private static boolean sameKind(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.getItem() != b.getItem()) {
            return false;
        }
        if (a.isItemStackDamageable() || b.isItemStackDamageable()
            || a.getMaxStackSize() == 1 && b.getMaxStackSize() == 1) {
            return true;
        }
        return a.getItemDamage() == b.getItemDamage() && ItemStack.areItemStackTagsEqual(a, b);
    }

    public static final class Entry {

        public final int tabId;
        public final int sourceSlot;
        /** Borrow-time template (for craft matching); not necessarily post-craft NBT. */
        public final ItemStack template;
        public int amount;

        Entry(int tabId, int sourceSlot, ItemStack template, int amount) {
            this.tabId = tabId;
            this.sourceSlot = sourceSlot;
            this.template = template;
            this.amount = amount;
        }
    }
}
