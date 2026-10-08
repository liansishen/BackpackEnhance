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

/**
 * Extract items from an overlay backpack slot.
 * <ul>
 * <li>{@code containerSlot >= 0}: into an open-container craft slot (manual + fill)</li>
 * <li>{@code containerSlot < 0}: into the player inventory (auto-craft borrow path)</li>
 * </ul>
 */
public class PacketOverlayExtract implements IMessage {

    private int tabId;
    private int slotIndex;
    private int amount;
    private int windowId;
    private int containerSlot;

    public PacketOverlayExtract() {}

    public PacketOverlayExtract(int tabId, int slotIndex, int amount, int windowId, int containerSlot) {
        this.tabId = tabId;
        this.slotIndex = slotIndex;
        this.amount = amount;
        this.windowId = windowId;
        this.containerSlot = containerSlot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tabId = buf.readInt();
        slotIndex = buf.readInt();
        amount = buf.readInt();
        windowId = buf.readInt();
        containerSlot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tabId);
        buf.writeInt(slotIndex);
        buf.writeInt(amount);
        buf.writeInt(windowId);
        buf.writeInt(containerSlot);
    }

    public static class Handler implements IMessageHandler<PacketOverlayExtract, IMessage> {

        private final BackpackScanner scanner = new BackpackScanner();
        private final OverlayClickExecutor clickExecutor = new OverlayClickExecutor();
        private final OverlaySnapshotFactory snapshotFactory = new OverlaySnapshotFactory();

        @Override
        public IMessage onMessage(PacketOverlayExtract message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            List<BackpackTab> tabs = scanner.scan(player);
            if (message.containerSlot < 0) {
                clickExecutor.extractToPlayer(player, tabs, message.tabId, message.slotIndex, message.amount);
            } else {
                clickExecutor.extractToContainerSlot(
                    player,
                    tabs,
                    message.tabId,
                    message.slotIndex,
                    message.amount,
                    message.windowId,
                    message.containerSlot);
            }
            List<OverlayTabSnapshot> snapshots = snapshotFactory.build(player, tabs);
            NetworkHandler.INSTANCE.sendTo(new PacketOverlayState(snapshots), player);
            return null;
        }
    }
}
