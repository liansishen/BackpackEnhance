package com.hepdd.backpackenhance.server.overlay;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;

public final class OverlaySessionTracker {

    private static final Map<UUID, Integer> ACTIVE_TABS = new ConcurrentHashMap<UUID, Integer>();
    private static final Map<UUID, Boolean> OVERLAY_MINIMIZED = new ConcurrentHashMap<UUID, Boolean>();
    private static final Map<UUID, Integer> SESSION_IDS = new ConcurrentHashMap<UUID, Integer>();

    private OverlaySessionTracker() {}

    public static void activate(EntityPlayerMP player, int activeTabId, int sessionId, boolean minimized) {
        UUID id = player.getUniqueID();
        SESSION_IDS.put(id, Integer.valueOf(sessionId));
        ACTIVE_TABS.put(id, Integer.valueOf(activeTabId));
        OVERLAY_MINIMIZED.put(id, Boolean.valueOf(minimized));
    }

    public static void setActiveTab(EntityPlayerMP player, int activeTabId) {
        ACTIVE_TABS.put(player.getUniqueID(), Integer.valueOf(activeTabId));
    }

    public static void setMinimized(EntityPlayerMP player, boolean minimized) {
        OVERLAY_MINIMIZED.put(player.getUniqueID(), Boolean.valueOf(minimized));
    }

    public static Integer getActiveTab(EntityPlayerMP player) {
        return ACTIVE_TABS.get(player.getUniqueID());
    }

    /** True while overlay session exists and is expanded (not minimized). */
    public static boolean shouldPrioritizeShift(EntityPlayerMP player) {
        if (!hasSession(player)) {
            return false;
        }
        Boolean minimized = OVERLAY_MINIMIZED.get(player.getUniqueID());
        return minimized == null || !minimized.booleanValue();
    }

    public static boolean hasSession(EntityPlayerMP player) {
        return ACTIVE_TABS.containsKey(player.getUniqueID());
    }

    public static boolean isCurrentExpandedSession(EntityPlayerMP player, int sessionId) {
        UUID id = player.getUniqueID();
        Integer current = SESSION_IDS.get(id);
        Boolean minimized = OVERLAY_MINIMIZED.get(id);
        return current != null && current.intValue() == sessionId && (minimized == null || !minimized.booleanValue());
    }

    /**
     * Clear only if the close packet matches the current session (avoids late Close wiping a
     * newer Request).
     */
    public static void clearIfSession(EntityPlayerMP player, int sessionId) {
        UUID id = player.getUniqueID();
        Integer current = SESSION_IDS.get(id);
        if (current != null && current.intValue() == sessionId) {
            clear(player);
        }
    }

    public static void clear(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        ACTIVE_TABS.remove(id);
        OVERLAY_MINIMIZED.remove(id);
        SESSION_IDS.remove(id);
    }
}
