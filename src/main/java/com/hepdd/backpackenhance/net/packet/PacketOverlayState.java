package com.hepdd.backpackenhance.net.packet;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.server.overlay.OverlaySlotSnapshot;
import com.hepdd.backpackenhance.server.overlay.OverlayTabSnapshot;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

public class PacketOverlayState implements IMessage {

    private List<OverlayTabSnapshot> tabs = new ArrayList<OverlayTabSnapshot>();
    private ItemStack cursorStack;

    public PacketOverlayState() {}

    public PacketOverlayState(List<OverlayTabSnapshot> tabs, ItemStack cursorStack) {
        this.tabs = tabs;
        this.cursorStack = cursorStack == null ? null : cursorStack.copy();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int tabCount = buf.readInt();
        tabs = new ArrayList<OverlayTabSnapshot>(tabCount);
        for (int i = 0; i < tabCount; i++) {
            int tabId = buf.readInt();
            int playerSlot = buf.readInt();
            BackpackKind kind = BackpackKind.values()[buf.readInt()];
            String displayName = ByteBufUtils.readUTF8String(buf);
            int columns = buf.readInt();
            int storageSlots = buf.readInt();
            ItemStack backpackStack = ByteBufUtils.readItemStack(buf);
            int modeId = buf.readInt();
            int nextModeId = buf.readInt();
            boolean modeCycleAvailable = buf.readBoolean();
            boolean resupplyEnabled = buf.readBoolean();
            int stackCount = buf.readInt();
            List<OverlaySlotSnapshot> slots = new ArrayList<OverlaySlotSnapshot>(stackCount);
            for (int slot = 0; slot < stackCount; slot++) {
                slots.add(new OverlaySlotSnapshot(buf.readInt(), ByteBufUtils.readItemStack(buf)));
            }
            tabs.add(
                new OverlayTabSnapshot(
                    tabId,
                    playerSlot,
                    kind,
                    displayName,
                    columns,
                    storageSlots,
                    backpackStack,
                    slots,
                    modeId,
                    nextModeId,
                    modeCycleAvailable,
                    resupplyEnabled));
        }
        cursorStack = ByteBufUtils.readItemStack(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(tabs.size());
        for (OverlayTabSnapshot tab : tabs) {
            buf.writeInt(tab.tabId);
            buf.writeInt(tab.playerSlot);
            buf.writeInt(tab.kind.ordinal());
            ByteBufUtils.writeUTF8String(buf, tab.displayName);
            buf.writeInt(tab.columns);
            buf.writeInt(tab.storageSlots);
            ByteBufUtils.writeItemStack(buf, tab.backpackStack);
            buf.writeInt(tab.modeId);
            buf.writeInt(tab.nextModeId);
            buf.writeBoolean(tab.modeCycleAvailable);
            buf.writeBoolean(tab.resupplyEnabled);
            buf.writeInt(tab.slots.size());
            for (OverlaySlotSnapshot slot : tab.slots) {
                buf.writeInt(slot.slot);
                ByteBufUtils.writeItemStack(buf, slot.stack);
            }
        }
        ByteBufUtils.writeItemStack(buf, cursorStack);
    }

    public static class Handler implements IMessageHandler<PacketOverlayState, IMessage> {

        @Override
        public IMessage onMessage(PacketOverlayState message, MessageContext ctx) {
            try {
                Class<?> stateClass = Class.forName("com.hepdd.backpackenhance.client.overlay.OverlayClientState");
                stateClass.getMethod("setState", List.class, ItemStack.class)
                    .invoke(null, message.tabs, message.cursorStack);
            } catch (ReflectiveOperationException ignored) {
                // Client-only state is unavailable on dedicated servers; this handler is only registered client-side.
            }
            return null;
        }
    }
}
