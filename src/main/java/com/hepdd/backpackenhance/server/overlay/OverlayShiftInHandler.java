package com.hepdd.backpackenhance.server.overlay;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.integration.BackpackInventoryAccess;
import com.hepdd.backpackenhance.integration.BackpackScanner;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.integration.WirelessOverlay;
import com.hepdd.backpackenhance.net.NetworkHandler;
import com.hepdd.backpackenhance.net.packet.PacketOverlayState;

public class OverlayShiftInHandler {

    private static final BackpackScanner SCANNER = new BackpackScanner();
    private static final OverlaySnapshotFactory SNAPSHOT_FACTORY = new OverlaySnapshotFactory();

    private OverlayShiftInHandler() {}

    /**
     * Server: shift into overlay when expanded. Client: suppress local prediction (reflection so
     * dedicated servers do not load client classes).
     */
    public static boolean handleShiftClick(EntityPlayer player, Slot sourceSlot) {
        if (player == null || sourceSlot == null || !sourceSlot.getHasStack()) {
            return false;
        }
        if (!OverlaySlotUtil.shouldPrioritizeShiftFrom(player, sourceSlot)) {
            return false;
        }
        if (player instanceof EntityPlayerMP) {
            return tryShiftIntoOverlay((EntityPlayerMP) player, sourceSlot);
        }
        if (player.worldObj != null && player.worldObj.isRemote) {
            return shouldSuppressClientShiftPredict(player, sourceSlot);
        }
        return false;
    }

    /**
     * Shift into the active overlay tab when expanded.
     * <ul>
     * <li>From chest/machine slots → overlay</li>
     * <li>From player inventory → overlay only on standalone {@code ContainerPlayer}</li>
     * <li>With a chest/machine open, player-bar shift is left to vanilla (container first)</li>
     * </ul>
     * Called from protocol + container mixins.
     */
    public static boolean tryShiftIntoOverlay(EntityPlayerMP player, Slot sourceSlot) {
        if (!OverlaySessionTracker.shouldPrioritizeShift(player) || sourceSlot == null
            || !OverlaySlotUtil.shouldPrioritizeShiftFrom(player, sourceSlot)
            || !sourceSlot.canTakeStack(player)
            || !sourceSlot.getHasStack()) {
            return false;
        }

        Integer activeTabId = OverlaySessionTracker.getActiveTab(player);
        if (activeTabId != null && WirelessOverlay.isWirelessTab(activeTabId.intValue())) {
            return WirelessOverlay.backend != null && WirelessOverlay.backend.shiftInto(player, sourceSlot);
        }
        List<BackpackTab> tabs = SCANNER.scan(player);
        if (tabs.isEmpty()) {
            return false;
        }

        BackpackTab activeTab = null;
        if (activeTabId != null && activeTabId.intValue() >= 0) {
            activeTab = findTab(tabs, activeTabId.intValue());
        }
        if (activeTab == null) {
            activeTab = tabs.get(0);
            OverlaySessionTracker.setActiveTab(player, activeTab.tabId);
        }

        ItemStack backpackStack = player.inventory.mainInventory[activeTab.playerSlot];
        if (backpackStack == null) {
            return false;
        }

        IInventory backpackInventory = SNAPSHOT_FACTORY
            .createInventory(player, activeTab.kind, backpackStack, activeTab.playerSlot);
        if (backpackInventory == null) {
            return false;
        }

        ItemStack original = sourceSlot.getStack();
        if (original == null) {
            return false;
        }

        ItemStack remaining = original.copy();
        int before = remaining.stackSize;
        int storageSlots = BackpackInventoryAccess.storageSlots(activeTab.kind, backpackInventory, backpackStack);
        remaining = insertIntoInventory(activeTab, backpackInventory, remaining, storageSlots);

        int afterBackpack = remaining == null ? 0 : remaining.stackSize;
        int toBackpack = before - afterBackpack;
        // Only intercept when the backpack actually accepted something.
        if (toBackpack <= 0) {
            return false;
        }

        // Remainder after backpack may go to player inventory (design §12.3).
        if (remaining != null && remaining.stackSize > 0) {
            remaining = insertIntoPlayerInventory(player, remaining);
        }

        if (remaining == null || remaining.stackSize <= 0) {
            sourceSlot.putStack(null);
        } else {
            ItemStack left = original.copy();
            left.stackSize = remaining.stackSize;
            sourceSlot.putStack(left);
        }
        sourceSlot.onSlotChanged();
        SNAPSHOT_FACTORY.saveInventory(activeTab.kind, backpackStack, backpackInventory);
        player.inventory.markDirty();
        player.openContainer.detectAndSendChanges();
        syncOverlay(player, tabs);
        return true;
    }

