package com.hepdd.backpackenhance.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.BeforeClass;
import org.junit.Test;

import com.hepdd.backpackenhance.client.overlay.BackpackOverlayPanel;
import com.hepdd.backpackenhance.client.overlay.OverlayClientState;
import com.hepdd.backpackenhance.net.packet.PacketWirelessAction;
import com.hepdd.backpackenhance.net.packet.PacketWirelessState;

import cpw.mods.fml.common.registry.FMLControlledNamespacedRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class WirelessInventoryTest {

    private static Item item;

    @BeforeClass
    public static void bootstrap() throws ReflectiveOperationException {
        item = new Item() {

            @Override
            public String getItemStackDisplayName(ItemStack stack) {
                return "Test item";
            }
        };
        // Normal Forge registration requires the game's LaunchClassLoader.
        Method register = FMLControlledNamespacedRegistry.class
            .getDeclaredMethod("addObjectRaw", int.class, String.class, Object.class);
        register.setAccessible(true);
        register.invoke(Item.itemRegistry, 30000, "backpackenhance:test", item);
    }

    @org.junit.After
    public void clearClientState() {
        OverlayClientState.clear();
    }

    private PacketWirelessState metadata(int session, int generation) {
        return new PacketWirelessState(session, 1003, generation, new ItemStack(item));
    }

    @Test
    public void clientPublishesCompleteChunksAndAbsoluteDeltas() {
        OverlayClientState.expectWirelessSession(12);
        OverlayClientState.enqueueWireless(metadata(12, 8));
        PacketWirelessState first = new PacketWirelessState(12, 1003, 8, null);
        first.reset = true;
        first.entries.add(new WirelessItemEntry(tagged(1, 1), 10000000000L));
        OverlayClientState.enqueueWireless(first);
        OverlayClientState.tick();
        BackpackTab tab = OverlayClientState.getTabs()
            .get(0);
        assertTrue(tab.wirelessLoading);
        assertEquals(0, tab.storageSlots);
        PacketWirelessState last = new PacketWirelessState(12, 1003, 8, null);
        last.complete = true;
        last.entries.add(new WirelessItemEntry(tagged(2, 1), 5));
        OverlayClientState.enqueueWireless(last);
        OverlayClientState.tick();
        assertEquals(2, tab.storageSlots);
        assertEquals(10000000000L, tab.getStoredAmount(0));
        PacketWirelessState change = new PacketWirelessState(12, 1003, 8, null);
        change.complete = true;
        change.entries.add(new WirelessItemEntry(tagged(1, 1), 7));
        change.entries.add(new WirelessItemEntry(tagged(2, 1), 0));
        OverlayClientState.enqueueWireless(change);
        OverlayClientState.tick();
        assertEquals(1, tab.storageSlots);
        assertEquals(7, tab.getStoredAmount(0));
    }

    @Test
    public void clientRejectsOldSessionsAndConnectionVersions() {
        OverlayClientState.expectWirelessSession(12);
        OverlayClientState.enqueueWireless(metadata(11, 7));
        OverlayClientState.tick();
        assertTrue(
            OverlayClientState.getTabs()
                .isEmpty());
        OverlayClientState.enqueueWireless(metadata(12, 8));
        OverlayClientState.tick();
        PacketWirelessState obsolete = new PacketWirelessState(12, 1003, 7, null);
        obsolete.removed = true;
        OverlayClientState.enqueueWireless(obsolete);
        OverlayClientState.tick();
        assertEquals(
            1,
            OverlayClientState.getTabs()
                .size());
        PacketWirelessState removed = new PacketWirelessState(12, 1003, 8, null);
        removed.removed = true;
        OverlayClientState.enqueueWireless(removed);
        OverlayClientState.tick();
        assertTrue(
            OverlayClientState.getTabs()
                .isEmpty());
    }

    @Test
    public void clientResetReplacesPreviousInventory() {
        OverlayClientState.expectWirelessSession(12);
        OverlayClientState.enqueueWireless(metadata(12, 8));
        PacketWirelessState initial = new PacketWirelessState(12, 1003, 8, null);
        initial.reset = true;
        initial.complete = true;
        initial.entries.add(new WirelessItemEntry(tagged(1, 1), 10));
        OverlayClientState.enqueueWireless(initial);
        OverlayClientState.tick();
        PacketWirelessState empty = new PacketWirelessState(12, 1003, 8, null);
        empty.reset = true;
        empty.complete = true;
        OverlayClientState.enqueueWireless(empty);
        OverlayClientState.tick();
        assertEquals(
            0,
            OverlayClientState.getTabs()
                .get(0).storageSlots);
    }

    @Test
    public void ordinarySearchPreservesSlotPositionsAndWidth() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab tab = new BackpackTab(3, 3, BackpackKind.FORESTRY, new ItemStack(item), 9, 125);
        tab.setSlotStack(7, tagged(1, 1));
        panel.setTabs(Collections.singletonList(tab));
        int width = (int) panelMethod("getExpandedWidth", int.class).invoke(panel, 9);
        configurePanelSearch(panel);
        List<?> slots = (List<?>) panelMethod("getVisibleSlots", BackpackTab.class).invoke(panel, tab);
        assertEquals(125, slots.size());
        assertEquals(7, slots.get(7));
        assertEquals(width, panelMethod("getExpandedWidth", int.class).invoke(panel, 9));
    }

    @Test
    public void wirelessSearchKeepsSixRowsAndStableWidth() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab tab = new BackpackTab(1003, 3, BackpackKind.AE2_WIRELESS, new ItemStack(item), 9, 0);
        java.util.ArrayList<WirelessItemEntry> entries = new java.util.ArrayList<WirelessItemEntry>();
        for (int i = 0; i < 100; i++) entries.add(new WirelessItemEntry(tagged(i, 1), 64));
        tab.setWirelessEntries(entries);
        panel.setTabs(Collections.singletonList(tab));
        int width = (int) panelMethod("getExpandedWidth", int.class).invoke(panel, 9);
        configurePanelSearch(panel);
        List<?> slots = (List<?>) panelMethod("getVisibleSlots", BackpackTab.class).invoke(panel, tab);
        assertEquals(54, slots.size());
        assertEquals(1, slots.get(0));
        assertTrue((int) slots.get(1) < 0);
        assertEquals(6, panelMethod("getDisplayRows", BackpackTab.class).invoke(panel, tab));
        assertEquals(width, panelMethod("getExpandedWidth", int.class).invoke(panel, 9));
    }

    @Test
    public void wirelessSelectionSurvivesPartialAndEmptyTabLists() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab physical = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        BackpackTab wireless = new BackpackTab(1003, 3, BackpackKind.AE2_WIRELESS, new ItemStack(item), 9, 0);
        panel.setTabs(Arrays.asList(physical, wireless));
        java.lang.reflect.Field preference = BackpackOverlayPanel.class.getDeclaredField("preferredTabId");
        preference.setAccessible(true);
        preference.setInt(panel, wireless.tabId);
        panel.setTabs(Arrays.asList(physical, wireless));
        assertEquals(1003, panel.getActiveTabId());
        panel.setTabs(Collections.emptyList());
        panel.setTabs(Collections.singletonList(physical));
        assertEquals(2, panel.getActiveTabId());
        panel.setTabs(Arrays.asList(physical, wireless));
        assertEquals(1003, panel.getActiveTabId());
    }

    @Test
    public void searchCyclesMatchingTabsAndScrollsToMatch() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab first = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 125);
        BackpackTab skipped = new BackpackTab(3, 3, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        BackpackTab second = new BackpackTab(4, 4, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        first.setSlotStack(80, tagged(1, 1));
        skipped.setSlotStack(0, tagged(2, 1));
        second.setSlotStack(8, tagged(1, 1));
        panel.setTabs(Arrays.asList(first, skipped, second));
        configurePanelSearch(panel);
        Method next = panelMethod("selectNextMatchingTab");
        assertEquals(true, next.invoke(panel));
        assertEquals(4, panel.getActiveTabId());
        assertEquals(4, panel.consumePendingActiveTabId());
        assertEquals(true, next.invoke(panel));
        assertEquals(2, panel.getActiveTabId());
        java.lang.reflect.Field rows = BackpackOverlayPanel.class.getDeclaredField("scrollRowsByPlayerSlot");
        rows.setAccessible(true);
        assertEquals(8, ((Map<?, ?>) rows.get(panel)).get(2));
        assertEquals(true, next.invoke(panel));
        assertEquals(4, panel.getActiveTabId());
    }

    @Test
    public void searchDoubleClicksAdvanceOncePerPair() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab first = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        BackpackTab second = new BackpackTab(4, 4, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        first.setSlotStack(0, tagged(1, 1));
        second.setSlotStack(0, tagged(1, 1));
        panel.setTabs(Arrays.asList(first, second));
        configurePanelSearch(panel);
        Method click = panelMethod("handleSearchClick", int.class, long.class);
        click.invoke(panel, 0, 1000L);
        assertEquals(2, panel.getActiveTabId());
        click.invoke(panel, 0, 1100L);
        assertEquals(4, panel.getActiveTabId());
        click.invoke(panel, 0, 1200L);
        assertEquals(4, panel.getActiveTabId());
        click.invoke(panel, 0, 1300L);
        assertEquals(2, panel.getActiveTabId());
        click.invoke(panel, 0, 2000L);
        click.invoke(panel, 0, 2300L);
        assertEquals(2, panel.getActiveTabId());
    }

    @Test
    public void searchNavigationKeepsSelectionForEmptyOrUnmatchedQuery() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab tab = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        tab.setSlotStack(0, tagged(2, 1));
        panel.setTabs(Collections.singletonList(tab));
        Method next = panelMethod("selectNextMatchingTab");
        assertEquals(false, next.invoke(panel));
        configurePanelSearch(panel);
        assertEquals(false, next.invoke(panel));
        assertEquals(2, panel.getActiveTabId());
        assertEquals(-1, panel.consumePendingActiveTabId());
    }

    @Test
    public void searchIncludesLoadedWirelessTabsAndSkipsLoadingTabs() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab physical = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        BackpackTab wireless = new BackpackTab(1003, 3, BackpackKind.AE2_WIRELESS, new ItemStack(item), 9, 0);
        physical.setSlotStack(0, tagged(1, 1));
        wireless.setWirelessEntries(Collections.singletonList(new WirelessItemEntry(tagged(1, 1), 64)));
        panel.setTabs(Arrays.asList(physical, wireless));
        configurePanelSearch(panel);
        Method next = panelMethod("selectNextMatchingTab");
        wireless.wirelessLoading = true;
        assertEquals(true, next.invoke(panel));
        assertEquals(2, panel.getActiveTabId());
        wireless.wirelessLoading = false;
        assertEquals(true, next.invoke(panel));
        assertEquals(1003, panel.getActiveTabId());
        assertEquals(true, next.invoke(panel));
        assertEquals(2, panel.getActiveTabId());
    }

    @Test
    public void fullBackpackViewportScrollsAcrossSlotsGapsAndScrollbar() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab tab = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 125);
        for (int slot = 0; slot < tab.storageSlots; slot++) tab.setSlotStack(slot, tagged(1, 64));
        panel.setTabs(Collections.singletonList(tab));
        setPanelInt(panel, "visibleRows", 6);
        setPanelInt(panel, "lastWidth", 200);
        setPanelInt(panel, "lastHeight", 200);
        int x = (int) panelMethod("getSlotGridX").invoke(panel);
        int y = (int) panelMethod("getSlotGridY").invoke(panel);
        int scrollbarX = (int) panelMethod("getScrollbarX").invoke(panel);
        Method viewport = panelMethod("isMouseOverSlotViewport", int.class, int.class);
        assertEquals(true, viewport.invoke(panel, x + 1, y + 1));
        assertEquals(true, viewport.invoke(panel, x + 17, y + 1));
        assertEquals(true, viewport.invoke(panel, scrollbarX + 1, y + 1));
        assertEquals(false, viewport.invoke(panel, x + 1, y - 1));
        Method scroll = panelMethod("scrollRows", BackpackTab.class, int.class);
        Method row = panelMethod("getScrollRow", BackpackTab.class);
        assertEquals(true, scroll.invoke(panel, tab, -120));
        assertEquals(1, row.invoke(panel, tab));
        for (int i = 0; i < 20; i++) scroll.invoke(panel, tab, -120);
        assertEquals(8, row.invoke(panel, tab));
        for (int i = 0; i < 20; i++) scroll.invoke(panel, tab, 120);
        assertEquals(0, row.invoke(panel, tab));
        for (int slot = 0; slot < tab.storageSlots; slot++) assertEquals(64, tab.getSlotStack(slot).stackSize);
    }

    @Test
    public void wirelessScrollingPreservesQuantitiesAndShortTabsHaveNoScroll() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab wireless = new BackpackTab(1003, 3, BackpackKind.AE2_WIRELESS, new ItemStack(item), 9, 0);
        java.util.ArrayList<WirelessItemEntry> entries = new java.util.ArrayList<WirelessItemEntry>();
        for (int i = 0; i < 55; i++) entries.add(new WirelessItemEntry(tagged(i, 1), Long.MAX_VALUE));
        wireless.setWirelessEntries(entries);
        panel.setTabs(Collections.singletonList(wireless));
        setPanelInt(panel, "visibleRows", 6);
        Method scroll = panelMethod("scrollRows", BackpackTab.class, int.class);
        assertEquals(true, scroll.invoke(panel, wireless, -120));
        assertEquals(1, panelMethod("getScrollRow", BackpackTab.class).invoke(panel, wireless));
        assertEquals(Long.MAX_VALUE, wireless.getStoredAmount(0));
        BackpackTab shortTab = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        panel.setTabs(Collections.singletonList(shortTab));
        assertEquals(false, scroll.invoke(panel, shortTab, -120));
        assertEquals(0, panelMethod("getScrollRow", BackpackTab.class).invoke(panel, shortTab));
    }

    @Test
    public void shortTabsReserveScrollbarSpaceAndRejectDragging() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab shortTab = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        panel.setTabs(Collections.singletonList(shortTab));
        int width = (int) panelMethod("getExpandedWidth", int.class).invoke(panel, 9);
        assertEquals(191, width);
        setPanelInt(panel, "lastWidth", width);
        setPanelInt(panel, "visibleRows", 3);
        int x = (int) panelMethod("getScrollbarX").invoke(panel);
        int y = (int) panelMethod("getScrollbarY").invoke(panel);
        assertEquals(172, x);
        assertEquals(15, panelMethod("getScrollbarThumbHeight").invoke(panel));
        assertEquals(y, panelMethod("getScrollbarThumbY", BackpackTab.class).invoke(panel, shortTab));
        assertEquals(false, panelMethod("hasVerticalScroll", BackpackTab.class).invoke(panel, shortTab));
        assertEquals(false, panelMethod("handleScrollbarPress", int.class, int.class).invoke(panel, x + 6, y + 5));
        assertEquals(false, panel.isScrollbarDragging());
        panel.dragScrollbarTo(y + 100);
        assertEquals(0, panelMethod("getScrollRow", BackpackTab.class).invoke(panel, shortTab));
        assertNull(panel.getSlotClickAt(x + 1, y + 1));
        BackpackTab wireless = new BackpackTab(1003, 3, BackpackKind.AE2_WIRELESS, new ItemStack(item), 9, 0);
        panel.setTabs(Collections.singletonList(wireless));
        assertEquals(width, panelMethod("getExpandedWidth", int.class).invoke(panel, 9));
    }

    @Test
    public void fixedSizeScrollbarDragsToBothEndsAndDisablesAfterTabChange() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab tab = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 125);
        panel.setTabs(Collections.singletonList(tab));
        setPanelInt(panel, "lastWidth", 191);
        setPanelInt(panel, "visibleRows", 6);
        int x = (int) panelMethod("getScrollbarX").invoke(panel);
        int y = (int) panelMethod("getScrollbarY").invoke(panel);
        int trackHeight = (int) panelMethod("getScrollbarTrackHeight").invoke(panel);
        assertEquals(108, trackHeight);
        assertEquals(15, panelMethod("getScrollbarThumbHeight").invoke(panel));
        Method press = panelMethod("handleScrollbarPress", int.class, int.class);
        Method row = panelMethod("getScrollRow", BackpackTab.class);
        assertEquals(true, press.invoke(panel, x + 11, y + 7));
        assertTrue(panel.isScrollbarDragging());
        panel.dragScrollbarTo(y + trackHeight + 100);
        assertEquals(8, row.invoke(panel, tab));
        assertEquals(y + trackHeight - 15, panelMethod("getScrollbarThumbY", BackpackTab.class).invoke(panel, tab));
        assertEquals(15, panelMethod("getScrollbarThumbHeight").invoke(panel));
        panel.dragScrollbarTo(y - 100);
        assertEquals(0, row.invoke(panel, tab));
        panel.releaseScrollbar();
        assertEquals(false, press.invoke(panel, x + 12, y + 7));
        assertEquals(true, press.invoke(panel, x + 6, y + trackHeight - 1));
        assertEquals(8, row.invoke(panel, tab));
        BackpackTab shortTab = new BackpackTab(3, 3, BackpackKind.FORESTRY, new ItemStack(item), 9, 27);
        panel.setTabs(Collections.singletonList(shortTab));
        panel.dragScrollbarTo(y + trackHeight);
        assertEquals(false, panel.isScrollbarDragging());
        assertEquals(0, row.invoke(panel, shortTab));
    }

    @Test
    public void wirelessEmptyAndFilteredListsUseDisabledSixRowViewport() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab tab = new BackpackTab(1003, 3, BackpackKind.AE2_WIRELESS, new ItemStack(item), 9, 0);
        panel.setTabs(Collections.singletonList(tab));
        setPanelInt(panel, "visibleRows", 6);
        Method rows = panelMethod("getDisplayRows", BackpackTab.class);
        Method scrollable = panelMethod("hasVerticalScroll", BackpackTab.class);
        assertEquals(6, rows.invoke(panel, tab));
        assertEquals(false, scrollable.invoke(panel, tab));
        java.util.ArrayList<WirelessItemEntry> entries = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) entries.add(new WirelessItemEntry(tagged(i, 1), 64));
        tab.setWirelessEntries(entries);
        assertEquals(true, scrollable.invoke(panel, tab));
        panelMethod("scrollRows", BackpackTab.class, int.class).invoke(panel, tab, -120);
        configurePanelSearch(panel);
        java.lang.reflect.Field cache = BackpackOverlayPanel.class.getDeclaredField("filteredSlots");
        cache.setAccessible(true);
        ((Map<?, ?>) cache.get(panel)).clear();
        assertEquals(6, rows.invoke(panel, tab));
        assertEquals(false, scrollable.invoke(panel, tab));
        assertEquals(0, panelMethod("getScrollRow", BackpackTab.class).invoke(panel, tab));
    }

    @Test
    public void narrowAndZeroRowViewportsExcludeHiddenSlotsAndDragging() throws ReflectiveOperationException {
        BackpackOverlayPanel panel = new BackpackOverlayPanel();
        BackpackTab tab = new BackpackTab(2, 2, BackpackKind.FORESTRY, new ItemStack(item), 9, 125);
        panel.setTabs(Collections.singletonList(tab));
        setPanelInt(panel, "lastWidth", 180);
        setPanelInt(panel, "visibleRows", 6);
        int x = (int) panelMethod("getSlotGridX").invoke(panel);
        int y = (int) panelMethod("getSlotGridY").invoke(panel);
        assertNull(panel.getSlotClickAt(x + 8 * 18, y + 1));
        assertEquals(
            false,
            panelMethod("isMouseOverSlotViewport", int.class, int.class).invoke(panel, x + 8 * 18, y + 1));
        setPanelInt(panel, "visibleRows", 0);
        assertEquals(0, panelMethod("getScrollbarThumbHeight").invoke(panel));
        assertEquals(false, panelMethod("hasVerticalScroll", BackpackTab.class).invoke(panel, tab));
        assertEquals(false, panelMethod("handleScrollbarPress", int.class, int.class).invoke(panel, x, y));
    }

    private static void setPanelInt(BackpackOverlayPanel panel, String name, int value)
        throws ReflectiveOperationException {
        java.lang.reflect.Field field = BackpackOverlayPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(panel, value);
    }

    private static Method panelMethod(String name, Class<?>... parameters) throws ReflectiveOperationException {
        Method method = BackpackOverlayPanel.class.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static void configurePanelSearch(BackpackOverlayPanel panel) throws ReflectiveOperationException {
        java.lang.reflect.Field text = BackpackOverlayPanel.class.getDeclaredField("searchText");
        text.setAccessible(true);
        text.set(panel, "variant 1");
        java.lang.reflect.Field filter = BackpackOverlayPanel.class.getDeclaredField("searchFilter");
        filter.setAccessible(true);
        filter.set(
            panel,
            (Predicate<ItemStack>) stack -> stack.getTagCompound()
                .getInteger("variant") == 1);
    }

    private ItemStack tagged(int variant, int count) {
        ItemStack stack = new ItemStack(item, count);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("variant", variant);
        stack.setTagCompound(tag);
        return stack;
    }

    @Test
    public void identitiesIgnoreQuantityAndPreserveNbt() {
        WirelessItemEntry first = new WirelessItemEntry(tagged(1, 64), Long.MAX_VALUE);
        WirelessItemEntry second = new WirelessItemEntry(tagged(1, 2), 0);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, new WirelessItemEntry(tagged(2, 1), 64));
        Map<WirelessItemEntry, WirelessItemEntry> entries = new HashMap<WirelessItemEntry, WirelessItemEntry>();
        entries.put(first, first);
        entries.put(second, second);
        assertEquals(1, entries.size());
        assertEquals(1, first.template.stackSize);
    }

    @Test
    public void tabRetainsLongQuantityAndSafeDisplayStack() {
        ItemStack terminal = new ItemStack(item);
        BackpackTab tab = new BackpackTab(1000, 0, BackpackKind.AE2_WIRELESS, terminal, 9, 0);
        tab.setWirelessEntries(Arrays.asList(new WirelessItemEntry(tagged(1, 1), Long.MAX_VALUE)));
        assertTrue(tab.isWireless());
        assertEquals(1, tab.storageSlots);
        assertEquals(Long.MAX_VALUE, tab.getStoredAmount(0));
        assertEquals(64, tab.getSlotStack(0).stackSize);
        assertNull(tab.getSlotStack(-1));
        assertEquals(0, tab.getStoredAmount(-1));
    }

    @Test
    public void fullAndRemovalQuantitiesRoundTrip() {
        PacketWirelessState original = new PacketWirelessState(12, 1003, 8, new ItemStack(item));
        original.reset = true;
        original.complete = true;
        original.entries.add(new WirelessItemEntry(tagged(1, 1), Long.MAX_VALUE));
        original.entries.add(new WirelessItemEntry(tagged(2, 1), 0));
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            PacketWirelessState decoded = new PacketWirelessState();
            decoded.fromBytes(buffer);
            assertEquals(12, decoded.sessionId);
            assertEquals(1003, decoded.tabId);
            assertEquals(8, decoded.generation);
            assertTrue(decoded.reset);
            assertTrue(decoded.complete);
            assertEquals(2, decoded.entries.size());
            assertEquals(Long.MAX_VALUE, decoded.entries.get(0).amount);
            assertEquals(0, decoded.entries.get(1).amount);
            assertEquals(original.entries.get(0), decoded.entries.get(0));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void cursorAcknowledgementIncludesEmptyCursor() {
        PacketWirelessState original = new PacketWirelessState(12, 1003, 8, null);
        original.updateCursor = true;
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            PacketWirelessState decoded = new PacketWirelessState();
            decoded.fromBytes(buffer);
            assertTrue(decoded.updateCursor);
            assertNull(decoded.cursor);
            assertNull(decoded.terminal);
            assertTrue(decoded.entries.isEmpty());
        } finally {
            buffer.release();
        }
    }

    @Test
    public void clickCarriesCapturedIdentityAndGeneration() {
        ItemStack clicked = tagged(1, 64);
        PacketWirelessAction original = new PacketWirelessAction(
            12,
            4,
            1003,
            8,
            PacketWirelessAction.CLICK,
            1,
            false,
            clicked);
        clicked.getTagCompound()
            .setInteger("variant", 2);
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            PacketWirelessAction decoded = new PacketWirelessAction();
            decoded.fromBytes(buffer);
            assertEquals(12, decoded.sessionId);
            assertEquals(4, decoded.windowId);
            assertEquals(8, decoded.generation);
            assertEquals(1, decoded.template.stackSize);
            assertEquals(
                1,
                decoded.template.getTagCompound()
                    .getInteger("variant"));
        } finally {
            buffer.release();
        }
    }
}
