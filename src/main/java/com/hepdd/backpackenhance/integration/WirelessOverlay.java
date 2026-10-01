package com.hepdd.backpackenhance.integration;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;

import com.hepdd.backpackenhance.net.packet.PacketWirelessAction;

import cpw.mods.fml.common.Loader;

/** Keeps optional AE2 classes outside the common packet and container paths. */
public final class WirelessOverlay {

    public static final int TAB_BASE = 1000;
    public static Backend backend;

    private WirelessOverlay() {}

    public static void init() {
        if (!Loader.isModLoaded("appliedenergistics2")) return;
        try {
            Class.forName("com.hepdd.backpackenhance.integration.ae2.WirelessOverlayService")
                .getMethod("init")
                .invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot initialize AE2 wireless overlay", e);
        }
    }

    public static boolean isWirelessTab(int tabId) {
        return tabId >= TAB_BASE && tabId < TAB_BASE + 36;
    }

    public interface Backend {

        void enqueue(EntityPlayerMP player, PacketWirelessAction action);

        boolean shiftInto(EntityPlayerMP player, Slot source);
    }
}
