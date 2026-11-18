package com.hepdd.backpackenhance.net.packet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.hepdd.backpackenhance.client.overlay.OverlayClientState;
import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.integration.BackpackTab;
import com.hepdd.backpackenhance.integration.BackpackTabColors;
import com.hepdd.backpackenhance.server.overlay.OverlaySlotSnapshot;
import com.hepdd.backpackenhance.server.overlay.OverlaySnapshotFactory;
import com.hepdd.backpackenhance.server.overlay.OverlayTabSnapshot;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketOverlayStateTest {

    private static Item item;

    @BeforeClass
    public static void bootstrap() throws ReflectiveOperationException {
        item = new Item();
        Method register = FMLControlledNamespacedRegistry.class
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 29980, "backpackenhance:overlay_state_test", item);
    }

    @After
    public void clearClientState() {
        OverlayClientState.clear();
    }

    @Test
    public void skipsNullSlotsInWirePayloadAndRestoresSparsePositions() {
        List<OverlaySlotSnapshot> sparseInput = new ArrayList<OverlaySlotSnapshot>();
        for (int i = 0; i < 27; i++) {
            if (i == 2) {
                sparseInput.add(new OverlaySlotSnapshot(i, new ItemStack(item, 5)));
            } else if (i == 20) {
                sparseInput.add(new OverlaySlotSnapshot(i, new ItemStack(item, 17)));
            } else {
                sparseInput.add(new OverlaySlotSnapshot(i, null));
            }
        }
        OverlayTabSnapshot snapshot = new OverlayTabSnapshot(
            0,
            9,
            BackpackKind.BRADS,
            "Brad's",
            9,
            27,
            new ItemStack(item),
            sparseInput);

        PacketOverlayState decoded = roundTrip(new PacketOverlayState(Collections.singletonList(snapshot)));
        new PacketOverlayState.Handler().onMessage(decoded, null);
        OverlayClientState.tick();

        List<BackpackTab> tabs = OverlayClientState.getTabs();
        assertEquals(1, tabs.size());
        BackpackTab tab = tabs.get(0);
        assertEquals(27, tab.storageSlots);
        assertEquals(5, tab.getSlotStack(2).stackSize);
        assertEquals(17, tab.getSlotStack(20).stackSize);
        for (int i = 0; i < 27; i++) {
            if (i != 2 && i != 20) {
                assertNull(tab.getSlotStack(i));
            }
        }
    }

    @Test
    public void serializationSkipsNullSlotBytesComparedToDirectSlotCount() throws ReflectiveOperationException {
        List<OverlaySlotSnapshot> slots = new ArrayList<OverlaySlotSnapshot>();
        for (int i = 0; i < 27; i++) {
            slots.add(new OverlaySlotSnapshot(i, i == 0 ? new ItemStack(item, 1) : null));
        }
        OverlayTabSnapshot snapshot = new OverlayTabSnapshot(
            0,
            9,
            BackpackKind.BRADS,
            "Brad's",
            9,
            27,
            new ItemStack(item),
            slots);
        PacketOverlayState decoded = roundTrip(new PacketOverlayState(Collections.singletonList(snapshot)));
        List<OverlayTabSnapshot> decodedTabs = decodedTabs(decoded);
        assertEquals(1, decodedTabs.get(0).slots.size());
        assertEquals(0, decodedTabs.get(0).slots.get(0).slot);
    }

    @Test
    public void stripsForestryStorageNbtWhilePreservingMetadataAndSource() throws ReflectiveOperationException {
        ItemStack original = new ItemStack(item, 1, 2);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("UID", 987654);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "Miner Pack");
        tag.setTag("display", display);
        NBTTagCompound slots = new NBTTagCompound();
        slots.setTag("0", new NBTTagCompound());
        tag.setTag("Slots", slots);
        NBTTagList items = new NBTTagList();
        items.appendTag(new NBTTagCompound());
        tag.setTag("Items", items);
        original.setTagCompound(tag);

        OverlayTabSnapshot snapshot = new OverlayTabSnapshot(
            0,
            9,
            BackpackKind.FORESTRY,
            "Miner Pack",
            5,
            15,
            original,
            Collections.<OverlaySlotSnapshot>emptyList(),
            2,
            3,
            true,
            true);

        PacketOverlayState decoded = roundTrip(new PacketOverlayState(Collections.singletonList(snapshot)));

        // Original server stack must remain untouched.
        assertTrue(
            original.getTagCompound()
                .hasKey("Slots"));
        assertTrue(
            original.getTagCompound()
                .hasKey("Items"));

        OverlayTabSnapshot decodedTab = decodedTabs(decoded).get(0);
        assertEquals(2, decodedTab.modeId);
        assertEquals(3, decodedTab.nextModeId);
        assertTrue(decodedTab.modeCycleAvailable);
        assertTrue(decodedTab.resupplyEnabled);

        NBTTagCompound wireTag = decodedTab.backpackStack.getTagCompound();
        assertNotNull(wireTag);
        assertFalse(wireTag.hasKey("Slots"));
        assertFalse(wireTag.hasKey("Items"));
        assertEquals(987654, wireTag.getInteger("UID"));
        assertEquals(
            "Miner Pack",
            wireTag.getCompoundTag("display")
                .getString("Name"));
        assertEquals(2, decodedTab.backpackStack.getItemDamage());
    }

    @Test
    public void stripsAdventureInventoryNbtWhilePreservingSkinAndNonInventoryFields() {
        ItemStack original = new ItemStack(item);
        NBTTagCompound root = new NBTTagCompound();
        root.setByte("type", (byte) 4);
        NBTTagCompound display = new NBTTagCompound();
        display.setString("Name", "Cow Pack");
        root.setTag("display", display);

        NBTTagCompound wearable = new NBTTagCompound();
        wearable.setByte("type", (byte) 7);
        NBTTagList inventory = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setInteger("Slot", 0);
        inventory.appendTag(entry);
        wearable.setTag("inventory", inventory);
        NBTTagCompound leftTank = new NBTTagCompound();
        leftTank.setString("FluidName", "water");
        wearable.setTag("leftTank", leftTank);
        root.setTag("wearableData", wearable);

        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setTag("ABPItems", inventory.copy());
        legacy.setString("colorName", "Cow");
        root.setTag("backpackData", legacy);
        original.setTagCompound(root);

        int expectedColor = BackpackTabColors.resolve(BackpackKind.ADVENTURE, original);
        ItemStack stripped = OverlaySnapshotFactory.stripRedundantStorageNbt(BackpackKind.ADVENTURE, original);

        // Original untouched
        assertTrue(
            original.getTagCompound()
                .getCompoundTag("wearableData")
                .hasKey("inventory"));
        assertTrue(
            original.getTagCompound()
                .getCompoundTag("backpackData")
                .hasKey("ABPItems"));

        NBTTagCompound strippedTag = stripped.getTagCompound();
        assertNotNull(strippedTag);
        assertEquals((byte) 4, strippedTag.getByte("type"));
        assertEquals(
            "Cow Pack",
            strippedTag.getCompoundTag("display")
                .getString("Name"));
        assertFalse(
            strippedTag.getCompoundTag("wearableData")
                .hasKey("inventory"));
        assertEquals(
            (byte) 7,
            strippedTag.getCompoundTag("wearableData")
                .getByte("type"));
        assertEquals(
            "water",
            strippedTag.getCompoundTag("wearableData")
                .getCompoundTag("leftTank")
                .getString("FluidName"));
        assertFalse(
            strippedTag.getCompoundTag("backpackData")
                .hasKey("ABPItems"));
        assertEquals(
            "Cow",
            strippedTag.getCompoundTag("backpackData")
                .getString("colorName"));
        assertEquals(expectedColor, BackpackTabColors.resolve(BackpackKind.ADVENTURE, stripped));
    }

    @Test
    public void preservesBradsMetadataNbtIntact() {
        ItemStack original = new ItemStack(item, 1, 105);
        NBTTagCompound root = new NBTTagCompound();
        root.setString("backpack-UID", "uuid-123");
        root.setString("name", "backpack.leather");
        root.setString("customName", "Traveler");
        original.setTagCompound(root);

        ItemStack result = OverlaySnapshotFactory.stripRedundantStorageNbt(BackpackKind.BRADS, original);
        assertTrue(ItemStack.areItemStacksEqual(original, result));
        assertEquals(
            "uuid-123",
            result.getTagCompound()
                .getString("backpack-UID"));
        assertEquals(
            "Traveler",
            result.getTagCompound()
                .getString("customName"));
    }

    @SuppressWarnings("unchecked")
    private static List<OverlayTabSnapshot> decodedTabs(PacketOverlayState packet) throws ReflectiveOperationException {
        java.lang.reflect.Field tabsField = PacketOverlayState.class.getDeclaredField("tabs");
        tabsField.setAccessible(true);
        return (List<OverlayTabSnapshot>) tabsField.get(packet);
    }

    private static PacketOverlayState roundTrip(PacketOverlayState sent) {
        ByteBuf buf = Unpooled.buffer();
        try {
            sent.toBytes(buf);
            PacketOverlayState received = new PacketOverlayState();
            received.fromBytes(buf);
            assertFalse(buf.isReadable());
            return received;
        } finally {
            buf.release();
        }
    }
}
