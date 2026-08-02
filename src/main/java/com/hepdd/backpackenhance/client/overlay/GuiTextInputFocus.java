package com.hepdd.backpackenhance.client.overlay;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiTextField;

/**
 * Detects focused text inputs in modded GUIs without requiring every GUI to expose the same API.
 */
final class GuiTextInputFocus {

    private static final int MAX_DEPTH = 4;

    private GuiTextInputFocus() {}

    static boolean hasFocusedTextInput(Object root) {
        return hasFocusedTextInput(root, new IdentityHashMap<Object, Boolean>(), 0);
    }

    private static boolean hasFocusedTextInput(Object value, Map<Object, Boolean> seen, int depth) {
        if (value == null || depth > MAX_DEPTH || seen.containsKey(value)) {
            return false;
        }
        seen.put(value, Boolean.TRUE);

        if (isFocusedTextInput(value)) {
            return true;
        }

        Class<?> type = value.getClass();
        if (isTerminalType(type)) {
            return false;
        }

        if (type.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (hasFocusedTextInput(Array.get(value, i), seen, depth + 1)) {
                    return true;
                }
            }
            return false;
        }

        if (value instanceof Iterable) {
            for (Object child : (Iterable<?>) value) {
                if (hasFocusedTextInput(child, seen, depth + 1)) {
                    return true;
                }
            }
            return false;
        }

        if (value instanceof Map) {
            for (Object child : ((Map<?, ?>) value).values()) {
                if (hasFocusedTextInput(child, seen, depth + 1)) {
                    return true;
                }
            }
            return false;
        }

        return hasFocusedField(value, seen, depth);
    }

    private static boolean isFocusedTextInput(Object value) {
        if (value instanceof GuiTextField) {
            return ((GuiTextField) value).isFocused();
        }

        Class<?> type = value.getClass();
        if (!looksLikeTextInput(type)) {
            return false;
        }

        return hasBooleanFocusField(value, type) || callsFocusedMethod(value, type);
    }

    private static boolean hasFocusedField(Object value, Map<Object, Boolean> seen, int depth) {
        Class<?> type = value.getClass();
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (Modifier.isStatic(field.getModifiers()) || field.getType()
                    .isPrimitive()) {
                    continue;
                }
                if (!shouldInspectField(field)) {
                    continue;
                }
                Object child = getFieldValue(field, value);
                if (hasFocusedTextInput(child, seen, depth + 1)) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean hasBooleanFocusField(Object value, Class<?> type) {
        while (type != null && type != Object.class) {
            Field[] fields = type.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType() != Boolean.TYPE && field.getType() != Boolean.class) {
                    continue;
                }
                String name = field.getName()
                    .toLowerCase();
                if (!name.equals("focused") && !name.equals("focus") && !name.equals("isfocused")) {
                    continue;
                }
                Object focused = getFieldValue(field, value);
                if (Boolean.TRUE.equals(focused)) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean callsFocusedMethod(Object value, Class<?> type) {
        String[] names = { "isFocused", "focused", "hasFocus" };
        for (int i = 0; i < names.length; i++) {
            Method method = findNoArgBooleanMethod(type, names[i]);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                Object focused = method.invoke(value);
                if (Boolean.TRUE.equals(focused)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // Some mod widgets throw if queried before init; treat them as not focused.
            }
        }
        return false;
    }

    private static Method findNoArgBooleanMethod(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(name);
                if (method.getParameterTypes().length == 0
                    && (method.getReturnType() == Boolean.TYPE || method.getReturnType() == Boolean.class)) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
                // Try superclass.
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean shouldInspectField(Field field) {
        Class<?> type = field.getType();
        if (isTerminalType(type)) {
            return false;
        }
        if (GuiTextField.class.isAssignableFrom(type) || type.isArray()
            || Iterable.class.isAssignableFrom(type)
            || Map.class.isAssignableFrom(type)) {
            return true;
        }
        return looksLikeTextInput(type) || isGuiOwnedType(type);
    }

    private static boolean looksLikeTextInput(Class<?> type) {
        String name = type.getName()
            .toLowerCase();
        return name.contains("textfield") || name.contains("text_field")
            || name.contains("textbox")
            || name.contains("textinput")
            || name.contains("inputfield")
            || name.contains("searchfield");
    }

    private static boolean isGuiOwnedType(Class<?> type) {
        String name = type.getName();
        return name.startsWith("net.minecraft.client.gui.") || name.startsWith("com.")
            || name.startsWith("codechicken.")
            || name.startsWith("gregtech.")
            || name.startsWith("gregtechmod.")
            || name.startsWith("gtPlusPlus.")
            || name.startsWith("tectech.");
    }

    private static boolean isTerminalType(Class<?> type) {
        if (type.isPrimitive() || type.isEnum()
            || type == String.class
            || Number.class.isAssignableFrom(type)
            || type == Boolean.class
            || type == Character.class
            || type == Class.class
            || type == Minecraft.class
            || type == FontRenderer.class) {
            return true;
        }
        String name = type.getName();
        return name.startsWith("java.") && !Iterable.class.isAssignableFrom(type) && !Map.class.isAssignableFrom(type);
    }

    private static Object getFieldValue(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
