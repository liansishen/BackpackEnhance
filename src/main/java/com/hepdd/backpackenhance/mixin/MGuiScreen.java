package com.hepdd.backpackenhance.mixin;

import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hepdd.backpackenhance.client.overlay.OverlayController;

@Mixin(GuiScreen.class)
public abstract class MGuiScreen {

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$handleMouseInput(CallbackInfo ci) {
        if (OverlayController.handleMouseWheelInput(
            (GuiScreen) (Object) this,
            Mouse.getEventDWheel(),
            Mouse.getEventNanoseconds())) {
            ci.cancel();
        }
    }
}
