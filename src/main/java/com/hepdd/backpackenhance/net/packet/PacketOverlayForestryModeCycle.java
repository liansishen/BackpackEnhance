package com.hepdd.backpackenhance.net.packet;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.integration.BackpackScanner;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.integration.ForestryModeBridge;
import com.hepdd.backpackenhance.net.NetworkHandler;
import com.hepdd.backpackenhance.server.overlay.OverlaySessionTracker;
import com.hepdd.backpackenhance.server.overlay.OverlaySnapshotFactory;
import com.hepdd.backpackenhance.server.overlay.OverlayTabSnapshot;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketOverlayForestryModeCycle implements IMessage {

    private int sessionId;
    private int tabId;
    private int playerSlot;
    private int expectedMode;
    private boolean expectedHasUid;
    private int expectedUid;

    public PacketOverlayForestryModeCycle() {}

    public PacketOverlayForestryModeCycle(int sessionId, int tabId, int playerSlot, int expectedMode,
        boolean expectedHasUid, int expectedUid) {
        this.sessionId = sessionId;
        this.tabId = tabId;
        this.playerSlot = playerSlot;
        this.expectedMode = expectedMode;
        this.expectedHasUid = expectedHasUid;
        this.expectedUid = expectedUid;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sessionId = buf.readInt();
        tabId = buf.readInt();
        playerSlot = buf.readInt();
        expectedMode = buf.readByte();
        expectedHasUid = buf.readBoolean();
        expectedUid = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(sessionId);
        buf.writeInt(tabId);
        buf.writeInt(playerSlot);
        buf.writeByte(expectedMode);
        buf.writeBoolean(expectedHasUid);
        buf.writeInt(expectedUid);
    }

    public static class Handler implements IMessageHandler<PacketOverlayForestryModeCycle, IMessage> {

        private final BackpackScanner scanner = new BackpackScanner();
        private final OverlaySnapshotFactory snapshotFactory = new OverlaySnapshotFactory();

        @Override
        public IMessage onMessage(PacketOverlayForestryModeCycle message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (!OverlaySessionTracker.isCurrentExpandedSession(player, message.sessionId)) {
                return null;
            }

            List<BackpackTab> tabs = scanner.scan(player);
            BackpackTab target = findTarget(tabs, message.tabId, message.playerSlot);
            if (target != null && target.kind == BackpackKind.FORESTRY
                && message.playerSlot >= 0
                && message.playerSlot < player.inventory.mainInventory.length) {
                ItemStack liveStack = player.inventory.mainInventory[message.playerSlot];
                BackpackKind liveKind = scanner.detectKind(liveStack);
                if (liveKind == BackpackKind.FORESTRY && ForestryModeBridge
                    .cycleMode(liveStack, message.expectedMode, message.expectedHasUid, message.expectedUid)) {
                    player.inventory.markDirty();
                    if (player.openContainer != null) {
                        player.openContainer.detectAndSendChanges();
                    }
                }
            }

            tabs = scanner.scan(player);
            List<OverlayTabSnapshot> snapshots = snapshotFactory.build(player, tabs);
            NetworkHandler.INSTANCE.sendTo(new PacketOverlayState(snapshots, player.inventory.getItemStack()), player);
            return null;
        }

        private BackpackTab findTarget(List<BackpackTab> tabs, int tabId, int playerSlot) {
            for (BackpackTab tab : tabs) {
                if (tab.tabId == tabId && tab.playerSlot == playerSlot) {
                    return tab;
                }
            }
            return null;
        }
    }
}
