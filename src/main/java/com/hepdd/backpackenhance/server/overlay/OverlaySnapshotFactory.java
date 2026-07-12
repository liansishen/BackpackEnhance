package com.hepdd.backpackenhance.server.overlay;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.darkona.adventurebackpack.inventory.InventoryBackpack;
import com.hepdd.backpackenhance.integration.BackpackInventoryAccess;
import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.integration.BackpackTab;

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

        IInventory inventory = createInventory(player, tab.kind, liveStack);
        if (inventory == null) {
            return null;
        }

        int storageSlots = BackpackInventoryAccess.storageSlots(tab.kind, inventory, liveStack);
        int columns = BackpackInventoryAccess.columns(tab.kind, storageSlots, inventory, liveStack);
        List<OverlaySlotSnapshot> slots = new ArrayList<OverlaySlotSnapshot>(storageSlots);
        for (int i = 0; i < storageSlots; i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            slots.add(new OverlaySlotSnapshot(i, stack == null ? null : stack.copy()));
        }

        return new OverlayTabSnapshot(
            tab.tabId,
            tab.playerSlot,
            tab.kind,
            liveStack.getDisplayName(),
            columns,
            storageSlots,
            liveStack.copy(),
            slots);
    }

    public IInventory createInventory(EntityPlayerMP player, BackpackKind kind, ItemStack stack) {
        switch (kind) {
            case ADVENTURE:
                return new InventoryBackpack(stack);
            case BRADS:
            case BRADS_WORKBENCH:
            case BRADS_ENDER:
                return createBradsInventory(player, stack);
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

}
