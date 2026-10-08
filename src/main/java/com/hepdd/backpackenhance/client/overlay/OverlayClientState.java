package com.hepdd.backpackenhance.client.overlay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.integration.WirelessItemEntry;
import com.hepdd.backpackenhance.integration.WirelessOverlay;
import com.hepdd.backpackenhance.net.packet.PacketWirelessState;
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
    private static int cursorRevision;
    private static boolean hasState;
    private static boolean dirty;
    private static int wirelessSession = -1;
    private static final Queue<Runnable> incoming = new ConcurrentLinkedQueue<Runnable>();
    private static final Map<Integer, BackpackTab> wirelessTabs = new LinkedHashMap<Integer, BackpackTab>();
    private static final Map<Integer, Map<WirelessItemEntry, WirelessItemEntry>> wirelessItems = new LinkedHashMap<Integer, Map<WirelessItemEntry, WirelessItemEntry>>();

    public static int beginCursorAction() {
        invalidateCursor();
        return cursorRevision;
    }

    public static void invalidateCursor() {
        cursorRevision++;
        pendingCursor = null;
        hasPendingCursor = false;
    }

    public static void expectWirelessSession(int session) {
        wirelessSession = session;
    }

    public static void tick() {
        Runnable update;
        while ((update = incoming.poll()) != null) update.run();
    }

    public static boolean consumeDirty() {
        boolean result = dirty;
        dirty = false;
        return result;
    }

    public static void enqueueWireless(PacketWirelessState packet) {
        incoming.add(() -> applyWireless(packet));
    }

    private static void applyWireless(PacketWirelessState packet) {
        if (packet.sessionId != wirelessSession) return;
        if (packet.updateCursor) {
            pendingCursor = packet.cursor;
            hasPendingCursor = true;
            dirty = true;
            return;
        }
        BackpackTab tab = wirelessTabs.get(packet.tabId);
        if (packet.removed) {
            if (tab != null && tab.wirelessGeneration == packet.generation) {
                wirelessTabs.remove(packet.tabId);
                wirelessItems.remove(packet.tabId);
                dirty = true;
            }
            return;
        }
        if (packet.terminal != null) {
            if (tab == null || tab.wirelessGeneration != packet.generation) {
                tab = new BackpackTab(
                    packet.tabId,
                    packet.tabId - WirelessOverlay.TAB_BASE,
                    BackpackKind.AE2_WIRELESS,
                    packet.terminal,
                    9,
                    0,
                    0,
                    0,
                    false,
                    false);
                tab.wirelessGeneration = packet.generation;
                tab.wirelessLoading = true;
                wirelessTabs.put(packet.tabId, tab);
                wirelessItems.put(packet.tabId, new LinkedHashMap<WirelessItemEntry, WirelessItemEntry>());
            }
            dirty = true;
            hasState = true;
            return;
        }
        if (tab == null || tab.wirelessGeneration != packet.generation) return;
        Map<WirelessItemEntry, WirelessItemEntry> items = wirelessItems.get(packet.tabId);
        if (packet.reset) {
            items.clear();
            tab.wirelessLoading = true;
        }
        for (WirelessItemEntry entry : packet.entries) {
            if (entry.amount == 0) items.remove(entry);
            else items.put(entry, entry);
        }
        if (packet.complete) tab.wirelessLoading = false;
        if (!tab.wirelessLoading) tab.setWirelessEntries(new ArrayList<WirelessItemEntry>(items.values()));
        dirty = true;
    }

    private OverlayClientState() {}

    public static void setState(List<OverlayTabSnapshot> snapshots, ItemStack cursorStack, boolean updateCursor) {
        setState(snapshots, cursorStack, updateCursor, -1);
    }

    public static void setState(List<OverlayTabSnapshot> snapshots, ItemStack cursorStack, boolean updateCursor,
        int revision) {
        incoming.add(() -> applyState(snapshots, cursorStack, updateCursor, revision));
    }

    private static void applyState(List<OverlayTabSnapshot> snapshots, ItemStack cursorStack, boolean updateCursor,
        int revision) {
        List<BackpackTab> converted = new ArrayList<BackpackTab>(snapshots.size());
        for (OverlayTabSnapshot snapshot : snapshots) {
            BackpackTab tab = new BackpackTab(
                snapshot.tabId,
                snapshot.playerSlot,
                snapshot.kind,
                snapshot.backpackStack,
                snapshot.columns,
                snapshot.storageSlots,
                snapshot.modeId,
                snapshot.nextModeId,
                snapshot.modeCycleAvailable,
                snapshot.resupplyEnabled);
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
        synchronizeBackpackModes(converted);
        if (updateCursor && (revision == -1 || revision == cursorRevision)) {
            // Hold cursor until tabs are applied on the client tick (same apply step).
            pendingCursor = cursorStack == null ? null : cursorStack.copy();
            hasPendingCursor = true;
        }
        hasState = true;
        dirty = true;
    }

    private static void synchronizeBackpackModes(List<BackpackTab> tabs) {
        for (BackpackTab tab : tabs) {
            if (!tab.modeCycleAvailable || tab.stack == null) continue;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer == null || mc.thePlayer.inventory == null) return;
            if (tab.playerSlot < 0 || tab.playerSlot >= mc.thePlayer.inventory.mainInventory.length) continue;
            ItemStack local = mc.thePlayer.inventory.mainInventory[tab.playerSlot];
            if (local != null && local.getItem() == tab.stack.getItem()) {
                local.setItemDamage(tab.stack.getItemDamage());
            }
        }
    }

    /**
     * Apply pending cursor to the local player. Call in the same tick as tab list refresh.
     */
    public static void applyPendingCursor() {
        if (!hasPendingCursor) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            applyPendingCursor(mc.thePlayer.inventory);
        }
    }

    static void applyPendingCursor(InventoryPlayer inventory) {
        if (!hasPendingCursor) return;
        hasPendingCursor = false;
        inventory.setItemStack(pendingCursor == null ? null : pendingCursor.copy());
        pendingCursor = null;
    }

    public static List<BackpackTab> getTabs() {
        List<BackpackTab> result = new ArrayList<BackpackTab>(syncedTabs);
        result.addAll(wirelessTabs.values());
        return result;
    }

    public static boolean hasState() {
        return hasState;
    }

    public static void clear() {
        invalidateCursor();
        syncedTabs = new ArrayList<BackpackTab>();
        hasState = false;
        incoming.clear();
        wirelessTabs.clear();
        wirelessItems.clear();
        wirelessSession = -1;
        dirty = false;
    }
}
