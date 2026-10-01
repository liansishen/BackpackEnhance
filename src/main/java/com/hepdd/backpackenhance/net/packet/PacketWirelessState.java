package com.hepdd.backpackenhance.net.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.BackpackEnhance;
import com.hepdd.backpackenhance.integration.WirelessItemEntry;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketWirelessState implements IMessage {

    public int sessionId;
    public int tabId;
    public int generation;
    public boolean removed;
    public boolean reset;
    public boolean complete;
    public boolean updateCursor;
    public ItemStack terminal;
    public ItemStack cursor;
    public List<WirelessItemEntry> entries = new ArrayList<WirelessItemEntry>();

    public PacketWirelessState() {}

    public PacketWirelessState(int sessionId, int tabId, int generation, ItemStack terminal) {
        this.sessionId = sessionId;
        this.tabId = tabId;
        this.generation = generation;
        this.terminal = terminal == null ? null : terminal.copy();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sessionId = buf.readInt();
        tabId = buf.readInt();
        generation = buf.readInt();
        removed = buf.readBoolean();
        reset = buf.readBoolean();
        complete = buf.readBoolean();
        updateCursor = buf.readBoolean();
        terminal = ByteBufUtils.readItemStack(buf);
        cursor = updateCursor ? ByteBufUtils.readItemStack(buf) : null;
        int size = buf.readUnsignedShort();
        if (size > 256) throw new IllegalArgumentException("Oversized wireless inventory update");
        for (int i = 0; i < size; i++) {
            ItemStack stack = ByteBufUtils.readItemStack(buf);
            long amount = buf.readLong();
            if (stack == null || amount < 0) throw new IllegalArgumentException("Invalid wireless item entry");
            entries.add(new WirelessItemEntry(stack, amount));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(sessionId);
        buf.writeInt(tabId);
        buf.writeInt(generation);
        buf.writeBoolean(removed);
        buf.writeBoolean(reset);
        buf.writeBoolean(complete);
        buf.writeBoolean(updateCursor);
        ByteBufUtils.writeItemStack(buf, terminal);
        if (updateCursor) ByteBufUtils.writeItemStack(buf, cursor);
        buf.writeShort(entries.size());
        for (WirelessItemEntry entry : entries) writeEntry(buf, entry);
    }

    public static void writeEntry(ByteBuf buf, WirelessItemEntry entry) {
        ByteBufUtils.writeItemStack(buf, entry.template);
        buf.writeLong(entry.amount);
    }

    public static class Handler implements IMessageHandler<PacketWirelessState, IMessage> {

        @Override
        public IMessage onMessage(PacketWirelessState message, MessageContext ctx) {
            BackpackEnhance.proxy.receiveWirelessState(message);
            return null;
        }
    }
}
