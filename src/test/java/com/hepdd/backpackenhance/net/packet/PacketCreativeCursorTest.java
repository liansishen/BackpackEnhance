package com.hepdd.backpackenhance.net.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.BeforeClass;
import org.junit.Test;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCreativeCursorTest {

    private static Item item;

    @BeforeClass
    public static void bootstrap() throws ReflectiveOperationException {
        item = new Item();
        Method register = FMLControlledNamespacedRegistry.class
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 29998, "backpackenhance:creative_cursor_test", item);
    }

    @Test
    public void creativePickupOfNeiGivenItemSynchronizesServerCursor() {
        InventoryPlayer client = new InventoryPlayer(null);
        InventoryPlayer server = new InventoryPlayer(null);
        ItemStack given = stack(12);
        client.setInventorySlotContents(9, given.copy());
        server.setInventorySlotContents(9, given.copy());

        client.setItemStack(client.decrStackSize(9, 12));
        server.setInventorySlotContents(9, null);
        assertNull(server.getItemStack());

        roundTrip(new PacketCreativeCursor(client.getItemStack())).apply(server, true, true);
        assertTrue(ItemStack.areItemStacksEqual(client.getItemStack(), server.getItemStack()));
        assertNotSame(client.getItemStack(), server.getItemStack());
        assertNull(server.getStackInSlot(9));
        assertEquals(
            42,
            server.getItemStack()
                .getTagCompound()
                .getInteger("marker"));
    }

    @Test
    public void placingOverlayItemInPlayerInventoryClearsServerCursor() {
        InventoryPlayer server = new InventoryPlayer(null);
        ItemStack taken = stack(8);
        server.setItemStack(taken.copy());
        server.setInventorySlotContents(10, taken);

        roundTrip(new PacketCreativeCursor(null)).apply(server, true, true);
        assertNull(server.getItemStack());
        assertSame(taken, server.getStackInSlot(10));
        assertEquals(8, server.getStackInSlot(10).stackSize);
    }

    @Test
    public void partialPlacementSynchronizesRemainingCount() {
        InventoryPlayer server = new InventoryPlayer(null);
        server.setItemStack(stack(12));
        roundTrip(new PacketCreativeCursor(stack(11))).apply(server, true, true);
        assertEquals(11, server.getItemStack().stackSize);
    }

    @Test
    public void survivalPlayerCannotReplaceAuthoritativeCursor() {
        InventoryPlayer server = new InventoryPlayer(null);
        ItemStack original = stack(2);
        server.setItemStack(original);
        roundTrip(new PacketCreativeCursor(stack(64))).apply(server, false, true);
        assertSame(original, server.getItemStack());
    }

    @Test
    public void creativePlayerInChestCannotReplaceAuthoritativeCursor() {
        InventoryPlayer server = new InventoryPlayer(null);
        ItemStack original = stack(2);
        server.setItemStack(original);
        roundTrip(new PacketCreativeCursor(null)).apply(server, true, false);
        assertSame(original, server.getItemStack());
    }

    @Test
    public void packetCapturesCursorBeforeLaterLocalMutation() {
        InventoryPlayer server = new InventoryPlayer(null);
        ItemStack cursor = stack(12);
        PacketCreativeCursor packet = new PacketCreativeCursor(cursor);
        cursor.stackSize = 1;
        roundTrip(packet).apply(server, true, true);
        assertEquals(12, server.getItemStack().stackSize);
    }

    @Test
    public void clickAndDragRoundTripsPreserveCursorRevision() {
        assertActionRoundTrip(
            new PacketOverlayClick(2, 5, 1, PacketOverlayClick.MODE_NORMAL, 73),
            new PacketOverlayClick(),
            73);
        assertActionRoundTrip(new PacketOverlayDrag(2, Arrays.asList(0, 3, 5), 1, 74), new PacketOverlayDrag(), 74);
    }

    private static ItemStack stack(int count) {
        ItemStack stack = new ItemStack(item, count);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("marker", 42);
        stack.setTagCompound(tag);
        return stack;
    }

    private static PacketCreativeCursor roundTrip(PacketCreativeCursor sent) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            sent.toBytes(buffer);
            PacketCreativeCursor received = new PacketCreativeCursor();
            received.fromBytes(buffer);
            assertFalse(buffer.isReadable());
            return received;
        } finally {
            buffer.release();
        }
    }

    private static void assertActionRoundTrip(IMessage sent, IMessage received, int revision) {
        ByteBuf encoded = Unpooled.buffer();
        ByteBuf decoded = Unpooled.buffer();
        try {
            sent.toBytes(encoded);
            received.fromBytes(encoded);
            assertFalse(encoded.isReadable());
            received.toBytes(decoded);
            assertEquals(revision, decoded.getInt(decoded.writerIndex() - 4));
            encoded.readerIndex(0);
            assertEquals(encoded, decoded);
        } finally {
            encoded.release();
            decoded.release();
        }
    }
}
