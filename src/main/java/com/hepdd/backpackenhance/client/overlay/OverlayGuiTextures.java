package com.hepdd.backpackenhance.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/** Component textures use logical GUI pixels independently of PNG resolution. */
public final class OverlayGuiTextures {

    enum Sprite {

        PANEL("panel", 32, 32),
        TITLE("title", 16, 16),
        TAB_STRIP("tab_strip", 16, 16),
        SLOT("slot", 18, 18),
        SLOT_HOVER("slot_hover", 16, 16),
        TAB_NORMAL("tab_normal", 22, 18),
        TAB_SELECTED("tab_selected", 22, 18),
        TAB_ACCENT("tab_accent", 22, 18),
        BUTTON_NORMAL("button_normal", 12, 12),
        BUTTON_HOVER("button_hover", 12, 12),
        BUTTON_DISABLED("button_disabled", 12, 12),
        MINIMIZE("icons/minimize", 12, 12),
        ARROW_NORMAL("arrow_normal", 10, 18),
        ARROW_HOVER("arrow_hover", 10, 18),
        ARROW_DISABLED("arrow_disabled", 10, 18),
        SCROLLBAR_TRACK("scrollbar_track", 12, 15),
        SCROLLBAR_THUMB("scrollbar_thumb", 12, 15),
        SCROLLBAR_THUMB_HOVER("scrollbar_thumb_hover", 12, 15),
        SCROLLBAR_THUMB_DISABLED("scrollbar_thumb_disabled", 12, 15),
        SEARCH("search", 16, 16),
        SEARCH_FOCUSED("search_focused", 16, 16),
        TOOLTIP("tooltip", 8, 8),
        EXPAND("icons/expand", 20, 20),
        ARROW_LEFT("icons/arrow_left", 10, 18),
        ARROW_RIGHT("icons/arrow_right", 10, 18),
        MODE_NORMAL("icons/mode_normal", 12, 12),
        MODE_LOCKED("icons/mode_locked", 12, 12),
        MODE_RECEIVE("icons/mode_receive", 12, 12),
        MODE_RESUPPLY("icons/mode_resupply", 12, 12);

        final ResourceLocation resource;
        final int width;
        final int height;

        Sprite(String name, int width, int height) {
            this.resource = new ResourceLocation("backpackenhance", "textures/gui/overlay/" + name + ".png");
            this.width = width;
            this.height = height;
        }

