package com.hepdd.backpackenhance.net.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketCreativeCursor implements IMessage {

    private ItemStack cursor;

    public PacketCreativeCursor() {}

    public PacketCreativeCursor(ItemStack cursor) {
        this.cursor = cursor == null ? null : cursor.copy();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        cursor = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeItemStack(buf, cursor);
    }

    void apply(InventoryPlayer inventory, boolean creative, boolean playerInventory) {
        if (creative && playerInventory) {
            inventory.setItemStack(cursor == null ? null : cursor.copy());
        }
    }

    public static class Handler implements IMessageHandler<PacketCreativeCursor, IMessage> {

        @Override
        public IMessage onMessage(PacketCreativeCursor message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            message.apply(
                player.inventory,
                player.theItemInWorldManager.isCreative(),
                player.openContainer == player.inventoryContainer);
            return null;
        }
    }
}
