package com.hepdd.backpackenhance.client.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

/**
 * Texture atlas helpers for the backpack overlay chrome.
 * Resource pack path: {@code assets/backpackenhance/textures/gui/overlay.png}
 * (also shipped under {@code resourcepacks/BackpackEnhance-Default/} as a reference pack).
 *
 * <p>
 * Atlas UV map (pixels, 256×256):
 * <ul>
 * <li>(0,0) 32×32 panel nine-slice (top/left/right 2, bottom 4 fixed)</li>
 * <li>(32,0) 16×16 title bar tile</li>
 * <li>(48,0)/(49,0)/(50,0) 1×1 body / title / divider fills</li>
 * <li>(0,32) 18×18 slot · (18,32) 16×16 slot hover</li>
 * <li>(0,56)/(24,56) 24×18 tab idle / active</li>
 * <li>(0,80)/(12,80)/(24,80) 12×12 button / hover / minimize glyph</li>
 * <li>(0,96)/(10,96) 10×18 arrow button / disabled</li>
 * <li>(0,128)/(1,128) tooltip bg / border pixels</li>
 * </ul>
 */
public final class OverlayGuiTextures {

    public static final ResourceLocation TEXTURE = new ResourceLocation("backpackenhance", "textures/gui/overlay.png");

    public static final int TEX_W = 256;
    public static final int TEX_H = 256;

    // Panel nine-slice (asymmetric: bottom edge is 4px and never stretched vertically)
    public static final int PANEL_U = 0;
    public static final int PANEL_V = 0;
    public static final int PANEL_SIZE = 32;
    public static final int PANEL_BORDER_TOP = 2;
    public static final int PANEL_BORDER_LEFT = 2;
    public static final int PANEL_BORDER_RIGHT = 2;
    public static final int PANEL_BORDER_BOTTOM = 4;

    // Title / fills
    public static final int TITLE_U = 32;
    public static final int TITLE_V = 0;
    public static final int TITLE_TILE = 16;
    public static final int FILL_BODY_U = 48;
    public static final int FILL_TITLE_U = 49;
    public static final int FILL_DIV_U = 50;
    public static final int FILL_V = 0;

    // Slot
    public static final int SLOT_U = 0;
    public static final int SLOT_V = 32;
    public static final int SLOT_SIZE = 18;
    public static final int HOVER_U = 18;
    public static final int HOVER_V = 32;
    public static final int HOVER_SIZE = 16;

    // Tabs (24 wide matches TAB_WIDTH - 2)
    public static final int TAB_W = 24;
    public static final int TAB_H = 18;
    public static final int TAB_IDLE_U = 0;
    public static final int TAB_ACTIVE_U = 24;
    public static final int TAB_V = 56;

    // Buttons
    public static final int BTN_SIZE = 12;
    public static final int BTN_U = 0;
    public static final int BTN_HOVER_U = 12;
    public static final int MINIMIZE_U = 24;
    public static final int BTN_V = 80;

    // Arrows
    public static final int ARROW_W = 10;
    public static final int ARROW_H = 18;
    public static final int ARROW_U = 0;
    public static final int ARROW_DIS_U = 10;
    public static final int ARROW_V = 96;

    // Tooltip 1×1
    public static final int TIP_BG_U = 0;
    public static final int TIP_BORDER_U = 1;
    public static final int TIP_V = 128;

    private static float zLevel;

    private OverlayGuiTextures() {}

    public static void setZLevel(float z) {
        zLevel = z;
    }

    public static void bind() {
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
    }

