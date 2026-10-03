package com.hepdd.backpackenhance.server.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.hepdd.backpackenhance.integration.BackpackInventoryAccess;
import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.integration.BackpackTab;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import de.eydamos.backpack.item.ItemBackpack;
import de.eydamos.backpack.item.ItemWorkbenchBackpack;
import de.eydamos.backpack.misc.ConfigurationBackpack;

public class OverlayShiftInHandlerTest {

    private static final BackpackKind[] KINDS = { BackpackKind.BRADS, BackpackKind.BRADS_WORKBENCH,
        BackpackKind.BRADS_ENDER };
    private static Item backpackItem;
    private static Item workbenchItem;
    private static Item ordinaryItem;
    private String[] originalBlacklist;

    @BeforeClass
    public static void bootstrap() throws ReflectiveOperationException {
        backpackItem = new ItemBackpack();
        workbenchItem = new ItemWorkbenchBackpack();
        ordinaryItem = new Item();
        Method register = FMLControlledNamespacedRegistry.class
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 29990, "backpackenhance:shift_backpack_test", backpackItem);
        register.invoke(Item.itemRegistry, 29991, "backpackenhance:shift_workbench_test", workbenchItem);
        register.invoke(Item.itemRegistry, 29992, "backpackenhance:shift_item_test", ordinaryItem);
    }

    @Before
    public void resetBlacklist() {
        originalBlacklist = ConfigurationBackpack.DISALLOW_ITEM_IDS;
        ConfigurationBackpack.DISALLOW_ITEM_IDS = new String[0];
    }

    @After
    public void restoreBlacklist() {
        ConfigurationBackpack.DISALLOW_ITEM_IDS = originalBlacklist;
    }

    @Test
    public void shiftCannotInsertBackpackIntoItself() throws ReflectiveOperationException {
        for (BackpackKind kind : KINDS) {
            ItemStack backpack = backpack(kind);
            BackpackTab tab = tab(kind, backpack);
            IInventory inventory = new InventoryBasic("Backpack", true, 3);
            ItemStack moving = backpack.copy();
            assertSame(moving, shiftInsert(tab, inventory, moving));
            assertEquals(1, moving.stackSize);
            assertEquals(
                42,
                backpack.getTagCompound()
                    .getInteger("contents"));
            assertEmpty(inventory);
        }
    }

    @Test
    public void shiftCannotInsertAnotherBackpackIntoSelectedBackpack() throws ReflectiveOperationException {
        for (BackpackKind targetKind : KINDS) {
            for (BackpackKind sourceKind : KINDS) {
                IInventory inventory = new InventoryBasic("Backpack", true, 3);
                ItemStack moving = backpack(sourceKind);
                assertSame(moving, shiftInsert(tab(targetKind, backpack(targetKind)), inventory, moving));
                assertEquals(1, moving.stackSize);
                assertEmpty(inventory);
            }
        }
    }

    @Test
    public void nativeSlotRulesRejectBackpacksForAllInsertionActions() {
        for (BackpackKind targetKind : KINDS) {
            IInventory inventory = new InventoryBasic("Backpack", true, 3);
            for (BackpackKind sourceKind : KINDS) {
                ItemStack stack = backpack(sourceKind);
                assertTrue(inventory.isItemValidForSlot(0, stack));
                assertFalse(BackpackInventoryAccess.isItemValid(targetKind, inventory, 0, stack));
            }
        }
    }

    @Test
    public void shiftRespectsConfiguredBlacklistForMergingAndEmptySlots() throws ReflectiveOperationException {
        ConfigurationBackpack.DISALLOW_ITEM_IDS = new String[] { "backpackenhance:shift_item_test" };
        for (BackpackKind kind : KINDS) {
            IInventory inventory = new InventoryBasic("Backpack", true, 3);
            inventory.setInventorySlotContents(0, new ItemStack(ordinaryItem, 5));
            ItemStack moving = new ItemStack(ordinaryItem, 10);
            assertSame(moving, shiftInsert(tab(kind, backpack(kind)), inventory, moving));
            assertEquals(10, moving.stackSize);
            assertEquals(5, inventory.getStackInSlot(0).stackSize);
            assertNull(inventory.getStackInSlot(1));
            assertNull(inventory.getStackInSlot(2));
        }
    }

    @Test
    public void shiftStillMergesAndInsertsOrdinaryItems() throws ReflectiveOperationException {
        for (BackpackKind kind : KINDS) {
            IInventory inventory = new InventoryBasic("Backpack", true, 3);
            inventory.setInventorySlotContents(0, new ItemStack(ordinaryItem, 60));
            assertNull(shiftInsert(tab(kind, backpack(kind)), inventory, new ItemStack(ordinaryItem, 10)));
            assertEquals(64, inventory.getStackInSlot(0).stackSize);
            assertEquals(6, inventory.getStackInSlot(1).stackSize);
            assertNull(inventory.getStackInSlot(2));
        }
    }

    @Test
    public void nativeSlotStillRespectsInventoryRestrictions() {
        IInventory inventory = new InventoryBasic("Restricted", true, 3) {

            @Override
            public boolean isItemValidForSlot(int slot, ItemStack stack) {
                return false;
            }
        };
        for (BackpackKind kind : KINDS) {
            assertFalse(BackpackInventoryAccess.isItemValid(kind, inventory, 0, new ItemStack(ordinaryItem)));
            assertTrue(BackpackInventoryAccess.isItemValid(kind, inventory, 0, null));
        }
    }

    private static ItemStack backpack(BackpackKind kind) {
        ItemStack stack = new ItemStack(kind == BackpackKind.BRADS_WORKBENCH ? workbenchItem : backpackItem);
        if (kind == BackpackKind.BRADS_ENDER) stack.setItemDamage(31999);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("contents", 42);
        stack.setTagCompound(tag);
        return stack;
    }

    private static BackpackTab tab(BackpackKind kind, ItemStack backpack) {
        return new BackpackTab(9, 9, kind, backpack, 9, 3);
    }

    private static ItemStack shiftInsert(BackpackTab tab, IInventory inventory, ItemStack moving)
        throws ReflectiveOperationException {
        Method insert = OverlayShiftInHandler.class
            .getDeclaredMethod("insertIntoInventory", BackpackTab.class, IInventory.class, ItemStack.class, int.class);
        insert.setAccessible(true);
        return (ItemStack) insert.invoke(null, tab, inventory, moving, inventory.getSizeInventory());
    }

    private static void assertEmpty(IInventory inventory) {
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            assertNull(inventory.getStackInSlot(slot));
        }
    }
}
