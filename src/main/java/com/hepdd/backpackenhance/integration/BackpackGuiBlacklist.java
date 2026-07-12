package com.hepdd.backpackenhance.integration;

import net.minecraft.client.gui.GuiScreen;

public class BackpackGuiBlacklist {

    public boolean isBlacklisted(GuiScreen gui) {
        if (gui == null) {
            return false;
        }

        String className = gui.getClass()
            .getName();
        return "com.darkona.adventurebackpack.client.gui.GuiAdvBackpack".equals(className)
            || "com.darkona.adventurebackpack.gui.GuiAdvBackpack".equals(className)
            || "de.eydamos.backpack.gui.GuiBackpack".equals(className)
            || "de.eydamos.backpack.gui.GuiWorkbenchBackpack".equals(className);
    }
}