    private static boolean shouldSuppressClientShiftPredict(EntityPlayer player, Slot sourceSlot) {
        try {
            Class<?> controllerClass = Class.forName("com.hepdd.backpackenhance.client.overlay.OverlayController");
            Object active = controllerClass.getMethod("hasActiveOverlaySession")
                .invoke(null);
            if (!(active instanceof Boolean) || !((Boolean) active).booleanValue()) {
                return false;
            }
            return OverlaySlotUtil.shouldPrioritizeShiftFrom(player, sourceSlot);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void syncOverlay(EntityPlayerMP player, List<BackpackTab> tabs) {
        List<OverlayTabSnapshot> snapshots = SNAPSHOT_FACTORY.build(player, tabs);
        NetworkHandler.INSTANCE.sendTo(new PacketOverlayState(snapshots), player);
    }

    private static BackpackTab findTab(List<BackpackTab> tabs, int tabId) {
        for (BackpackTab tab : tabs) {
            if (tab.tabId == tabId) {
                return tab;
            }
        }
        return null;
    }

    private static ItemStack insertIntoInventory(BackpackTab tab, IInventory inventory, ItemStack stack, int maxSlots) {
        if (stack == null) {
            return null;
        }

        int slots = Math.min(maxSlots, inventory.getSizeInventory());
        for (int i = 0; i < slots && stack.stackSize > 0; i++) {
            ItemStack existing = inventory.getStackInSlot(i);
            if (existing != null && OverlaySlotUtil.canMerge(existing, stack)
                && BackpackInventoryAccess.isItemValid(tab.kind, inventory, i, stack)) {
                int limit = Math.min(inventory.getInventoryStackLimit(), existing.getMaxStackSize());
                int move = Math.min(stack.stackSize, limit - existing.stackSize);
                if (move > 0) {
                    existing.stackSize += move;
                    stack.stackSize -= move;
                    inventory.setInventorySlotContents(i, existing);
                }
            }
        }

        for (int i = 0; i < slots && stack.stackSize > 0; i++) {
            if (inventory.getStackInSlot(i) == null
                && BackpackInventoryAccess.isItemValid(tab.kind, inventory, i, stack)) {
                int limit = Math.min(inventory.getInventoryStackLimit(), stack.getMaxStackSize());
                int move = Math.min(stack.stackSize, limit);
                ItemStack placed = stack.copy();
                placed.stackSize = move;
                inventory.setInventorySlotContents(i, placed);
                stack.stackSize -= move;
            }
        }

        return stack.stackSize <= 0 ? null : stack;
    }

    private static ItemStack insertIntoPlayerInventory(EntityPlayerMP player, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        stack = mergeIntoPlayerRange(player, stack, 0, 9);
        if (stack == null) {
            return null;
        }
        return mergeIntoPlayerRange(player, stack, 9, 36);
    }

    private static ItemStack mergeIntoPlayerRange(EntityPlayerMP player, ItemStack stack, int startInclusive,
        int endExclusive) {
        for (int i = startInclusive; i < endExclusive && stack.stackSize > 0; i++) {
            ItemStack existing = player.inventory.mainInventory[i];
            if (existing != null && OverlaySlotUtil.canMerge(existing, stack)) {
                int move = Math.min(stack.stackSize, existing.getMaxStackSize() - existing.stackSize);
                if (move > 0) {
                    existing.stackSize += move;
                    stack.stackSize -= move;
                }
            }
        }
        for (int i = startInclusive; i < endExclusive && stack.stackSize > 0; i++) {
            if (player.inventory.mainInventory[i] == null) {
                player.inventory.mainInventory[i] = stack.copy();
                stack.stackSize = 0;
            }
        }
        return stack.stackSize <= 0 ? null : stack;
    }
}
