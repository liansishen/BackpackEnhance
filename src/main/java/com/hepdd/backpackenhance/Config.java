package com.hepdd.backpackenhance;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.hepdd.backpackenhance.client.overlay.OverlayGuiContainerAccess;

/**
 * Mod configuration. Overlay placement is stored <strong>relative to the open container
 * panel</strong> ({@code guiLeft}/{@code guiTop}):
 * {@code screenX = guiLeft + offsetX}, {@code screenY = guiTop + offsetY}.
 * Changing resolution re-centers the container; the overlay keeps the same offset beside it.
 */
public class Config {

    private static File configurationFile;

    /** Legacy absolute screen seed only (migration). */
    public static int overlayWindowX = -1;
    public static int overlayWindowY = -1;
    /**
     * Working offset from the container panel's top-left ({@code guiLeft}/{@code guiTop}).
     * When {@link #overlayHasContainerOffset} is false, use default placement.
     */
    public static int overlayOffX;
    public static int overlayOffY;
    public static boolean overlayHasContainerOffset;
    public static int overlayWidth = -1;
    public static int overlayHeight = -1;
    public static boolean overlayMinimized = false;
    /** Display columns for the overlay slot grid (not backpack NBT layout). Default 9. */
    public static int overlayColumns = 9;
    /** Default key for toggle minimize/expand in GUI (KEY_T). Rebindable in controls. */
    public static String overlayHotkey = "KEY_T";
    public static boolean overlayScanHotbar = true;
    public static String[] overlaySupportedMods = new String[0];

    /** Per-GUI positions: {@code class@xSize x ySize} → {@code {offsetX, offsetY}} from panel origin. */
    private static final Map<String, int[]> overlayPositionsByGui = new LinkedHashMap<String, int[]>();

