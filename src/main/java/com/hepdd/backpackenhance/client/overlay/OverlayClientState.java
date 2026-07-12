package com.hepdd.backpackenhance.client.overlay;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.server.overlay.OverlaySlotSnapshot;
import com.hepdd.backpackenhance.server.overlay.OverlayTabSnapshot;

/**
 * Client mirror of server overlay snapshots. Tab stacks and cursor are applied together so a
 * place/take never briefly shows the item both in a slot and on the cursor.
 */
public final class OverlayClientState {

    private static List<BackpackTab> syncedTabs = new ArrayList<BackpackTab>();
    private static ItemStack pendingCursor;
    private static boolean hasPendingCursor;
    private static boolean hasState;

    private OverlayClientState() {}

    public static void setState(List<OverlayTabSnapshot> snapshots, ItemStack cursorStack) {
        List<BackpackTab> converted = new ArrayList<BackpackTab>(snapshots.size());
        for (OverlayTabSnapshot snapshot : snapshots) {
            BackpackTab tab = new BackpackTab(
                snapshot.tabId,
                snapshot.playerSlot,
                snapshot.kind,
                snapshot.backpackStack,
                snapshot.columns,
                snapshot.storageSlots);
            ItemStack[] stacks = new ItemStack[snapshot.storageSlots];
            for (OverlaySlotSnapshot slot : snapshot.slots) {
                if (slot.slot >= 0 && slot.slot < stacks.length) {
                    stacks[slot.slot] = slot.stack;
                }
            }
            tab.setSlotStacks(stacks);
            converted.add(tab);
        }
        syncedTabs = converted;
        // Hold cursor until tabs are applied on the client tick (same apply step).
        pendingCursor = cursorStack == null ? null : cursorStack.copy();
        hasPendingCursor = true;
        hasState = true;
    }

    /**
     * Apply pending cursor to the local player. Call in the same tick as tab list refresh.
     */
    public static void applyPendingCursor() {
        if (!hasPendingCursor) {
            return;
        }
        hasPendingCursor = false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.inventory.setItemStack(pendingCursor == null ? null : pendingCursor.copy());
        }
        pendingCursor = null;
    }

    public static List<BackpackTab> getTabs() {
        return new ArrayList<BackpackTab>(syncedTabs);
    }

    public static boolean hasState() {
        return hasState;
    }

    public static void clear() {
        syncedTabs = new ArrayList<BackpackTab>();
        pendingCursor = null;
        hasPendingCursor = false;
        hasState = false;
    }
}
