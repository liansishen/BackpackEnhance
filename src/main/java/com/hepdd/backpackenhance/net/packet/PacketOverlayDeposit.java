package com.hepdd.backpackenhance.net.packet;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.integration.BackpackScanner;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.net.NetworkHandler;
import com.hepdd.backpackenhance.server.overlay.OverlayClickExecutor;
import com.hepdd.backpackenhance.server.overlay.OverlaySnapshotFactory;
import com.hepdd.backpackenhance.server.overlay.OverlayTabSnapshot;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Move craft-matching items from the player inventory back into an overlay backpack
 * (NEI auto-craft ledger settle). Matching is lenient for damageable / GT tools.
 */
public class PacketOverlayDeposit implements IMessage {

    private int tabId;
    private ItemStack template;
    private int amount;

    public PacketOverlayDeposit() {}

    public PacketOverlayDeposit(int tabId, ItemStack template, int amount) {
        this.tabId = tabId;
        this.template = template;
        this.amount = amount;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tabId = buf.readInt();
        amount = buf.readInt();
        template = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tabId);
        buf.writeInt(amount);
        ByteBufUtils.writeItemStack(buf, template);
    }

    public static class Handler implements IMessageHandler<PacketOverlayDeposit, IMessage> {

        private final BackpackScanner scanner = new BackpackScanner();
        private final OverlayClickExecutor clickExecutor = new OverlayClickExecutor();
        private final OverlaySnapshotFactory snapshotFactory = new OverlaySnapshotFactory();

        @Override
        public IMessage onMessage(PacketOverlayDeposit message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            List<BackpackTab> tabs = scanner.scan(player);
            clickExecutor.depositFromPlayer(player, tabs, message.tabId, message.template, message.amount);
            List<OverlayTabSnapshot> snapshots = snapshotFactory.build(player, tabs);
            NetworkHandler.INSTANCE.sendTo(new PacketOverlayState(snapshots), player);
            return null;
        }
    }
}
