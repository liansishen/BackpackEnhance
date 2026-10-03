package com.hepdd.backpackenhance.client.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Collections;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.net.packet.PacketOverlayState;
import com.hepdd.backpackenhance.server.overlay.OverlayTabSnapshot;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class OverlayCursorSynchronizationTest {

    private static Item item;

    @BeforeClass
    public static void bootstrap() throws ReflectiveOperationException {
        item = new Item();
        Method register = FMLControlledNamespacedRegistry.class
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 29999, "backpackenhance:cursor_test", item);
    }

    @After
    public void clearState() {
        OverlayClientState.clear();
    }

    @Test
    public void refreshPreservesPickedUpBackpackAndAllowsPuttingItBack() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        ItemStack backpack = backpack();
        inventory.setInventorySlotContents(9, backpack);
        inventory.setItemStack(inventory.decrStackSize(9, 1));

        receive(new PacketOverlayState(Collections.emptyList()));
        OverlayClientState.applyPendingCursor(inventory);

        assertSame(backpack, inventory.getItemStack());
        assertNull(inventory.getStackInSlot(9));
        assertTrue(
            OverlayClientState.getTabs()
                .isEmpty());
        inventory.setInventorySlotContents(10, inventory.getItemStack());
        inventory.setItemStack(null);
        receive(refresh(BackpackKind.ADVENTURE, 10));
        OverlayClientState.applyPendingCursor(inventory);
        assertSame(backpack, inventory.getStackInSlot(10));
        assertEquals(
            42,
            inventory.getStackInSlot(10)
                .getTagCompound()
                .getInteger("contents"));
        assertNull(inventory.getItemStack());
        assertEquals(
            10,
            OverlayClientState.getTabs()
                .get(0).playerSlot);
    }

    @Test
    public void refreshUpdatesEveryPhysicalBackpackKindWithoutChangingCursor() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        ItemStack carried = backpack();
        inventory.setItemStack(carried);
        for (BackpackKind kind : BackpackKind.values()) {
            if (kind == BackpackKind.AE2_WIRELESS) continue;
            receive(refresh(kind, 9));
            OverlayClientState.applyPendingCursor(inventory);
            assertSame(carried, inventory.getItemStack());
            assertEquals(
                kind,
                OverlayClientState.getTabs()
                    .get(0).kind);
        }
    }

    @Test
    public void actionSynchronizesNonemptyCursorIncludingNbt() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        ItemStack cursor = backpack();
        receive(new PacketOverlayState(Collections.emptyList(), cursor));
        OverlayClientState.applyPendingCursor(inventory);
        assertTrue(ItemStack.areItemStacksEqual(cursor, inventory.getItemStack()));
        assertNotSame(cursor, inventory.getItemStack());
        assertEquals(
            42,
            inventory.getItemStack()
                .getTagCompound()
                .getInteger("contents"));
    }

    @Test
    public void actionCanClearCursor() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        inventory.setItemStack(backpack());
        receive(new PacketOverlayState(Collections.emptyList(), null));
        OverlayClientState.applyPendingCursor(inventory);
        assertNull(inventory.getItemStack());
    }

    @Test
    public void refreshPreservesPendingActionCursorInSameTick() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        ItemStack cursor = backpack();
        receive(new PacketOverlayState(Collections.emptyList(), cursor));
        receive(refresh(BackpackKind.BRADS, 9));
        OverlayClientState.applyPendingCursor(inventory);
        assertTrue(ItemStack.areItemStacksEqual(cursor, inventory.getItemStack()));
        assertEquals(
            BackpackKind.BRADS,
            OverlayClientState.getTabs()
                .get(0).kind);
    }

    @Test
    public void consumedActionDoesNotOverwriteLaterInventoryClick() {
        InventoryPlayer inventory = new InventoryPlayer(null);
        receive(new PacketOverlayState(Collections.emptyList(), null));
        OverlayClientState.applyPendingCursor(inventory);
        ItemStack pickedUp = backpack();
        inventory.setItemStack(pickedUp);
        OverlayClientState.applyPendingCursor(inventory);
        assertSame(pickedUp, inventory.getItemStack());
    }

    private static ItemStack backpack() {
        ItemStack stack = new ItemStack(item);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("contents", 42);
        stack.setTagCompound(tag);
        return stack;
    }

    private static PacketOverlayState refresh(BackpackKind kind, int playerSlot) {
        OverlayTabSnapshot snapshot = new OverlayTabSnapshot(
            playerSlot,
            playerSlot,
            kind,
            "Backpack",
            kind.columns,
            kind.storageSlots,
            backpack(),
            Collections.emptyList());
        return new PacketOverlayState(Collections.singletonList(snapshot));
    }

    private static void receive(PacketOverlayState sent) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            sent.toBytes(buffer);
            PacketOverlayState received = new PacketOverlayState();
            received.fromBytes(buffer);
            assertFalse(buffer.isReadable());
            new PacketOverlayState.Handler().onMessage(received, null);
            OverlayClientState.tick();
            assertTrue(OverlayClientState.hasState());
            assertTrue(OverlayClientState.consumeDirty());
        } finally {
            buffer.release();
        }
    }
}
