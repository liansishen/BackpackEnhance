package com.hepdd.backpackenhance.net.packet;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;

import com.hepdd.backpackenhance.integration.BackpackScanner;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.net.NetworkHandler;
import com.hepdd.backpackenhance.server.overlay.OverlayClickExecutor;
import com.hepdd.backpackenhance.server.overlay.OverlaySnapshotFactory;
import com.hepdd.backpackenhance.server.overlay.OverlayTabSnapshot;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketOverlayClick implements IMessage {

    public static final int MODE_NORMAL = 0;
    public static final int MODE_SHIFT = 1;
    public static final int MODE_HOTBAR = 2;
    public static final int MODE_DOUBLE = 3;

    private int tabId;
    private int slotIndex;
    private int button;
    private int mode;
    private int cursorRevision = -1;

    public PacketOverlayClick() {}

    public PacketOverlayClick(int tabId, int slotIndex, int button, int mode) {
        this.tabId = tabId;
        this.slotIndex = slotIndex;
        this.button = button;
        this.mode = mode;
    }

    public PacketOverlayClick(int tabId, int slotIndex, int button, int mode, int cursorRevision) {
        this(tabId, slotIndex, button, mode);
        this.cursorRevision = cursorRevision;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tabId = buf.readInt();
        slotIndex = buf.readInt();
        button = buf.readInt();
        mode = buf.readInt();
        cursorRevision = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tabId);
        buf.writeInt(slotIndex);
        buf.writeInt(button);
        buf.writeInt(mode);
        buf.writeInt(cursorRevision);
    }

    public static class Handler implements IMessageHandler<PacketOverlayClick, IMessage> {

        private final BackpackScanner scanner = new BackpackScanner();
        private final OverlayClickExecutor clickExecutor = new OverlayClickExecutor();
        private final OverlaySnapshotFactory snapshotFactory = new OverlaySnapshotFactory();

        @Override
        public IMessage onMessage(PacketOverlayClick message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            List<BackpackTab> tabs = scanner.scan(player);
            if (message.mode == MODE_SHIFT && message.button == 0) {
                clickExecutor.shiftClickOut(player, tabs, message.tabId, message.slotIndex);
            } else if (message.mode == MODE_HOTBAR) {
                clickExecutor.hotbarSwap(player, tabs, message.tabId, message.slotIndex, message.button);
            } else if (message.mode == MODE_DOUBLE && message.button == 0) {
                clickExecutor.doubleClickCollect(player, tabs, message.tabId, message.slotIndex);
            } else if (message.button == 0) {
                clickExecutor.leftClick(player, tabs, message.tabId, message.slotIndex);
            } else if (message.button == 1) {
                clickExecutor.rightClick(player, tabs, message.tabId, message.slotIndex);
            }
            List<OverlayTabSnapshot> snapshots = snapshotFactory.build(player, tabs);
            NetworkHandler.INSTANCE.sendTo(
                new PacketOverlayState(snapshots, player.inventory.getItemStack(), message.cursorRevision),
                player);
            return null;
        }
    }
}
