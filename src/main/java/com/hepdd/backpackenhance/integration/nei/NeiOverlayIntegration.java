package com.hepdd.backpackenhance.integration.nei;

import java.util.Collections;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.lwjgl.input.Keyboard;

import com.hepdd.backpackenhance.client.overlay.BackpackOverlayPanel;
import com.hepdd.backpackenhance.client.overlay.OverlayController;

import codechicken.nei.VisiblityData;
import codechicken.nei.api.API;
import codechicken.nei.api.INEIGuiHandler;
import codechicken.nei.api.TaggedInventoryArea;
import codechicken.nei.guihook.GuiContainerManager;
import codechicken.nei.guihook.IContainerObjectHandler;
import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;

/**
 * NEI GUI hooks: hide item panel under overlay, recipe keys (R/U) on overlay slots,
 * stack-under-mouse for tooltips.
 */
public final class NeiOverlayIntegration implements INEIGuiHandler {

    private static boolean registered;

    private NeiOverlayIntegration() {}

    public static void init() {
        if (!registered) {
            API.registerNEIGuiHandler(new NeiOverlayIntegration());
            GuiContainerManager.addObjectHandler(new OverlayObjectHandler());
            registered = true;
        }
    }

    @Override
    public VisiblityData modifyVisiblity(GuiContainer gui, VisiblityData currentVisibility) {
        return currentVisibility;
    }

    @Override
    public Iterable<Integer> getItemSpawnSlots(GuiContainer gui, ItemStack item) {
        return Collections.emptyList();
    }

    @Override
    public List<TaggedInventoryArea> getInventoryAreas(GuiContainer gui) {
        return Collections.emptyList();
    }

    @Override
    public boolean handleDragNDrop(GuiContainer gui, int mouseX, int mouseY, ItemStack draggedStack, int button) {
        return false;
    }

    @Override
    public boolean hideItemPanelSlot(GuiContainer gui, int x, int y, int width, int height) {
        BackpackOverlayPanel panel = OverlayController.getPanel();
        if (!OverlayController.isActiveFor(gui) || panel == null) {
            return false;
        }
        return intersects(x, y, width, height, panel.getX(), panel.getY(), panel.getWidth(), panel.getHeight());
    }

    public static boolean handleRecipeKey(GuiContainer gui, char typedChar, int keyCode) {
        BackpackOverlayPanel panel = OverlayController.getPanel();
        if (!OverlayController.isActiveFor(gui) || panel == null) {
            return false;
        }

        int mouseX = org.lwjgl.input.Mouse.getX() * gui.width / Minecraft.getMinecraft().displayWidth;
        int mouseY = gui.height - org.lwjgl.input.Mouse.getY() * gui.height / Minecraft.getMinecraft().displayHeight
            - 1;
        ItemStack stack = panel.getStackAt(mouseX, mouseY);
        if (stack == null) {
            return false;
        }

        if (keyCode == Keyboard.KEY_R) {
            return GuiCraftingRecipe.openRecipeGui("item", stack.copy());
        }
        if (keyCode == Keyboard.KEY_U) {
            return GuiUsageRecipe.openRecipeGui("item", stack.copy());
        }
        return false;
    }

    private static boolean intersects(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private static final class OverlayObjectHandler implements IContainerObjectHandler {

        @Override
        public void guiTick(GuiContainer gui) {}

        @Override
        public void refresh(GuiContainer gui) {}

        @Override
        public void load(GuiContainer gui) {}

        @Override
        public ItemStack getStackUnderMouse(GuiContainer gui, int mouseX, int mouseY) {
            BackpackOverlayPanel panel = OverlayController.getPanel();
            if (!OverlayController.isActiveFor(gui) || panel == null) {
                return null;
            }
            ItemStack stack = panel.getStackAt(mouseX, mouseY);
            return stack == null ? null : stack.copy();
        }

        @Override
        public boolean objectUnderMouse(GuiContainer gui, int mouseX, int mouseY) {
            BackpackOverlayPanel panel = OverlayController.getPanel();
            return OverlayController.isActiveFor(gui) && panel != null && panel.getSlotClickAt(mouseX, mouseY) != null;
        }

        @Override
        public boolean shouldShowTooltip(GuiContainer gui) {
            return true;
        }
    }
}
