package com.hepdd.backpackenhance.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hepdd.backpackenhance.client.overlay.OverlayController;
import com.hepdd.backpackenhance.client.overlay.OverlayGlState;
import com.hepdd.backpackenhance.client.overlay.OverlayGuiContainerAccess;
import com.hepdd.backpackenhance.integration.nei.NeiOverlayIntegration;

@Mixin(GuiContainer.class)
public abstract class MGuiContainer implements OverlayGuiContainerAccess {

    @Shadow
    protected int guiLeft;

    @Shadow
    protected int guiTop;

    @Shadow
    protected int xSize;

    @Shadow
    protected int ySize;

    @Override
    public int backpackenhance$getGuiLeft() {
        return this.guiLeft;
    }

    @Override
    public int backpackenhance$getGuiTop() {
        return this.guiTop;
    }

    @Override
    public int backpackenhance$getXSize() {
        return this.xSize;
    }

    @Override
    public int backpackenhance$getYSize() {
        return this.ySize;
    }

    @Inject(
        method = "drawScreen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerForegroundLayer(II)V",
            shift = At.Shift.AFTER))
    private void backpackenhance$drawOverlay(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        OverlayGlState.beginOverlayPass();
        GL11.glPushMatrix();
        // Foreground is drawn in container-local space; overlay uses screen space.
        GL11.glTranslatef(-this.guiLeft, -this.guiTop, 300.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        OverlayController.renderOverlayInContainer((GuiContainer) (Object) this, mouseX, mouseY);
        GL11.glPopMatrix();
        OverlayGlState.endOverlayPass();
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$mouseClicked(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (OverlayController.shouldCancelMouseEvent(mouseX, mouseY)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClickMove", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$mouseClickMove(int mouseX, int mouseY, int button, long timeSinceLastClick,
        CallbackInfo ci) {
        if (OverlayController.shouldCancelMouseEvent(mouseX, mouseY)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseMovedOrUp", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$mouseMovedOrUp(int mouseX, int mouseY, int button, CallbackInfo ci) {
        if (OverlayController.shouldCancelMouseEvent(mouseX, mouseY)) {
            ci.cancel();
        }
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$keyTyped(char typedChar, int keyCode, CallbackInfo ci) {
        if (OverlayController.handleOverlayToggleKey(keyCode)) {
            ci.cancel();
            return;
        }
        if (NeiOverlayIntegration.handleRecipeKey((GuiContainer) (Object) this, typedChar, keyCode)) {
            ci.cancel();
        }
    }
}
