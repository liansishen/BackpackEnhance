package com.hepdd.backpackenhance.mixin;

import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hepdd.backpackenhance.client.overlay.OverlayController;

@Mixin(GuiContainerCreative.class)
public abstract class MGuiContainerCreative {

    @Inject(method = "setCurrentCreativeTab", at = @At("RETURN"))
    private void backpackenhance$creativeTabChanged(CreativeTabs tab, CallbackInfo ci) {
        OverlayController.onCreativeTabChanged((GuiContainerCreative) (Object) this);
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$keyTyped(char typedChar, int keyCode, CallbackInfo ci) {
        GuiContainerCreative gui = (GuiContainerCreative) (Object) this;
        if (OverlayController.handleSearchKey(gui, typedChar, keyCode)) {
            ci.cancel();
            return;
        }
        if (OverlayController.isActiveFor(gui) && !OverlayController.isTextInputFocused(gui)
            && OverlayController.handleOverlayToggleKey(keyCode)) {
            ci.cancel();
        }
    }
}
