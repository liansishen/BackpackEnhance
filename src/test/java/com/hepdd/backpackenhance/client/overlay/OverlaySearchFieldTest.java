package com.hepdd.backpackenhance.client.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import net.minecraft.client.gui.GuiTextField;

import org.junit.Test;

public class OverlaySearchFieldTest {

    @Test
    public void themedBackgroundPreservesVanillaTextAndClickInsets() throws ReflectiveOperationException {
        OverlaySearchField field = new OverlaySearchField(null, 20, 30, 162, 14);
        assertFalse(field.getEnableBackgroundDrawing());
        assertEquals(154, field.getWidth());
        Field vanillaFlag = GuiTextField.class.getDeclaredField("enableBackgroundDrawing");
        vanillaFlag.setAccessible(true);
        assertTrue(vanillaFlag.getBoolean(field));
    }

    @Test
    public void inputAdaptersKeepTheSameFieldTypeAndBounds() throws ReflectiveOperationException {
        assertEquals(
            GuiTextField.class,
            BackpackOverlayPanel.class.getDeclaredField("searchField")
                .getType());
        GuiTextField field = new OverlaySearchField(null, 20, 30, 162, 14);
        assertEquals(20, field.xPosition);
        assertEquals(30, field.yPosition);
        assertEquals(162, field.width);
        assertEquals(14, field.height);
        field.setFocused(true);
        assertTrue(field.isFocused());
    }
}