    public static void synchronizeConfiguration(File configFile) {
        configurationFile = configFile;
        Configuration configuration = new Configuration(configFile);

        overlayWindowX = configuration.getInt(
            "windowX",
            "overlay",
            overlayWindowX,
            -1,
            Integer.MAX_VALUE,
            "Legacy absolute screen X (migration only).");
        overlayWindowY = configuration.getInt(
            "windowY",
            "overlay",
            overlayWindowY,
            -1,
            Integer.MAX_VALUE,
            "Legacy absolute screen Y (migration only).");
        overlayWidth = configuration.getInt(
            "width",
            "overlay",
            overlayWidth,
            -1,
            Integer.MAX_VALUE,
            "Last expanded overlay width. -1 uses automatic layout.");
        overlayHeight = configuration.getInt(
            "height",
            "overlay",
            overlayHeight,
            -1,
            Integer.MAX_VALUE,
            "Last expanded overlay height. -1 uses automatic layout.");
        overlayMinimized = configuration.getBoolean(
            "minimized",
            "overlay",
            overlayMinimized,
            "Whether the overlay starts minimized when it is active.");
        overlayColumns = configuration.getInt(
            "columns",
            "overlay",
            overlayColumns,
            1,
            18,
            "Number of slots per row in the overlay grid. Default 9 (vanilla inventory width).");
        overlayHotkey = configuration.getString(
            "hotkey",
            "overlay",
            overlayHotkey,
            "Default key for minimize/expand overlay in GUI (e.g. KEY_T). Rebind in Controls.");
        overlayScanHotbar = configuration.getBoolean(
            "scanHotbar",
            "overlay",
            overlayScanHotbar,
            "Whether backpacks in hotbar slots are included in overlay tabs.");
        overlaySupportedMods = configuration.getStringList(
            "supportedMods",
            "overlay",
            overlaySupportedMods,
            "Optional supported mod filter. Empty means all. Values: adventurebackpack, backpack.");

        Property positionsProp = configuration.get(
            "overlay",
            "guiPositions",
            new String[0],
            "Per-GUI overlay positions relative to the container panel. "
                + "Each entry: class@xSize x ySize|offsetX|offsetY "
                + "(offset from guiLeft/guiTop in GUI pixels).");
        loadGuiPositions(positionsProp.getStringList());

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static boolean isModEnabled(String modKey) {
        if (overlaySupportedMods.length == 0) {
            return true;
        }
        for (String supportedMod : overlaySupportedMods) {
            if (modKey.equalsIgnoreCase(supportedMod.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Key: {@code class@panelWxpanelH} from container xSize/ySize (distinguishes single/double chest).
     * Null until the container is laid out.
     */
    public static String guiPositionKey(GuiScreen gui) {
        if (gui == null) {
            return null;
        }
        String className = gui.getClass()
            .getName();
        int panelW = 0;
        int panelH = 0;
        if (gui instanceof OverlayGuiContainerAccess) {
            OverlayGuiContainerAccess access = (OverlayGuiContainerAccess) gui;
            panelW = access.backpackenhance$getXSize();
            panelH = access.backpackenhance$getYSize();
        }
        if (panelW > 0 && panelH > 0) {
            return className + "@" + panelW + "x" + panelH;
        }
        if (gui instanceof GuiContainer) {
            return null;
        }
        if (gui.width <= 0 || gui.height <= 0) {
            return null;
        }
        return className;
    }

    public static String guiPositionClassKey(GuiScreen gui) {
        return gui == null ? null
            : gui.getClass()
                .getName();
    }

    /** Container top-left; {@code null} if not a container with access. */
    public static int[] containerOrigin(GuiScreen gui) {
        if (gui instanceof OverlayGuiContainerAccess) {
            OverlayGuiContainerAccess access = (OverlayGuiContainerAccess) gui;
            return new int[] { access.backpackenhance$getGuiLeft(), access.backpackenhance$getGuiTop() };
        }
        return null;
    }

    /** Clear working offset (next layout uses default or reloads from map). */
    public static void clearWorkingOffset() {
        overlayHasContainerOffset = false;
        overlayOffX = 0;
        overlayOffY = 0;
    }

    /**
     * Set working offset from absolute screen position of the overlay top-left.
     */
    public static void setWorkingFromScreen(GuiScreen gui, int screenX, int screenY) {
        int[] origin = containerOrigin(gui);
        if (origin == null) {
            // Non-container: store as screen coords in legacy fields only
            overlayWindowX = screenX;
            overlayWindowY = screenY;
            overlayHasContainerOffset = false;
            return;
        }
        overlayOffX = screenX - origin[0];
        overlayOffY = screenY - origin[1];
        overlayHasContainerOffset = true;
        overlayWindowX = screenX;
        overlayWindowY = screenY;
    }

    /**
     * Absolute screen X from working container offset, or -1 for default placement.
     */
    public static int screenXFromOffset(GuiScreen gui) {
        if (!overlayHasContainerOffset) {
            return -1;
        }
        int[] origin = containerOrigin(gui);
        if (origin == null) {
            return overlayWindowX;
        }
        return origin[0] + overlayOffX;
    }

    public static int screenYFromOffset(GuiScreen gui) {
        if (!overlayHasContainerOffset) {
            return -1;
        }
        int[] origin = containerOrigin(gui);
        if (origin == null) {
            return overlayWindowY;
        }
        return origin[1] + overlayOffY;
    }

    /**
     * Default: to the right of the container panel (or screen-right if no container).
     */
    public static int defaultScreenX(GuiScreen gui, int overlayW) {
        int[] origin = containerOrigin(gui);
        int panelW = 0;
        if (gui instanceof OverlayGuiContainerAccess) {
            panelW = ((OverlayGuiContainerAccess) gui).backpackenhance$getXSize();
        }
        if (origin != null && panelW > 0) {
            return origin[0] + panelW + 8;
        }
        return gui.width - overlayW - 8;
    }

    public static int defaultScreenY(GuiScreen gui, int overlayH) {
        int[] origin = containerOrigin(gui);
        if (origin != null) {
            return origin[1];
        }
        return (gui.height - overlayH) / 2;
    }

    /** Load map entry into working container offset. */
    public static void applyGuiOverlayPosition(GuiScreen gui) {
        String key = guiPositionKey(gui);
        if (key == null || gui == null) {
            return;
        }
        int[] stored = findStored(gui, key);
        if (stored != null) {
            overlayOffX = stored[0];
            overlayOffY = stored[1];
            overlayHasContainerOffset = true;
            return;
        }
        // Legacy absolute screen seed once
        if (overlayPositionsByGui.isEmpty() && overlayWindowX >= 0 && overlayWindowY >= 0) {
            setWorkingFromScreen(gui, overlayWindowX, overlayWindowY);
            return;
        }
        clearWorkingOffset();
    }

    private static int[] findStored(GuiScreen gui, String key) {
        int[] stored = overlayPositionsByGui.get(key);
        if (stored != null) {
            return stored;
        }
        String classKey = guiPositionClassKey(gui);
        if (classKey != null) {
            stored = overlayPositionsByGui.get(classKey);
            if (stored != null) {
                return stored;
            }
        }
        // Old keys that wrongly used full screen size
        if (gui.width > 0 && gui.height > 0) {
            stored = overlayPositionsByGui.get(
                gui.getClass()
                    .getName() + "@"
                    + gui.width
                    + "x"
                    + gui.height);
        }
        return stored;
    }

    public static void saveOverlayStateFromScreen(GuiScreen gui, int screenX, int screenY, boolean minimized) {
        setWorkingFromScreen(gui, screenX, screenY);
        overlayMinimized = minimized;
        String key = guiPositionKey(gui);
        if (key != null && overlayHasContainerOffset) {
            overlayPositionsByGui.put(key, new int[] { overlayOffX, overlayOffY });
        }
        writeOverlayConfig();
    }

    private static void loadGuiPositions(String[] entries) {
        overlayPositionsByGui.clear();
        if (entries == null) {
            return;
        }
        for (String entry : entries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            int first = entry.indexOf('|');
            int second = first < 0 ? -1 : entry.indexOf('|', first + 1);
            if (first <= 0 || second <= first) {
                continue;
            }
            String key = entry.substring(0, first)
                .trim();
            if (key.isEmpty()) {
                continue;
            }
            try {
                // Accept int or float (legacy 0..1 screen-relative is ignored as unusable for container)
                float a = Float.parseFloat(
                    entry.substring(first + 1, second)
                        .trim());
                float b = Float.parseFloat(
                    entry.substring(second + 1)
                        .trim());
                // Old screen-relative 0..1 fractions: skip (cannot map to container without context)
                if (a >= 0.0F && a <= 1.0F
                    && b >= 0.0F
                    && b <= 1.0F
                    && (a != 0.0F || b != 0.0F)
                    && Math.abs(a - Math.rint(a)) > 1e-4
                    && Math.abs(b - Math.rint(b)) > 1e-4) {
                    // Looks like old 0..1 free-area fractions — discard rather than mis-place
                    continue;
                }
                overlayPositionsByGui.put(key, new int[] { Math.round(a), Math.round(b) });
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
    }

    private static String[] serializeGuiPositions() {
        List<String> lines = new ArrayList<String>(overlayPositionsByGui.size());
        for (Map.Entry<String, int[]> e : overlayPositionsByGui.entrySet()) {
            int[] pos = e.getValue();
            if (e.getKey() == null || pos == null || pos.length < 2) {
                continue;
            }
            lines.add(e.getKey() + "|" + pos[0] + "|" + pos[1]);
        }
        return lines.toArray(new String[lines.size()]);
    }

    private static void writeOverlayConfig() {
        if (configurationFile == null) {
            return;
        }
        Configuration configuration = new Configuration(configurationFile);
        configuration.get("overlay", "windowX", overlayWindowX)
            .set(overlayWindowX);
        configuration.get("overlay", "windowY", overlayWindowY)
            .set(overlayWindowY);
        configuration.get("overlay", "minimized", overlayMinimized)
            .set(overlayMinimized);
        configuration
            .get(
                "overlay",
                "guiPositions",
                new String[0],
                "Per-GUI overlay positions: class@xSize x ySize|offsetX|offsetY from container guiLeft/guiTop.")
            .set(serializeGuiPositions());
        configuration.save();
    }
}
