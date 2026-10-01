package com.hepdd.backpackenhance.client.overlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import net.minecraft.creativetab.CreativeTabs;

import org.junit.Test;

public class CreativeOverlayVisibilityTest {

    @Test
    public void survivalInventoryTabAllowsOverlay() throws ReflectiveOperationException {
        assertTrue(isAllowed(CreativeTabs.tabInventory.getTabIndex()));
    }

    @Test
    public void otherCreativeTabsHideOverlay() throws ReflectiveOperationException {
        for (CreativeTabs tab : CreativeTabs.creativeTabArray) {
            if (tab != null && tab != CreativeTabs.tabInventory) assertFalse(isAllowed(tab.getTabIndex()));
        }
        assertFalse(isAllowed(-1));
        assertFalse(isAllowed(Integer.MAX_VALUE));
    }

    private static boolean isAllowed(int selectedTabIndex) throws ReflectiveOperationException {
        Method method = OverlayController.class.getDeclaredMethod("isCreativeInventoryTab", int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, selectedTabIndex);
    }
}
