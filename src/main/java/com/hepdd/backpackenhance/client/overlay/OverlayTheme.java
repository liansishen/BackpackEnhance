package com.hepdd.backpackenhance.client.overlay;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Properties;

import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.util.ResourceLocation;

import com.hepdd.backpackenhance.BackpackEnhance;

public final class OverlayTheme implements IResourceManagerReloadListener {

    enum Color {

        TEXT("text", 0xFF404040),
        SEARCH_TEXT("search.text", 0xFFE0E0E0),
        SEARCH_DISABLED("search.disabled", 0xFF707070),
        SEARCH_HINT("search.hint", 0xFF808080),
        SEARCH_SHADE("search.shade", 0x80000000),
        TOOLTIP_TEXT("tooltip.text", 0xFFFFFFFF);

        final String key;
        final int fallback;

        Color(String key, int fallback) {
            this.key = key;
            this.fallback = fallback;
        }
    }

    public static final OverlayTheme INSTANCE = new OverlayTheme();
    static final ResourceLocation COLORS = new ResourceLocation("backpackenhance", "gui/overlay.properties");

    private EnumMap<Color, Integer> colors = defaults();

    static int color(Color color) {
        return INSTANCE.colors.get(color);
    }

    @Override
    public void onResourceManagerReload(IResourceManager manager) {
        colors = defaults();
        try {
            // Minecraft returns resource packs from lowest to highest priority.
            for (IResource resource : manager.getAllResources(COLORS)) {
                try (InputStream stream = resource.getInputStream()) {
                    Properties properties = new Properties();
                    properties.load(stream);
                    apply(colors, properties);
                }
            }
        } catch (IOException e) {
            BackpackEnhance.LOG.warn("Unable to load overlay colors from {}", COLORS, e);
        }
    }

    static EnumMap<Color, Integer> defaults() {
        EnumMap<Color, Integer> result = new EnumMap<>(Color.class);
        for (Color color : Color.values()) result.put(color, color.fallback);
        return result;
    }

    static void apply(EnumMap<Color, Integer> colors, Properties properties) {
        for (Color color : Color.values()) {
            String value = properties.getProperty(color.key);
            if (value == null) continue;
            try {
                colors.put(color, parseColor(value));
            } catch (IllegalArgumentException e) {
                BackpackEnhance.LOG.warn("Invalid overlay color {}={}; expected RRGGBB or AARRGGBB", color.key, value);
            }
        }
    }

    static int parseColor(String value) {
        String hex = value.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (!hex.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) throw new IllegalArgumentException(value);
        int result = (int) Long.parseLong(hex, 16);
        return hex.length() == 6 ? result | 0xFF000000 : result;
    }
}
