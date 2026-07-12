package com.hepdd.backpackenhance.mixin;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.backpackenhance.integration.nei.OverlayNeiSupport;

import codechicken.nei.recipe.IRecipeHandler;

/**
 * NEI recipe presence (green/red slots + Shift missing tooltip) uses
 * {@code IOverlayHandler.presenceOverlay}, not {@code GuiOverlayButton.presenceOverlay}.
 * Inject here so the active overlay tab is counted.
 */
@Pseudo
@Mixin(targets = "codechicken.nei.api.IOverlayHandler", remap = false)
public interface MNeiIOverlayHandler {

    @Inject(method = "presenceOverlay", at = @At("RETURN"), cancellable = true, require = 0)
    default void backpackenhance$presenceWithOverlay(GuiContainer firstGui, IRecipeHandler recipe, int recipeIndex,
        CallbackInfoReturnable<List> cir) {
        if (firstGui == null || recipe == null || !OverlayNeiSupport.hasOverlayItems()) {
            return;
        }
        List existing = cir.getReturnValue();
        List enriched = OverlayNeiSupport.enrichPresenceOverlay(firstGui, existing);
        if (enriched != null) {
            cir.setReturnValue(enriched);
        } else {
            // No prior states (or empty handler list) — build from ingredients.
            List ingredients = recipe.getIngredientStacks(recipeIndex);
            cir.setReturnValue(OverlayNeiSupport.buildPresenceOverlay(firstGui, ingredients));
        }
    }
}
