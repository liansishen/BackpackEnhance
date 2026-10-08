package com.hepdd.backpackenhance.net;

import com.hepdd.backpackenhance.BackpackEnhance;
import com.hepdd.backpackenhance.net.packet.PacketCloseOverlay;
import com.hepdd.backpackenhance.net.packet.PacketCreativeCursor;
import com.hepdd.backpackenhance.net.packet.PacketOverlayActiveTab;
import com.hepdd.backpackenhance.net.packet.PacketOverlayClick;
import com.hepdd.backpackenhance.net.packet.PacketOverlayDeposit;
import com.hepdd.backpackenhance.net.packet.PacketOverlayDrag;
import com.hepdd.backpackenhance.net.packet.PacketOverlayExtract;
import com.hepdd.backpackenhance.net.packet.PacketOverlayForestryModeCycle;
import com.hepdd.backpackenhance.net.packet.PacketOverlaySettings;
import com.hepdd.backpackenhance.net.packet.PacketOverlayState;
import com.hepdd.backpackenhance.net.packet.PacketRequestOverlay;
import com.hepdd.backpackenhance.net.packet.PacketWirelessAction;
import com.hepdd.backpackenhance.net.packet.PacketWirelessState;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public final class NetworkHandler {

    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE
        .newSimpleChannel(BackpackEnhance.MODID);

    private NetworkHandler() {}

    public static void init() {
        INSTANCE.registerMessage(PacketRequestOverlay.Handler.class, PacketRequestOverlay.class, 0, Side.SERVER);
        INSTANCE.registerMessage(PacketOverlayState.Handler.class, PacketOverlayState.class, 1, Side.CLIENT);
        INSTANCE.registerMessage(PacketOverlayClick.Handler.class, PacketOverlayClick.class, 2, Side.SERVER);
        INSTANCE.registerMessage(PacketOverlayDrag.Handler.class, PacketOverlayDrag.class, 3, Side.SERVER);
        INSTANCE.registerMessage(PacketOverlayActiveTab.Handler.class, PacketOverlayActiveTab.class, 4, Side.SERVER);
        INSTANCE.registerMessage(PacketCloseOverlay.Handler.class, PacketCloseOverlay.class, 5, Side.SERVER);
        INSTANCE.registerMessage(PacketOverlaySettings.Handler.class, PacketOverlaySettings.class, 6, Side.SERVER);
        INSTANCE.registerMessage(PacketOverlayExtract.Handler.class, PacketOverlayExtract.class, 7, Side.SERVER);
        INSTANCE.registerMessage(PacketOverlayDeposit.Handler.class, PacketOverlayDeposit.class, 8, Side.SERVER);
        INSTANCE.registerMessage(
            PacketOverlayForestryModeCycle.Handler.class,
            PacketOverlayForestryModeCycle.class,
            9,
            Side.SERVER);
        INSTANCE.registerMessage(PacketWirelessAction.Handler.class, PacketWirelessAction.class, 10, Side.SERVER);
        INSTANCE.registerMessage(PacketWirelessState.Handler.class, PacketWirelessState.class, 11, Side.CLIENT);
        INSTANCE.registerMessage(PacketCreativeCursor.Handler.class, PacketCreativeCursor.class, 12, Side.SERVER);
    }
}
