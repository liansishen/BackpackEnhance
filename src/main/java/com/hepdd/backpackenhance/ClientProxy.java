package com.hepdd.backpackenhance;

import net.minecraftforge.common.MinecraftForge;

import com.hepdd.backpackenhance.client.keybind.KeyBindings;
import com.hepdd.backpackenhance.client.overlay.OverlayClientState;
import com.hepdd.backpackenhance.client.overlay.OverlayController;
import com.hepdd.backpackenhance.integration.nei.NeiOverlayIntegration;
import com.hepdd.backpackenhance.net.packet.PacketWirelessState;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        KeyBindings.init();
        NeiOverlayIntegration.init();
        OverlayController overlayController = new OverlayController();
        MinecraftForge.EVENT_BUS.register(overlayController);
        FMLCommonHandler.instance()
            .bus()
            .register(overlayController);
    }

    @Override
    public void receiveWirelessState(PacketWirelessState packet) {
        OverlayClientState.enqueueWireless(packet);
    }

}
