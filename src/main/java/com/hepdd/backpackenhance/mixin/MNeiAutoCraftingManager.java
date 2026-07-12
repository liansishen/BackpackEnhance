package com.hepdd.backpackenhance.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.backpackenhance.integration.nei.OverlayNeiSupport;

import codechicken.nei.ItemStackAmount;

/**
 * Bookmark auto-craft inventory sampling only sees container slots by default.
 * Append the active BackpackEnhance overlay tab so chain math can use those items.
 */
@Pseudo
@Mixin(targets = "codechicken.nei.recipe.AutoCraftingManager", remap = false)
public abstract class MNeiAutoCraftingManager {

    @Inject(method = "getInventoryItems", at = @At("RETURN"), require = 0)
    private static void backpackenhance$addOverlayTab(GuiContainer guiContainer,
        CallbackInfoReturnable<ItemStackAmount> cir) {
        ItemStackAmount inventory = cir.getReturnValue();
        if (inventory != null) {
            OverlayNeiSupport.addActiveTabToItemStackAmount(inventory);
        }
    }
}
