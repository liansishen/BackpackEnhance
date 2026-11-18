package com.hepdd.backpackenhance.server.overlay;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

import com.darkona.adventurebackpack.inventory.InventoryBackpack;
import com.hepdd.backpackenhance.integration.BackpackInventoryAccess;
import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.integration.ForestryModeBridge;

import de.eydamos.backpack.item.ItemBackpackBase;
import de.eydamos.backpack.saves.BackpackSave;

public class OverlaySnapshotFactory {

    public List<OverlayTabSnapshot> build(EntityPlayerMP player, List<BackpackTab> tabs) {
        List<OverlayTabSnapshot> snapshots = new ArrayList<OverlayTabSnapshot>();
        for (BackpackTab tab : tabs) {
            OverlayTabSnapshot snapshot = snapshot(player, tab);
            if (snapshot != null) {
                snapshots.add(snapshot);
            }
        }
        return snapshots;
    }

    public OverlayTabSnapshot snapshot(EntityPlayerMP player, BackpackTab tab) {
        ItemStack liveStack = player.inventory.mainInventory[tab.playerSlot];
        if (liveStack == null) {
            return null;
        }

        IInventory inventory = createInventory(player, tab.kind, liveStack, tab.playerSlot);
        if (inventory == null) {
            return null;
        }

        int storageSlots = BackpackInventoryAccess.storageSlots(tab.kind, inventory, liveStack);
        int columns = BackpackInventoryAccess.columns(tab.kind, storageSlots, inventory, liveStack);
        List<OverlaySlotSnapshot> slots = new ArrayList<OverlaySlotSnapshot>();
        for (int i = 0; i < storageSlots; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack != null) {
                slots.add(new OverlaySlotSnapshot(i, stack.copy()));
            }
        }

        ForestryModeBridge.ModeState mode = tab.kind == BackpackKind.FORESTRY ? ForestryModeBridge.state(liveStack)
            : ForestryModeBridge.ModeState.NONE;
        ItemStack displayStack = stripRedundantStorageNbt(tab.kind, liveStack);
        if (displayStack == liveStack) {
            displayStack = liveStack.copy();
        }
        return new OverlayTabSnapshot(
            tab.tabId,
            tab.playerSlot,
            tab.kind,
            liveStack.getDisplayName(),
            columns,
            storageSlots,
            displayStack,
            slots,
            mode.modeId,
            mode.nextModeId,
            mode.available,
            mode.resupplyEnabled);
    }

    /**
     * Returns a lightweight copy of {@code stack} with large internal item-storage NBT stripped when
     * safe, preserving display name, skin/color/mode/UID and all non-storage metadata. Never mutates
     * the input {@code stack}.
     */
    public static ItemStack stripRedundantStorageNbt(BackpackKind kind, ItemStack stack) {
        if (stack == null || kind == null || !stack.hasTagCompound()) {
            return stack;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (kind == BackpackKind.FORESTRY) {
            if (!root.hasKey("Slots") && !root.hasKey("Items")) {
                return stack;
            }
            ItemStack copy = stack.copy();
            NBTTagCompound tag = copy.getTagCompound();
            tag.removeTag("Slots");
            tag.removeTag("Items");
            if (tag.hasNoTags()) {
                copy.setTagCompound(null);
            }
            return copy;
        }
        if (kind == BackpackKind.ADVENTURE) {
            NBTTagCompound srcWearable = null;
            if (root.hasKey("wearableData", Constants.NBT.TAG_COMPOUND)) {
                srcWearable = root.getCompoundTag("wearableData");
            }
            NBTTagCompound srcLegacy = null;
            if (root.hasKey("backpackData", Constants.NBT.TAG_COMPOUND)) {
                srcLegacy = root.getCompoundTag("backpackData");
            }
            boolean hasWearableInv = srcWearable != null && srcWearable.hasKey("inventory");
            boolean hasLegacyInv = srcLegacy != null && (srcLegacy.hasKey("inventory") || srcLegacy.hasKey("ABPItems"));
            if (!hasWearableInv && !hasLegacyInv) {
                return stack;
            }
            ItemStack copy = stack.copy();
            NBTTagCompound tag = copy.getTagCompound();
            if (hasWearableInv) {
                NBTTagCompound wearable = tag.getCompoundTag("wearableData");
                wearable.removeTag("inventory");
                if (wearable.hasNoTags()) {
                    tag.removeTag("wearableData");
                }
            }
            if (hasLegacyInv) {
                NBTTagCompound legacy = tag.getCompoundTag("backpackData");
                legacy.removeTag("inventory");
                legacy.removeTag("ABPItems");
                if (legacy.hasNoTags()) {
                    tag.removeTag("backpackData");
                }
            }
            if (tag.hasNoTags()) {
                copy.setTagCompound(null);
            }
            return copy;
        }
        return stack;
    }

    public IInventory createInventory(EntityPlayerMP player, BackpackKind kind, ItemStack stack, int playerSlot) {
        switch (kind) {
            case ADVENTURE:
                return new InventoryBackpack(stack);
            case BRADS:
            case BRADS_WORKBENCH:
            case BRADS_ENDER:
                return createBradsInventory(player, stack);
            case FORESTRY:
                return createForestryInventory(player, stack, playerSlot);
            default:
                return null;
        }
    }

    public void saveInventory(BackpackKind kind, ItemStack stack, IInventory inventory) {
        if ((kind == BackpackKind.BRADS || kind == BackpackKind.BRADS_WORKBENCH)
            && inventory instanceof de.eydamos.backpack.inventory.InventoryBackpack) {
            BackpackSave save = new BackpackSave(stack);
            ((de.eydamos.backpack.inventory.InventoryBackpack) inventory).writeToNBT(save);
            save.save();
        } else {
            inventory.markDirty();
        }
    }

    private IInventory createBradsInventory(EntityPlayerMP player, ItemStack stack) {
        IInventory inventory = ItemBackpackBase.getInventory(stack, player);
        if (inventory instanceof de.eydamos.backpack.inventory.InventoryBackpack) {
            BackpackSave save = new BackpackSave(stack);
            ((de.eydamos.backpack.inventory.InventoryBackpack) inventory).readFromNBT(save);
        }
        return inventory;
    }

    private IInventory createForestryInventory(EntityPlayerMP player, ItemStack stack, int playerSlot) {
        try {
            Class<?> adapterClass = Class
                .forName("com.hepdd.backpackenhance.integration.forestry.ForestryBackpackAccess");
            return (IInventory) adapterClass
                .getMethod(
                    "createInventory",
                    net.minecraft.entity.player.EntityPlayer.class,
                    ItemStack.class,
                    Integer.TYPE)
                .invoke(null, player, stack, Integer.valueOf(playerSlot));
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

}
