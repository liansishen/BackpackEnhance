package com.hepdd.backpackenhance.integration;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;

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
            || "de.eydamos.backpack.gui.GuiWorkbenchBackpack".equals(className)
            || "forestry.storage.gui.GuiBackpack".equals(className)
            || "forestry.storage.gui.GuiBackpackT2".equals(className)
            || isForestryNaturalistBackpackGui(gui, className);
    }

    private static boolean isForestryNaturalistBackpackGui(GuiScreen gui, String className) {
        if (!"forestry.core.gui.GuiNaturalistInventory".equals(className) || !(gui instanceof GuiContainer)) {
            return false;
        }
        return "forestry.storage.gui.ContainerNaturalistBackpack".equals(
            ((GuiContainer) gui).inventorySlots.getClass()
                .getName());
    }
}
