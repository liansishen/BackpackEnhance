package com.hepdd.backpackenhance.mixin;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.backpackenhance.integration.nei.OverlayNeiSupport;

/**
 * Safety net: {@code GuiOverlayButton} uses {@code RecipeHandlerRef.getPresenceOverlay} first.
 * If the interface mixin on {@code IOverlayHandler} does not apply, this still injects overlay
 * stacks into presence / missing-materials checks.
 */
@Pseudo
@Mixin(targets = "codechicken.nei.recipe.RecipeHandlerRef", remap = false)
public abstract class MNeiRecipeHandlerRef {

    @Inject(method = "getPresenceOverlay", at = @At("RETURN"), cancellable = true, require = 0)
    private void backpackenhance$presenceWithOverlay(GuiContainer gui, CallbackInfoReturnable<List> cir) {
        if (gui == null || !OverlayNeiSupport.hasOverlayItems()) {
            return;
        }
        List existing = cir.getReturnValue();
        List enriched = OverlayNeiSupport.enrichPresenceOverlay(gui, existing);
        if (enriched != null) {
            cir.setReturnValue(enriched);
        }
    }
}
