package com.hepdd.backpackenhance.net.packet;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.backpackenhance.server.overlay.OverlaySessionTracker;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketCloseOverlay implements IMessage {

    private int sessionId;

    public PacketCloseOverlay() {}

    public PacketCloseOverlay(int sessionId) {
        this.sessionId = sessionId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sessionId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(sessionId);
    }

    public static class Handler implements IMessageHandler<PacketCloseOverlay, IMessage> {

        @Override
        public IMessage onMessage(PacketCloseOverlay message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            OverlaySessionTracker.clearIfSession(player, message.sessionId);
            return null;
        }
    }
}
