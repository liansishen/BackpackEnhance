package com.hepdd.backpackenhance.client.overlay;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Isolates OpenGL / RenderHelper state while drawing the backpack overlay so subsequent
 * container slots, NEI item panels, and 3D item models are not left with broken lighting
 * (classic "dark faces" on blocks / models).
 */
public final class OverlayGlState {

    private static final int OVERLAY_ATTRIB_BITS = GL11.GL_ENABLE_BIT | GL11.GL_LIGHTING_BIT
        | GL11.GL_DEPTH_BUFFER_BIT
        | GL11.GL_COLOR_BUFFER_BIT
        | GL11.GL_CURRENT_BIT
        | GL11.GL_TEXTURE_BIT;

    private OverlayGlState() {}

    /**
     * Call immediately before drawing the overlay (and before any matrix translate for it).
     * Pairs with {@link #endOverlayPass()}.
     */
    public static void beginOverlayPass() {
        GL11.glPushAttrib(OVERLAY_ATTRIB_BITS);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * Restore captured state, then re-apply standard GUI item lighting so later NEI / slot /
     * held-item draws see a known-good light setup.
     */
    public static void endOverlayPass() {
        GL11.glPopAttrib();
        restoreGuiItemLighting();
    }

    /**
     * Safe setup for rendering a single ItemStack icon inside the overlay.
     * Pairs with {@link #endItemIcon()}.
     */
    public static void beginItemIcon() {
        GL11.glPushAttrib(OVERLAY_ATTRIB_BITS);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.enableGUIStandardItemLighting();
    }

    public static void endItemIcon() {
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glPopAttrib();
    }

    /**
     * Re-establish the lighting environment vanilla GUI / NEI item rendering expects.
     * Safe to call even if lighting was already enabled.
     */
    public static void restoreGuiItemLighting() {
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        // Fullbright lightmap (same idea as GUI item rendering).
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
    }
}
