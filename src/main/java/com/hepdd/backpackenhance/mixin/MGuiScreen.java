package com.hepdd.backpackenhance.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hepdd.backpackenhance.client.overlay.OverlayController;

@Mixin(GuiScreen.class)
public abstract class MGuiScreen {

    @Inject(method = "handleKeyboardInput", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$handleKeyboardInput(CallbackInfo ci) {
        if (Keyboard.getEventKeyState() && OverlayController
            .handleSearchKey((GuiScreen) (Object) this, Keyboard.getEventCharacter(), Keyboard.getEventKey()))
            ci.cancel();
    }

    @Inject(method = "handleMouseInput", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$handleMouseInput(CallbackInfo ci) {
        GuiScreen gui = (GuiScreen) (Object) this;
        if (Mouse.getEventButton() >= 0 && Mouse.getEventButtonState()) {
            Minecraft mc = Minecraft.getMinecraft();
            int mouseX = Mouse.getEventX() * gui.width / mc.displayWidth;
            int mouseY = gui.height - Mouse.getEventY() * gui.height / mc.displayHeight - 1;
            if (OverlayController.handleSearchMouseInput(gui, mouseX, mouseY, Mouse.getEventButton())) {
                ci.cancel();
                return;
            }
        }
        if (OverlayController.handleMouseWheelInput(gui, Mouse.getEventDWheel(), Mouse.getEventNanoseconds())) {
            ci.cancel();
        }
    }
}
