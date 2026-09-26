package com.hepdd.backpackenhance.integration.forestry;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import forestry.core.inventory.ItemLocation;
import forestry.storage.BackpackMode;
import forestry.storage.inventory.ItemInventoryBackpack;
import forestry.storage.inventory.ItemInventoryBackpackPaged;
import forestry.storage.items.ItemBackpack;
import forestry.storage.items.ItemBackpackNaturalist;

public final class ForestryBackpackAccess {

    public static final int NO_MODE = -1;

    private static final String UID_KEY = "UID";
    private static final int NBT_INT = 3;

    private ForestryBackpackAccess() {}

    public static IInventory createInventory(EntityPlayer player, ItemStack stack, int playerSlot) {
        if (player == null || stack == null || !(stack.getItem() instanceof ItemBackpack)) {
            return null;
        }

        ItemBackpack backpack = (ItemBackpack) stack.getItem();
        ItemLocation location = new ItemLocation(ItemLocation.Type.PLAYER_INVENTORY, playerSlot);
        if (backpack instanceof ItemBackpackNaturalist) {
            return new ItemInventoryBackpackPaged(
                player,
                backpack.getBackpackSize(),
                stack,
                (ItemBackpackNaturalist) backpack,
                location);
        }
        return new ItemInventoryBackpack(player, backpack.getBackpackSize(), stack, location);
    }

    public static int storageSlots(ItemStack stack, int fallback) {
        if (stack != null && stack.getItem() instanceof ItemBackpack) {
            return ((ItemBackpack) stack.getItem()).getBackpackSize();
        }
        return fallback;
    }

    public static int primaryColor(ItemStack stack, int fallback) {
        if (stack == null || !(stack.getItem() instanceof ItemBackpack)) {
            return fallback;
        }
        ItemBackpack backpack = (ItemBackpack) stack.getItem();
        if (backpack.getDefinition() == null) {
            return fallback;
        }
        return 0xFF000000 | (backpack.getDefinition()
            .getPrimaryColour() & 0xFFFFFF);
    }

    public static boolean isItemValid(ItemStack backpackStack, ItemStack candidate) {
        if (candidate == null || backpackStack == null || !(backpackStack.getItem() instanceof ItemBackpack)) {
            return candidate == null;
        }
        ItemBackpack backpack = (ItemBackpack) backpackStack.getItem();
        return backpack.getDefinition() != null && backpack.getDefinition()
            .isValidItem(candidate);
    }

    public static boolean supportsModeCycle(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBackpack;
    }

    public static int modeId(ItemStack stack) {
        if (!supportsModeCycle(stack)) {
            return NO_MODE;
        }
        return ItemBackpack.getMode(stack)
            .ordinal();
    }

    public static int nextModeId(ItemStack stack) {
        int current = modeId(stack);
        if (current < 0) {
            return NO_MODE;
        }
        int next = current + 1;
        if (!forestry.core.config.Config.enableBackpackResupply && next == BackpackMode.RESUPPLY.ordinal()) {
            next++;
        }
        return next % BackpackMode.values().length;
    }

    public static boolean isResupplyEnabled() {
        return forestry.core.config.Config.enableBackpackResupply;
    }

    public static boolean hasUid(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag != null && tag.hasKey(UID_KEY, NBT_INT);
    }

    public static int uid(ItemStack stack) {
        NBTTagCompound tag = stack == null ? null : stack.getTagCompound();
        return tag == null ? 0 : tag.getInteger(UID_KEY);
    }

    public static boolean cycleMode(ItemStack stack, int expectedMode, boolean expectedHasUid, int expectedUid) {
        if (!supportsModeCycle(stack) || modeId(stack) != expectedMode) {
            return false;
        }
        if (expectedHasUid != hasUid(stack) || expectedHasUid && uid(stack) != expectedUid) {
            return false;
        }
        stack.setItemDamage(nextModeId(stack));
        return true;
    }
}
