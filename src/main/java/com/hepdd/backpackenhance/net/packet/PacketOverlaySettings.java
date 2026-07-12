package com.hepdd.backpackenhance.net.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.backpackenhance.server.overlay.OverlaySessionTracker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Sync overlay minimized state to the server session. */
public class PacketOverlaySettings implements IMessage {

    private boolean minimized;

    public PacketOverlaySettings() {}

    public PacketOverlaySettings(boolean minimized) {
        this.minimized = minimized;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        minimized = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(minimized);
    }

    public static class Handler implements IMessageHandler<PacketOverlaySettings, IMessage> {

        @Override
        public IMessage onMessage(PacketOverlaySettings message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            OverlaySessionTracker.setMinimized(player, message.minimized);
            return null;
        }
    }
}
