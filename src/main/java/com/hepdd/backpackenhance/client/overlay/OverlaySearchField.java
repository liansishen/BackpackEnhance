package com.hepdd.backpackenhance.client.overlay;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

import com.hepdd.backpackenhance.client.overlay.OverlayTheme.Color;

final class OverlaySearchField extends GuiTextField {

    OverlaySearchField(FontRenderer font, int x, int y, int width, int height) {
        super(font, x, y, width, height);
    }

    @Override
    public void drawTextBox() {
        if (!getVisible()) return;
        OverlayGuiTextures.drawSearchBackground(xPosition - 1, yPosition - 1, width + 2, height + 2, isFocused());
        setTextColor(OverlayTheme.color(Color.SEARCH_TEXT));
        setDisabledTextColour(OverlayTheme.color(Color.SEARCH_DISABLED));
        super.drawTextBox();
    }

    @Override
    public boolean getEnableBackgroundDrawing() {
        // Vanilla text placement and mouse clicks still use its enabled private background flag.
        return false;
    }

    @Override
    public int getWidth() {
        return width - 8;
    }
}
