package com.hepdd.backpackenhance.net.packet;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.integration.WirelessOverlay;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketWirelessAction implements IMessage {

    public static final int OPEN = 0;
    public static final int CLOSE = 1;
    public static final int SELECT = 2;
    public static final int CLICK = 3;
    public static final int SHIFT = 4;
    public static final int HOTBAR = 5;
    public static final int COLLECT = 6;
    public static final int DRAG = 7;

    public int sessionId;
    public int windowId;
    public int tabId;
    public int generation;
    public int action;
    public int value;
    public boolean minimized;
    public ItemStack template;

    public PacketWirelessAction() {}

    public PacketWirelessAction(int sessionId, int windowId, int tabId, int generation, int action, int value,
        boolean minimized, ItemStack template) {
        this.sessionId = sessionId;
        this.windowId = windowId;
        this.tabId = tabId;
        this.generation = generation;
        this.action = action;
        this.value = value;
        this.minimized = minimized;
        this.template = template == null ? null : template.copy();
        if (this.template != null) this.template.stackSize = 1;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        sessionId = buf.readInt();
        windowId = buf.readInt();
        tabId = buf.readInt();
        generation = buf.readInt();
        action = buf.readInt();
        value = buf.readInt();
        minimized = buf.readBoolean();
        template = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(sessionId);
        buf.writeInt(windowId);
        buf.writeInt(tabId);
        buf.writeInt(generation);
        buf.writeInt(action);
        buf.writeInt(value);
        buf.writeBoolean(minimized);
        ByteBufUtils.writeItemStack(buf, template);
    }

    public static class Handler implements IMessageHandler<PacketWirelessAction, IMessage> {

        @Override
        public IMessage onMessage(PacketWirelessAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (WirelessOverlay.backend != null) WirelessOverlay.backend.enqueue(player, message);
            return null;
        }
    }
}
