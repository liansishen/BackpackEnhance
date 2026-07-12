package com.hepdd.backpackenhance.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.backpackenhance.integration.nei.OverlayNeiSupport;

import codechicken.nei.recipe.GuiOverlayButton;

/**
 * Fallback presence path when no {@code IOverlayHandler} is registered.
 * Primary path is {@link MNeiIOverlayHandler}.
 */
@Pseudo
@Mixin(targets = "codechicken.nei.recipe.GuiOverlayButton", remap = false)
public abstract class MNeiGuiOverlayButton {

    @Inject(method = "presenceOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void backpackenhance$presenceWithOverlay(List ingredients, CallbackInfoReturnable<List> cir) {
        if (!OverlayNeiSupport.hasOverlayItems() || ingredients == null) {
            return;
        }
        GuiOverlayButton self = (GuiOverlayButton) (Object) this;
        if (self.firstGui == null) {
            return;
        }
        cir.setReturnValue(OverlayNeiSupport.buildPresenceOverlay(self.firstGui, ingredients));
    }
}
