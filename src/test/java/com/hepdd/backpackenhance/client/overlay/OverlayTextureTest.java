package com.hepdd.backpackenhance.client.overlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.hepdd.backpackenhance.client.overlay.OverlayGuiTextures.Sprite;

public class OverlayTextureTest {

    private static final File DEFAULT = new File("src/main/resources/assets/backpackenhance");
    private static final File MODERNITY = new File("resourcepacks/Modernity-BackpackEnhance/assets/backpackenhance");

    @Test
    public void bothThemesProvideEveryComponentAtItsLogicalSize() throws IOException {
        for (File theme : new File[] { DEFAULT, MODERNITY }) {
            for (Sprite sprite : Sprite.values()) {
                BufferedImage image = read(theme, sprite);
                assertEquals(sprite.name(), sprite.width, image.getWidth());
                assertEquals(sprite.name(), sprite.height, image.getHeight());
                assertEquals(
                    "RGB color components",
                    3,
                    image.getColorModel()
                        .getNumColorComponents());
            }
        }
    }

    @Test
    public void modernityPackIconHasTheFrameAndCenteredBButton() throws IOException {
        BufferedImage icon = ImageIO.read(new File("resourcepacks/Modernity-BackpackEnhance/pack.png"));
        assertNotNull(icon);
        assertEquals(128, icon.getWidth());
        assertEquals(128, icon.getHeight());
        assertEquals(0xFF413F54, icon.getRGB(0, 0));
        assertEquals(0xFFF2F2F2, icon.getRGB(4, 4));
        assertEquals(0xFFCBCCD4, icon.getRGB(8, 8));
        assertEquals(0xFFF2F2F2, icon.getRGB(12, 12));
        assertEquals(0xFF413F54, icon.getRGB(28, 28));
        BufferedImage letter = read(MODERNITY, Sprite.EXPAND);
        BufferedImage button = read(MODERNITY, Sprite.BUTTON_NORMAL);
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 5; x++) {
                int pixel = letter.getRGB(7 + x, 6 + y);
                int expected = pixel == 0 ? button.getRGB(4 + x, 3 + y) : pixel;
                assertEquals(expected, icon.getRGB(52 + x * 6, 46 + y * 6));
            }
        }
    }

    @Test
    public void defaultComponentsPreserveEveryLegacyPixel() throws IOException {
        assertLegacyCrops(DEFAULT, "default");
    }

    @Test
    public void modernityComponentsPreserveEveryLegacyPixel() throws IOException {
        assertLegacyCrops(MODERNITY, "modernity");
    }

    @Test
    public void narrowerTabsPreserveLegacyEdgesAndAccentSize() throws IOException {
        for (File theme : new File[] { DEFAULT, MODERNITY }) {
            BufferedImage atlas = legacy(theme == DEFAULT ? "default" : "modernity");
            for (Sprite sprite : new Sprite[] { Sprite.TAB_NORMAL, Sprite.TAB_SELECTED }) {
                BufferedImage image = read(theme, sprite);
                assertEquals(22, image.getWidth());
                assertEquals(18, image.getHeight());
                int u = sprite == Sprite.TAB_NORMAL ? 0 : 24;
                for (int y = 0; y < 18; y++) {
                    for (int x = 0; x < 22; x++) {
                        assertEquals(atlas.getRGB(u + x + (x >= 11 ? 2 : 0), 56 + y), image.getRGB(x, y));
                    }
                }
            }
            assertEquals(22, read(theme, Sprite.TAB_ACCENT).getWidth());
        }
    }

    @Test
    public void unchangedComponentsKeepLegacyColors() throws IOException {
        assertSolid(read(DEFAULT, Sprite.SCROLLBAR_TRACK), 0xFF2A2A2A);
        for (File theme : new File[] { DEFAULT, MODERNITY }) {
            BufferedImage atlas = legacy(theme == DEFAULT ? "default" : "modernity");
            assertSolid(read(theme, Sprite.TAB_STRIP), atlas.getRGB(48, 0));
            assertBorder(read(theme, Sprite.SEARCH), 0xFFA0A0A0, 0xFF000000);
            assertBorder(read(theme, Sprite.SEARCH_FOCUSED), 0xFFA0A0A0, 0xFF000000);
            assertBorder(read(theme, Sprite.TOOLTIP), atlas.getRGB(1, 128), atlas.getRGB(0, 128));
            BufferedImage accent = read(theme, Sprite.TAB_ACCENT);
            for (int y = 0; y < accent.getHeight(); y++) {
                for (int x = 0; x < accent.getWidth(); x++) {
                    assertEquals(x < 2 ? 0xFFFFFFFF : 0, accent.getRGB(x, y));
                }
            }
        }
    }

    @Test
    public void fixedControlIconsMatchMinimizeColorWithinTheirBounds() throws IOException {
        Sprite[] icons = { Sprite.EXPAND, Sprite.ARROW_LEFT, Sprite.ARROW_RIGHT, Sprite.MODE_NORMAL, Sprite.MODE_LOCKED,
            Sprite.MODE_RECEIVE, Sprite.MODE_RESUPPLY };
        for (File theme : new File[] { DEFAULT, MODERNITY }) {
            int iconColor = theme == MODERNITY ? 0xFFF2F2F2 : 0xFF404040;
            BufferedImage minimize = read(theme, Sprite.MINIMIZE);
            for (int y = 0; y < minimize.getHeight(); y++) {
                for (int x = 0; x < minimize.getWidth(); x++) {
                    int pixel = minimize.getRGB(x, y);
                    assertTrue(pixel == 0 || pixel == iconColor);
                }
            }
            for (Sprite sprite : icons) {
                BufferedImage image = read(theme, sprite);
                int visible = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getRGB(x, y);
                        assertTrue(sprite.name(), pixel == 0 || pixel == iconColor);
                        if (pixel != 0) {
                            visible++;
                            assertTrue(x > 0 && y > 0 && x < image.getWidth() - 1 && y < image.getHeight() - 1);
                        }
                    }
                }
                assertTrue(sprite.name(), visible > 5);
            }
        }
    }

    @Test
    public void modernityScrollbarUsesBeveledBlueGrayAndBlueHoverColors() throws IOException {
        BufferedImage track = read(MODERNITY, Sprite.SCROLLBAR_TRACK);
        assertEquals(0, track.getRGB(0, 0));
        assertEquals(0xFFF2F2F2, track.getRGB(3, 0));
        assertEquals(0xFF696D88, track.getRGB(4, 1));
        assertEquals(0xFF9A9FB4, track.getRGB(4, 3));
        BufferedImage thumb = read(MODERNITY, Sprite.SCROLLBAR_THUMB);
        assertEquals(0xFF413F54, thumb.getRGB(0, 0));
        assertEquals(0xFFADB0C4, thumb.getRGB(1, 1));
        assertEquals(0xFF9A9FB4, thumb.getRGB(2, 3));
        assertEquals(0xFF696D88, thumb.getRGB(1, 12));
        BufferedImage hover = read(MODERNITY, Sprite.SCROLLBAR_THUMB_HOVER);
        assertEquals(0xFF413F54, hover.getRGB(0, 0));
        assertEquals(0xFFDAFFFF, hover.getRGB(1, 1));
        assertEquals(0xFF9CD3FF, hover.getRGB(2, 3));
        assertEquals(0xFF708CBA, hover.getRGB(1, 12));
        BufferedImage disabled = read(MODERNITY, Sprite.SCROLLBAR_THUMB_DISABLED);
        assertEquals(0xFF413F54, disabled.getRGB(0, 0));
        assertEquals(0xFF878FA5, disabled.getRGB(1, 1));
        assertEquals(0xFF696D88, disabled.getRGB(2, 3));
        assertEquals(0, disabled.getRGB(0, 14));
    }

    @Test
    public void arrowAndScrollbarStatesHaveDistinctTextures() throws IOException {
        for (File theme : new File[] { DEFAULT, MODERNITY }) {
            for (Sprite[] states : new Sprite[][] { { Sprite.ARROW_NORMAL, Sprite.ARROW_HOVER, Sprite.ARROW_DISABLED },
                { Sprite.SCROLLBAR_THUMB, Sprite.SCROLLBAR_THUMB_HOVER, Sprite.SCROLLBAR_THUMB_DISABLED } }) {
                BufferedImage normal = read(theme, states[0]);
                BufferedImage hover = read(theme, states[1]);
                BufferedImage disabled = read(theme, states[2]);
                int hoverChanges = 0;
                int disabledChanges = 0;
                for (int y = 0; y < normal.getHeight(); y++) {
                    for (int x = 0; x < normal.getWidth(); x++) {
                        assertEquals(normal.getRGB(x, y) >>> 24, hover.getRGB(x, y) >>> 24);
                        if (normal.getRGB(x, y) != hover.getRGB(x, y)) hoverChanges++;
                        if (normal.getRGB(x, y) != disabled.getRGB(x, y)) disabledChanges++;
                    }
                }
                assertTrue(states[1].name(), hoverChanges > 20);
                assertTrue(states[2].name(), disabledChanges > 20);
            }
        }
        assertEquals(0xFF9CD3FF, read(MODERNITY, Sprite.ARROW_HOVER).getRGB(4, 5));
    }

    private static void assertLegacyCrops(File theme, String name) throws IOException {
        BufferedImage atlas = legacy(name);
        assertCrop(theme, atlas, Sprite.PANEL, 0, 0);
        assertCrop(theme, atlas, Sprite.TITLE, 32, 0);
        assertCrop(theme, atlas, Sprite.SLOT, 0, 32);
        assertCrop(theme, atlas, Sprite.SLOT_HOVER, 18, 32);
        assertCrop(theme, atlas, Sprite.BUTTON_NORMAL, 0, 80);
        assertCrop(theme, atlas, Sprite.BUTTON_HOVER, 12, 80);
        assertCrop(theme, atlas, Sprite.BUTTON_DISABLED, 0, 80);
        assertCrop(theme, atlas, Sprite.MINIMIZE, 24, 80);
        assertCrop(theme, atlas, Sprite.ARROW_NORMAL, 0, 96);
        assertCrop(theme, atlas, Sprite.ARROW_DISABLED, 10, 96);
    }

    private static void assertCrop(File theme, BufferedImage atlas, Sprite sprite, int u, int v) throws IOException {
        BufferedImage image = read(theme, sprite);
        for (int y = 0; y < sprite.height; y++) {
            for (int x = 0; x < sprite.width; x++) {
                assertEquals(sprite.name() + " at " + x + "," + y, atlas.getRGB(u + x, v + y), image.getRGB(x, y));
            }
        }
    }

    private static void assertSolid(BufferedImage image, int color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) assertEquals(color, image.getRGB(x, y));
        }
    }

    private static void assertBorder(BufferedImage image, int border, int center) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                boolean edge = x == 0 || y == 0 || x == image.getWidth() - 1 || y == image.getHeight() - 1;
                assertEquals(edge ? border : center, image.getRGB(x, y));
            }
        }
    }

    private static BufferedImage read(File theme, Sprite sprite) throws IOException {
        BufferedImage image = ImageIO.read(new File(theme, sprite.resource.getResourcePath()));
        assertNotNull(sprite.name(), image);
        return image;
    }

    private static BufferedImage legacy(String name) throws IOException {
        try (InputStream stream = OverlayTextureTest.class.getResourceAsStream("/overlay/" + name + "-legacy.png")) {
            assertNotNull(name, stream);
            return ImageIO.read(stream);
        }
    }
}
