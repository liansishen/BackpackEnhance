package com.hepdd.backpackenhance.integration;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.darkona.adventurebackpack.inventory.SlotBackpack;

import de.eydamos.backpack.saves.BackpackSave;

public final class BackpackInventoryAccess {

    private BackpackInventoryAccess() {}

    public static int storageSlots(BackpackKind kind, IInventory inventory, ItemStack backpackStack) {
        if (kind == BackpackKind.ADVENTURE) {
            return Math.min(48, inventory.getSizeInventory());
        }
        if (isBradsStorage(kind) && backpackStack != null) {
            BackpackSave save = new BackpackSave(backpackStack);
            if (!save.isUninitialized()) {
                return Math.min(save.getSize(), inventory.getSizeInventory());
            }
        }
        return inventory.getSizeInventory();
    }

    public static int storageSlots(BackpackKind kind, ItemStack backpackStack) {
        if (kind == BackpackKind.ADVENTURE) {
            return kind.storageSlots;
        }
        if (isBradsStorage(kind) && backpackStack != null) {
            BackpackSave save = new BackpackSave(backpackStack);
            if (!save.isUninitialized()) {
                return save.getSize();
            }
        }
        return kind.storageSlots;
    }

    public static int columns(BackpackKind kind, int storageSlots, IInventory inventory, ItemStack backpackStack) {
        if (kind == BackpackKind.ADVENTURE) {
            return 8;
        }
        if (isBradsStorage(kind) && backpackStack != null) {
            BackpackSave save = new BackpackSave(backpackStack);
            if (!save.isUninitialized()) {
                return Math.max(1, save.getSlotsPerRow());
            }
        }
        return storageSlots >= 9 ? 9 : Math.max(1, storageSlots);
    }

    public static boolean isItemValid(BackpackKind kind, IInventory inventory, int slot, ItemStack stack) {
        if (stack == null) {
            return true;
        }
        if (kind == BackpackKind.ADVENTURE) {
            return SlotBackpack.isValidItem(stack);
        }
        return inventory.isItemValidForSlot(slot, stack);
    }

    private static boolean isBradsStorage(BackpackKind kind) {
        return kind == BackpackKind.BRADS || kind == BackpackKind.BRADS_WORKBENCH || kind == BackpackKind.BRADS_ENDER;
    }
}
