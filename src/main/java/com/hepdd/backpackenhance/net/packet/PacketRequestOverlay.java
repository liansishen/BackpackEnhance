package com.hepdd.backpackenhance.net.packet;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.backpackenhance.integration.BackpackScanner;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.net.NetworkHandler;
import com.hepdd.backpackenhance.server.overlay.OverlaySessionTracker;
import com.hepdd.backpackenhance.server.overlay.OverlaySnapshotFactory;
import com.hepdd.backpackenhance.server.overlay.OverlayTabSnapshot;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketRequestOverlay implements IMessage {

    private String screenClassName = "";
    private String containerClassName = "";
    private boolean minimized;
    private int sessionId;

    public PacketRequestOverlay() {}

    public PacketRequestOverlay(String screenClassName, String containerClassName, boolean minimized, int sessionId) {
        this.screenClassName = screenClassName;
        this.containerClassName = containerClassName;
        this.minimized = minimized;
        this.sessionId = sessionId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        screenClassName = ByteBufUtils.readUTF8String(buf);
        containerClassName = ByteBufUtils.readUTF8String(buf);
        minimized = buf.readBoolean();
        sessionId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, screenClassName);
        ByteBufUtils.writeUTF8String(buf, containerClassName);
        buf.writeBoolean(minimized);
        buf.writeInt(sessionId);
    }

    public static class Handler implements IMessageHandler<PacketRequestOverlay, IMessage> {

        private final BackpackScanner scanner = new BackpackScanner();
        private final OverlaySnapshotFactory snapshotFactory = new OverlaySnapshotFactory();

        @Override
        public IMessage onMessage(PacketRequestOverlay message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            List<BackpackTab> tabs = scanner.scan(player);
            List<OverlayTabSnapshot> snapshots = snapshotFactory.build(player, tabs);
            int activeTab = snapshots.isEmpty() ? -1 : snapshots.get(0).tabId;
            OverlaySessionTracker.activate(player, activeTab, message.sessionId, message.minimized);
            // Creative inventory clicks keep the carried stack client-side.
            NetworkHandler.INSTANCE.sendTo(new PacketOverlayState(snapshots), player);
            return null;
        }
    }
}
