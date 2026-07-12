package com.hepdd.backpackenhance.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hepdd.backpackenhance.client.overlay.OverlayController;
import com.hepdd.backpackenhance.client.overlay.OverlayGlState;

/**
 * ModularUI2: inject at the end of {@code ModularScreen.drawScreen()} so the overlay sits above
 * panels but under tooltips and the held stack. Skip {@code overlay == true} (OverlayStack).
 */
@Pseudo
@Mixin(targets = "com.cleanroommc.modularui.screen.ModularScreen", remap = false)
public abstract class MModularUI2Draw {

    @Shadow(remap = false)
    private boolean overlay;

    @Inject(method = "drawScreen()V", at = @At("RETURN"), require = 0)
    private void backpackenhance$drawOverlayAfterPanels(CallbackInfo ci) {
        if (this.overlay) {
            return;
        }

        GuiScreen current = Minecraft.getMinecraft().currentScreen;
        if (!(current instanceof GuiContainer)) {
            return;
        }
        GuiContainer container = (GuiContainer) current;

        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int mouseX = Mouse.getX() * scaled.getScaledWidth() / mc.displayWidth;
        int mouseY = scaled.getScaledHeight() - Mouse.getY() * scaled.getScaledHeight() / mc.displayHeight - 1;

        OverlayGlState.beginOverlayPass();
        boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        if (stencil) {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0F, 0.0F, 300.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        OverlayController.renderOverlayInContainer(container, mouseX, mouseY);
        GL11.glPopMatrix();
        if (stencil) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
        }
        OverlayGlState.endOverlayPass();
    }
}
