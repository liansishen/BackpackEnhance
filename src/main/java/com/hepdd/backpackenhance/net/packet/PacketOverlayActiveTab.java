package com.hepdd.backpackenhance.net.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.backpackenhance.server.overlay.OverlaySessionTracker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketOverlayActiveTab implements IMessage {

    private int tabId;

    public PacketOverlayActiveTab() {}

    public PacketOverlayActiveTab(int tabId) {
        this.tabId = tabId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tabId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tabId);
    }

    public static class Handler implements IMessageHandler<PacketOverlayActiveTab, IMessage> {

        @Override
        public IMessage onMessage(PacketOverlayActiveTab message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            OverlaySessionTracker.setActiveTab(player, message.tabId);
            return null;
        }
    }
}
