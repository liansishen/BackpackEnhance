package com.hepdd.backpackenhance.client.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.hepdd.backpackenhance.Config;
import com.hepdd.backpackenhance.client.overlay.OverlayGuiTextures.Sprite;
import com.hepdd.backpackenhance.integration.BackpackKind;
import com.hepdd.backpackenhance.integration.BackpackTab;

public class OverlayTabLayoutTest {

    private BackpackOverlayPanel panel;
    private int oldWidth;
    private int oldColumns;
    private final Item item = new Item() {

        @Override
        public String getItemStackDisplayName(ItemStack stack) {
            return "Test backpack";
        }
    };

    @Before
    public void setUp() throws ReflectiveOperationException {
        oldWidth = Config.overlayWidth;
        oldColumns = Config.overlayColumns;
        Config.overlayWidth = -1;
        Config.overlayColumns = 9;
        panel = new BackpackOverlayPanel();
        set("lastWidth", method("getExpandedWidth", int.class).invoke(panel, 9));
    }

    @After
    public void restoreConfig() {
        Config.overlayWidth = oldWidth;
        Config.overlayColumns = oldColumns;
    }

    @Test
    public void defaultWidthFitsSixTabsWithCenteredSmallerIcons() throws ReflectiveOperationException {
        assertEquals(191, get("lastWidth"));
        assertEquals(22, Sprite.TAB_NORMAL.width);
        assertEquals(18, Sprite.TAB_NORMAL.height);
        assertEquals(14, get("TAB_ICON_SIZE"));
        assertEquals(22, get("TAB_BTN_WIDTH"));
        assertEquals(24, get("TAB_WIDTH"));
        assertEquals(2, ((int) get("TAB_BTN_HEIGHT") - (int) get("TAB_ICON_SIZE")) / 2);
        for (int count : new int[] { 1, 6, 7, 12, 13 }) {
            panel.setTabs(tabs(count));
            assertEquals(6, method("getPageSize", int.class).invoke(panel, 177));
            assertEquals(count > 6, method("needsTabPaging", int.class).invoke(panel, 177));
            assertEquals((count + 5) / 6, method("getPageCount", int.class).invoke(panel, 6));
        }
    }

    @Test
    public void pagingArrowsReachEveryTabAndStopAtBothEnds() throws ReflectiveOperationException {
        panel.setTabs(tabs(13));
        Method arrows = method("handleTabArrowClick", int.class, int.class);
        assertEquals(true, arrows.invoke(panel, 8, 20));
        assertEquals(0, get("tabPage"));
        for (int page = 0; page < 3; page++) {
            assertEquals(page, get("tabPage"));
            int count = page == 2 ? 1 : 6;
            for (int i = 0; i < 6; i++) assertEquals(i < count ? page * 6 + i : -1, tabAt(18 + i * 24, 20));
            assertEquals(true, arrows.invoke(panel, 175, 20));
        }
        assertEquals(2, get("tabPage"));
        for (int page = 1; page >= 0; page--) {
            arrows.invoke(panel, 8, 20);
            assertEquals(page, get("tabPage"));
        }
        assertEquals(0, panel.getActiveTabId());
    }

    @Test
    public void tabHitboxesMatchButtonsAndExcludeGapsAndArrows() throws ReflectiveOperationException {
        for (int count : new int[] { 6, 7 }) {
            panel.setTabs(tabs(count));
            int firstX = count == 6 ? 7 : 18;
            for (int i = 0; i < 6; i++) {
                int x = firstX + i * 24;
                assertEquals(i, tabAt(x, 18));
                assertEquals(i, tabAt(x + 21, 35));
                assertEquals(-1, tabAt(x + 22, 20));
                assertEquals(-1, tabAt(x + 23, 20));
                assertEquals(-1, tabAt(x, 17));
                assertEquals(-1, tabAt(x, 36));
            }
            assertEquals(-1, tabAt(firstX - 1, 20));
            assertEquals(-1, tabAt(174, 20));
            assertEquals(-1, tabAt(183, 20));
        }
    }

    @Test
    public void resizedWindowsAndRemovedTabsClampTheCurrentPage() throws ReflectiveOperationException {
        panel.setTabs(tabs(13));
        assertEquals(3, method("getPageSize", int.class).invoke(panel, 114));
        assertEquals(8, method("getPageSize", int.class).invoke(panel, 225));
        set("lastWidth", 128);
        set("tabPage", 4);
        assertEquals(12, tabAt(18, 20));
        set("lastWidth", 239);
        method("clampTabPage").invoke(panel);
        assertEquals(1, get("tabPage"));
        assertEquals(8, tabAt(18, 20));
        set("lastWidth", 191);
        panel.setTabs(tabs(6));
        assertEquals(0, get("tabPage"));
        assertEquals(false, method("needsTabPaging", int.class).invoke(panel, 177));
        assertEquals(5, tabAt(7 + 5 * 24, 20));
    }

    @Test
    public void searchSelectionMovesToTheSixTabPageContainingTheMatch() throws ReflectiveOperationException {
        List<BackpackTab> tabs = tabs(13);
        tabs.get(6)
            .setSlotStack(0, new ItemStack(item));
        panel.setTabs(tabs);
        set("searchText", "match");
        set("searchFilter", (Predicate<ItemStack>) stack -> true);
        assertTrue((boolean) method("selectNextMatchingTab").invoke(panel));
        assertEquals(6, panel.getActiveTabId());
        assertEquals(1, get("tabPage"));
        assertEquals(6, tabAt(18, 20));
        assertFalse((boolean) method("handleTabArrowClick", int.class, int.class).invoke(panel, 18, 20));
    }

    private List<BackpackTab> tabs(int count) {
        List<BackpackTab> tabs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tabs.add(new BackpackTab(i, i, BackpackKind.FORESTRY, new ItemStack(item), 9, 27));
        }
        return tabs;
    }

    private int tabAt(int x, int y) throws ReflectiveOperationException {
        return (int) method("getTabIndexAt", int.class, int.class).invoke(panel, x, y);
    }

    private Object get(String name) throws ReflectiveOperationException {
        Field field = BackpackOverlayPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(panel);
    }

    private void set(String name, Object value) throws ReflectiveOperationException {
        Field field = BackpackOverlayPanel.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(panel, value);
    }

    private static Method method(String name, Class<?>... types) throws ReflectiveOperationException {
        Method method = BackpackOverlayPanel.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }
}