        void bind() {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(resource);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_LIGHTING);
        }
    }

    private static float zLevel;

    private OverlayGuiTextures() {}

    public static void setZLevel(float z) {
        zLevel = z;
    }

    private static void region(Sprite sprite, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        float fu = 1.0F / sprite.width;
        float fv = 1.0F / sprite.height;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, zLevel, u * fu, (v + vh) * fv);
        t.addVertexWithUV(x + w, y + h, zLevel, (u + uw) * fu, (v + vh) * fv);
        t.addVertexWithUV(x + w, y, zLevel, (u + uw) * fu, v * fv);
        t.addVertexWithUV(x, y, zLevel, u * fu, v * fv);
        t.draw();
    }

    private static void draw(Sprite sprite, int x, int y, int w, int h) {
        sprite.bind();
        region(sprite, x, y, w, h, 0, 0, sprite.width, sprite.height);
    }

    private static void nineSlice(Sprite sprite, int x, int y, int w, int h, int top, int left, int bottom, int right,
        boolean center) {
        sprite.bind();
        if (w < left + right || h < top + bottom) {
            if (center) region(sprite, x, y, w, h, 0, 0, sprite.width, sprite.height);
            return;
        }
        int midW = w - left - right;
        int midH = h - top - bottom;
        int texMidW = sprite.width - left - right;
        int texMidH = sprite.height - top - bottom;
        region(sprite, x, y, left, top, 0, 0, left, top);
        region(sprite, x + w - right, y, right, top, sprite.width - right, 0, right, top);
        region(sprite, x, y + h - bottom, left, bottom, 0, sprite.height - bottom, left, bottom);
        region(
            sprite,
            x + w - right,
            y + h - bottom,
            right,
            bottom,
            sprite.width - right,
            sprite.height - bottom,
            right,
            bottom);
        if (midW > 0) {
            region(sprite, x + left, y, midW, top, left, 0, texMidW, top);
            region(sprite, x + left, y + h - bottom, midW, bottom, left, sprite.height - bottom, texMidW, bottom);
        }
        if (midH > 0) {
            region(sprite, x, y + top, left, midH, 0, top, left, texMidH);
            region(sprite, x + w - right, y + top, right, midH, sprite.width - right, top, right, texMidH);
        }
        if (center && midW > 0 && midH > 0) {
            region(sprite, x + left, y + top, midW, midH, left, top, texMidW, texMidH);
        }
    }

    public static void drawPanel(int x, int y, int w, int h) {
        nineSlice(Sprite.PANEL, x, y, w, h, 2, 2, 4, 2, true);
    }

    /** Redraw the original frame above inset content, keeping the bottom border four pixels high. */
    public static void drawPanelFrame(int x, int y, int w, int h) {
        nineSlice(Sprite.PANEL, x, y, w, h, 2, 2, 4, 2, false);
    }

    public static void drawTitleBar(int x, int y, int w, int h) {
        draw(Sprite.TITLE, x, y, w, h);
    }

    public static void drawBodyFill(int x, int y, int w, int h) {
        draw(Sprite.TAB_STRIP, x, y, w, h);
    }

    public static void drawSlot(int x, int y) {
        draw(Sprite.SLOT, x, y, 18, 18);
    }

    public static void drawSlotHover(int x, int y) {
        GL11.glColorMask(true, true, true, false);
        draw(Sprite.SLOT_HOVER, x, y, 16, 16);
        GL11.glColorMask(true, true, true, true);
    }

    public static void drawTab(int x, int y, boolean active) {
        draw(active ? Sprite.TAB_SELECTED : Sprite.TAB_NORMAL, x, y, 22, 18);
    }

    public static void drawTabAccent(int x, int y, int color) {
        tinted(Sprite.TAB_ACCENT, x, y, color);
    }

    public static void drawSmallButton(int x, int y, boolean hovered, boolean enabled) {
        draw(!enabled ? Sprite.BUTTON_DISABLED : hovered ? Sprite.BUTTON_HOVER : Sprite.BUTTON_NORMAL, x, y, 12, 12);
    }

    public static void drawTitleButton(int x, int y, boolean hovered) {
        drawSmallButton(x, y, hovered, true);
        draw(Sprite.MINIMIZE, x, y, 12, 12);
    }

    public static void drawArrowButton(int x, int y, boolean enabled, boolean hovered) {
        Sprite sprite = !enabled ? Sprite.ARROW_DISABLED : hovered ? Sprite.ARROW_HOVER : Sprite.ARROW_NORMAL;
        draw(sprite, x, y, 10, 18);
    }

    public static void drawScrollbar(int x, int y, int height, int thumbY, int thumbHeight, boolean hovered,
        boolean enabled) {
        nineSlice(Sprite.SCROLLBAR_TRACK, x, y, 12, height, 3, 4, 1, 4, true);
        Sprite thumb = !enabled ? Sprite.SCROLLBAR_THUMB_DISABLED
            : hovered ? Sprite.SCROLLBAR_THUMB_HOVER : Sprite.SCROLLBAR_THUMB;
        draw(thumb, x, thumbY, 12, thumbHeight);
    }

    public static void drawSearchBackground(int x, int y, int w, int h, boolean focused) {
        nineSlice(focused ? Sprite.SEARCH_FOCUSED : Sprite.SEARCH, x, y, w, h, 1, 1, 1, 1, true);
    }

    public static void drawTooltipBackground(int x, int y, int w, int h) {
        nineSlice(Sprite.TOOLTIP, x, y, w, h, 1, 1, 1, 1, true);
    }

    static void drawIcon(Sprite sprite, int x, int y) {
        draw(sprite, x, y, sprite.width, sprite.height);
    }

    private static void tinted(Sprite sprite, int x, int y, int color) {
        sprite.bind();
        GL11.glColor4f(
            (color >> 16 & 255) / 255.0F,
            (color >> 8 & 255) / 255.0F,
            (color & 255) / 255.0F,
            (color >>> 24) / 255.0F);
        region(sprite, x, y, sprite.width, sprite.height, 0, 0, sprite.width, sprite.height);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