    /** Draw a rectangular UV region 1:1 at screen position. */
    public static void draw(int x, int y, int u, int v, int w, int h) {
        float fu = 1.0F / TEX_W;
        float fv = 1.0F / TEX_H;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, zLevel, u * fu, (v + h) * fv);
        t.addVertexWithUV(x + w, y + h, zLevel, (u + w) * fu, (v + h) * fv);
        t.addVertexWithUV(x + w, y, zLevel, (u + w) * fu, v * fv);
        t.addVertexWithUV(x, y, zLevel, u * fu, v * fv);
        t.draw();
    }

    /** Stretch a UV region to an arbitrary screen size (for 1×1 fills / tiles). */
    public static void drawStretched(int x, int y, int w, int h, int u, int v, int uw, int vh) {
        float fu = 1.0F / TEX_W;
        float fv = 1.0F / TEX_H;
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertexWithUV(x, y + h, zLevel, u * fu, (v + vh) * fv);
        t.addVertexWithUV(x + w, y + h, zLevel, (u + uw) * fu, (v + vh) * fv);
        t.addVertexWithUV(x + w, y, zLevel, (u + uw) * fu, v * fv);
        t.addVertexWithUV(x, y, zLevel, u * fu, v * fv);
        t.draw();
    }

    /**
     * Classic nine-slice with uniform border on all sides.
     *
     * @param border corner/edge thickness in both texture and screen pixels
     */
    public static void drawNineSlice(int x, int y, int w, int h, int u, int v, int tw, int th, int border) {
        drawNineSlice(x, y, w, h, u, v, tw, th, border, border, border, border);
    }

    /**
     * Nine-slice with independent border thicknesses (screen px = texture px per side).
     * Corners are never stretched; edges stretch only along their length; center stretches both axes.
     */
    public static void drawNineSlice(int x, int y, int w, int h, int u, int v, int tw, int th, int borderTop,
        int borderLeft, int borderBottom, int borderRight) {
        int bt = borderTop;
        int bl = borderLeft;
        int bb = borderBottom;
        int br = borderRight;
        if (w < bl + br || h < bt + bb) {
            drawStretched(x, y, w, h, u, v, tw, th);
            return;
        }
        int midW = w - bl - br;
        int midH = h - bt - bb;
        int texMidW = tw - bl - br;
        int texMidH = th - bt - bb;

        // corners (1:1)
        draw(x, y, u, v, bl, bt);
        draw(x + w - br, y, u + tw - br, v, br, bt);
        draw(x, y + h - bb, u, v + th - bb, bl, bb);
        draw(x + w - br, y + h - bb, u + tw - br, v + th - bb, br, bb);

        // edges (stretch along length only — bottom strip height stays bb / no vertical stretch)
        if (midW > 0) {
            if (bt > 0) {
                drawStretched(x + bl, y, midW, bt, u + bl, v, texMidW, bt);
            }
            if (bb > 0) {
                drawStretched(x + bl, y + h - bb, midW, bb, u + bl, v + th - bb, texMidW, bb);
            }
        }
        if (midH > 0) {
            if (bl > 0) {
                drawStretched(x, y + bt, bl, midH, u, v + bt, bl, texMidH);
            }
            if (br > 0) {
                drawStretched(x + w - br, y + bt, br, midH, u + tw - br, v + bt, br, texMidH);
            }
        }
        // center
        if (midW > 0 && midH > 0) {
            drawStretched(x + bl, y + bt, midW, midH, u + bl, v + bt, texMidW, texMidH);
        }
    }

    public static void drawPanel(int x, int y, int w, int h) {
        drawNineSlice(
            x,
            y,
            w,
            h,
            PANEL_U,
            PANEL_V,
            PANEL_SIZE,
            PANEL_SIZE,
            PANEL_BORDER_TOP,
            PANEL_BORDER_LEFT,
            PANEL_BORDER_BOTTOM,
            PANEL_BORDER_RIGHT);
    }

    /**
     * Redraw only the nine-slice border (corners + edges), leaving the interior alone.
     * Used after slots/items so chrome edges stay on top. Bottom edge is 4px unstretched.
     */
    public static void drawPanelFrame(int x, int y, int w, int h) {
        int u = PANEL_U;
        int v = PANEL_V;
        int tw = PANEL_SIZE;
        int th = PANEL_SIZE;
        int bt = PANEL_BORDER_TOP;
        int bl = PANEL_BORDER_LEFT;
        int bb = PANEL_BORDER_BOTTOM;
        int br = PANEL_BORDER_RIGHT;
        if (w < bl + br || h < bt + bb) {
            return;
        }
        int midW = w - bl - br;
        int midH = h - bt - bb;
        int texMidW = tw - bl - br;
        int texMidH = th - bt - bb;
        draw(x, y, u, v, bl, bt);
        draw(x + w - br, y, u + tw - br, v, br, bt);
        draw(x, y + h - bb, u, v + th - bb, bl, bb);
        draw(x + w - br, y + h - bb, u + tw - br, v + th - bb, br, bb);
        if (midW > 0) {
            if (bt > 0) {
                drawStretched(x + bl, y, midW, bt, u + bl, v, texMidW, bt);
            }
            if (bb > 0) {
                drawStretched(x + bl, y + h - bb, midW, bb, u + bl, v + th - bb, texMidW, bb);
            }
        }
        if (midH > 0) {
            if (bl > 0) {
                drawStretched(x, y + bt, bl, midH, u, v + bt, bl, texMidH);
            }
            if (br > 0) {
                drawStretched(x + w - br, y + bt, br, midH, u + tw - br, v + bt, br, texMidH);
            }
        }
    }

    /** Inset title strip (caller insets by 2 so outer frame stays visible). */
    public static void drawTitleBar(int x, int y, int w, int h) {
        drawStretched(x, y, w, h, TITLE_U, TITLE_V, TITLE_TILE, TITLE_TILE);
    }

    public static void drawBodyFill(int x, int y, int w, int h) {
        drawStretched(x, y, w, h, FILL_BODY_U, FILL_V, 1, 1);
    }

    public static void drawSlot(int x, int y) {
        draw(x, y, SLOT_U, SLOT_V, SLOT_SIZE, SLOT_SIZE);
    }

    public static void drawSlotHover(int x, int y) {
        GL11.glColorMask(true, true, true, false);
        draw(x, y, HOVER_U, HOVER_V, HOVER_SIZE, HOVER_SIZE);
        GL11.glColorMask(true, true, true, true);
    }

    public static void drawTab(int x, int y, boolean active) {
        int u = active ? TAB_ACTIVE_U : TAB_IDLE_U;
        draw(x, y, u, TAB_V, TAB_W, TAB_H);
    }

    public static void drawTitleButton(int x, int y, boolean hovered) {
        int u = hovered ? BTN_HOVER_U : BTN_U;
        draw(x, y, u, BTN_V, BTN_SIZE, BTN_SIZE);
        // minimize glyph overlaid
        draw(x, y, MINIMIZE_U, BTN_V, BTN_SIZE, BTN_SIZE);
    }

    public static void drawArrowButton(int x, int y, boolean enabled, boolean hovered) {
        if (!enabled) {
            draw(x, y, ARROW_DIS_U, ARROW_V, ARROW_W, ARROW_H);
            return;
        }
        // Hover reuses button-hover palette via slight brighten when enabled+hovered;
        // atlas has one enabled sprite — lighten with color tint.
        if (hovered) {
            GL11.glColor4f(1.15F, 1.15F, 1.15F, 1.0F);
        }
        draw(x, y, ARROW_U, ARROW_V, ARROW_W, ARROW_H);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawTooltipBackground(int x, int y, int w, int h) {
        drawStretched(x, y, w, h, TIP_BG_U, TIP_V, 1, 1);
        // 1px border
        drawStretched(x, y, w, 1, TIP_BORDER_U, TIP_V, 1, 1);
        drawStretched(x, y + h - 1, w, 1, TIP_BORDER_U, TIP_V, 1, 1);
        drawStretched(x, y, 1, h, TIP_BORDER_U, TIP_V, 1, 1);
        drawStretched(x + w - 1, y, 1, h, TIP_BORDER_U, TIP_V, 1, 1);
    }
}
