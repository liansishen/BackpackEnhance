package com.hepdd.backpackenhance.client.keybind;

import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Keyboard;

import com.hepdd.backpackenhance.Config;

import cpw.mods.fml.client.registry.ClientRegistry;

public final class KeyBindings {

    private static final String TOGGLE_OVERLAY_KEY = "key.backpackenhance.toggle";

    /** Toggle overlay minimized / expanded while a GUI is open (default T). */
    public static KeyBinding toggleOverlay;

    private KeyBindings() {}

    public static void init() {
        int defaultKey = parseKeyCode(Config.overlayHotkey);
        if (defaultKey == Keyboard.KEY_NONE) {
            defaultKey = Keyboard.KEY_T;
        }
        toggleOverlay = new KeyBinding(TOGGLE_OVERLAY_KEY, defaultKey, "key.categories.backpackenhance");
        ClientRegistry.registerKeyBinding(toggleOverlay);
    }

    private static int parseKeyCode(String configuredKey) {
        if (configuredKey == null || configuredKey.trim()
            .isEmpty() || TOGGLE_OVERLAY_KEY.equals(configuredKey)) {
            return Keyboard.KEY_T;
        }

        String keyName = configuredKey.trim()
            .toUpperCase();
        if (!keyName.startsWith("KEY_")) {
            keyName = "KEY_" + keyName;
        }
        int key = Keyboard.getKeyIndex(keyName.substring(4));
        return key == Keyboard.KEY_NONE ? Keyboard.KEY_T : key;
    }
}
