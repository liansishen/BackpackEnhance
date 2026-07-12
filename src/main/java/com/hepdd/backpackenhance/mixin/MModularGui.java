package com.hepdd.backpackenhance.mixin;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hepdd.backpackenhance.client.overlay.OverlayController;
import com.hepdd.backpackenhance.client.overlay.OverlayGlState;

/**
 * ModularGui overrides {@code drawScreen} and draws tooltips + cursor inside the foreground
 * layer. Inject at HEAD of the foreground pass so the overlay sits above machine panels but under
 * ModularUI tooltips / cursor / held item.
 * <p>
 * Runtime ModularUI jars reobfuscate vanilla overrides to SRG ({@code func_146979_b}); target both
 * names with {@code require = 0}.
 */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.modularui.common.internal.wrapper.ModularGui", remap = false)
public abstract class MModularGui {

    @Inject(
        method = { "drawGuiContainerForegroundLayer(II)V", "func_146979_b(II)V" },
        at = @At("HEAD"),
        require = 0,
        remap = false)
    private void backpackenhance$drawOverlay(int mouseX, int mouseY, CallbackInfo ci) {
        OverlayGlState.beginOverlayPass();
        GL11.glPushMatrix();
        // ModularGui foreground runs in screen space (no guiLeft/guiTop translate).
        GL11.glTranslatef(0.0F, 0.0F, 300.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        OverlayController.renderOverlayInContainer((GuiContainer) (Object) this, mouseX, mouseY);
        GL11.glPopMatrix();
        OverlayGlState.endOverlayPass();
    }
}
