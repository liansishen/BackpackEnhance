package com.hepdd.backpackenhance.net.packet;

import java.util.ArrayList;
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

public class PacketOverlayDrag implements IMessage {

    private int tabId;
    private int button;
    private List<Integer> slots = new ArrayList<Integer>();

    public PacketOverlayDrag() {}

    public PacketOverlayDrag(int tabId, List<Integer> slots) {
        this(tabId, slots, 0);
    }

    public PacketOverlayDrag(int tabId, List<Integer> slots, int button) {
        this.tabId = tabId;
        this.button = button;
        this.slots = new ArrayList<Integer>(slots);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tabId = buf.readInt();
        button = buf.readInt();
        int size = buf.readInt();
        slots = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) {
            slots.add(buf.readInt());
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tabId);
        buf.writeInt(button);
        buf.writeInt(slots.size());
        for (Integer slot : slots) {
            buf.writeInt(slot.intValue());
        }
    }

    public static class Handler implements IMessageHandler<PacketOverlayDrag, IMessage> {

        private final BackpackScanner scanner = new BackpackScanner();
        private final OverlayClickExecutor clickExecutor = new OverlayClickExecutor();
        private final OverlaySnapshotFactory snapshotFactory = new OverlaySnapshotFactory();

        @Override
        public IMessage onMessage(PacketOverlayDrag message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            List<BackpackTab> tabs = scanner.scan(player);
            clickExecutor.dragDistribute(player, tabs, message.tabId, message.slots, message.button);
            List<OverlayTabSnapshot> snapshots = snapshotFactory.build(player, tabs);
            NetworkHandler.INSTANCE.sendTo(new PacketOverlayState(snapshots, player.inventory.getItemStack()), player);
            return null;
        }
    }
}
