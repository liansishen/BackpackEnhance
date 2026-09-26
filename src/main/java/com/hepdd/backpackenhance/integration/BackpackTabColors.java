package com.hepdd.backpackenhance.integration;

import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.darkona.adventurebackpack.common.Constants;
import com.darkona.adventurebackpack.reference.BackpackTypes;
import com.darkona.adventurebackpack.util.BackpackUtils;

/**
 * Resolves the accent color for overlay tab strips from the backpack item itself:
 * dye/meta for Brad's, skin/type body color for AdventureBackpack, definition color for Forestry.
 */
public final class BackpackTabColors {

    private static final int LEATHER = argb(0x8B5A2B);
    private static final int ENDER = argb(0x6B3FA0);
    private static final int WORKBENCH = argb(0x9A6A34);

    private BackpackTabColors() {}

    public static int resolve(BackpackKind kind, ItemStack stack) {
        if (kind == null) {
            return LEATHER;
        }
        if (stack == null) {
            return kind.color;
        }
        if (kind == BackpackKind.ADVENTURE) {
            return resolveAdventure(stack, kind);
        }
        if (kind == BackpackKind.BRADS || kind == BackpackKind.BRADS_WORKBENCH || kind == BackpackKind.BRADS_ENDER) {
            return resolveBrads(stack, kind);
        }
        if (kind == BackpackKind.FORESTRY) {
            return resolveForestry(stack, kind);
        }
        return kind.color;
    }

    private static int resolveForestry(ItemStack stack, BackpackKind kind) {
        try {
            Class<?> adapterClass = Class
                .forName("com.hepdd.backpackenhance.integration.forestry.ForestryBackpackAccess");
            Object result = adapterClass.getMethod("primaryColor", ItemStack.class, Integer.TYPE)
                .invoke(null, stack, Integer.valueOf(kind.color));
            return result instanceof Integer ? ((Integer) result).intValue() : kind.color;
        } catch (ReflectiveOperationException ignored) {
            return kind.color;
        } catch (LinkageError ignored) {
            return kind.color;
        }
    }

    /**
     * Brad's: item damage meta encodes dye. {@code damage % 100}: 0 undyed leather, 1–16 dyes
     * (dyeDamage + 1), 17 workbench, 99 ender. Tier is {@code damage / 100}.
     */
    private static int resolveBrads(ItemStack stack, BackpackKind kind) {
        int meta = stack.getItemDamage() % 100;
        if (meta == 99 || kind == BackpackKind.BRADS_ENDER) {
            return ENDER;
        }
        if (meta == 17 || kind == BackpackKind.BRADS_WORKBENCH) {
            return WORKBENCH;
        }
        if (meta >= 1 && meta <= 16) {
            return dyeColorArgb(meta - 1);
        }
        // Undyed / default leather body
        return LEATHER;
    }

    /**
     * Adventure: NBT wearable compound {@code type} → {@link BackpackTypes} skin.
     * Dye-named skins use vanilla dye palette; themed skins use approximate body colors.
     */
    private static int resolveAdventure(ItemStack stack, BackpackKind kind) {
        try {
            BackpackTypes type = readAdventureType(stack);
            if (type == null || type == BackpackTypes.UNKNOWN) {
                return kind.color;
            }
            return colorForAdventureType(type, kind);
        } catch (Throwable ignored) {
            return kind.color;
        }
    }

    private static BackpackTypes readAdventureType(ItemStack stack) {
        // Prefer official helper (handles wearable compound + normalize).
        BackpackTypes type = BackpackTypes.getType(stack);
        if (type != null && type != BackpackTypes.UNKNOWN) {
            return type;
        }
        // Fallback: damage meta or root NBT
        if (stack.hasTagCompound()) {
            NBTTagCompound root = stack.getTagCompound();
            if (root.hasKey(Constants.TAG_TYPE)) {
                return BackpackTypes.getType(root.getByte(Constants.TAG_TYPE));
            }
            NBTTagCompound wearable = BackpackUtils.getOrCreateWearableCompound(stack);
            if (wearable != null && wearable.hasKey(Constants.TAG_TYPE)) {
                return BackpackTypes.getType(wearable.getByte(Constants.TAG_TYPE));
            }
        }
        return BackpackTypes.getType(stack.getItemDamage() & 0xFF);
    }

    private static int colorForAdventureType(BackpackTypes type, BackpackKind fallbackKind) {
        switch (type) {
            case STANDARD:
            case LEATHER:
                return LEATHER;
            case BLACK:
                return dyeColorArgb(0);
            case RED:
                return dyeColorArgb(1);
            case GREEN:
                return dyeColorArgb(2);
            case BROWN:
                return dyeColorArgb(3);
            case BLUE:
                return dyeColorArgb(4);
            case PURPLE:
                return dyeColorArgb(5);
            case CYAN:
                return dyeColorArgb(6);
            case LIGHT_GRAY:
                return dyeColorArgb(7);
            case GRAY:
                return dyeColorArgb(8);
            case PINK:
                return dyeColorArgb(9);
            case LIME:
                return dyeColorArgb(10);
            case YELLOW:
                return dyeColorArgb(11);
            case LIGHT_BLUE:
                return dyeColorArgb(12);
            case MAGENTA:
                return dyeColorArgb(13);
            case ORANGE:
                return dyeColorArgb(14);
            case WHITE:
                return dyeColorArgb(15);
            // Themed skins — approximate dominant body color
            case COW:
            case HORSE:
            case WOLF:
            case VILLAGER:
                return argb(0xA07850);
            case CHICKEN:
            case EGG:
                return argb(0xF0E8D0);
            case PIG:
            case PIGMAN:
                return argb(0xF0A0A8);
            case SHEEP:
                return argb(0xE8E8E8);
            case OCELOT:
                return argb(0xE8B84A);
            case SQUID:
                return argb(0x2A4A6A);
            case CREEPER:
                return argb(0x4A8A3A);
            case SKELETON:
            case WITHER_SKELETON:
                return argb(0xC8C8B0);
            case ZOMBIE:
                return argb(0x5A7A3A);
            case SPIDER:
            case SILVERFISH:
                return argb(0x4A3030);
            case BLAZE:
            case MAGMA_CUBE:
            case NETHER:
                return argb(0xE07020);
            case ENDERMAN:
            case END:
            case DRAGON:
                return argb(0x5A2080);
            case GHAST:
            case QUARTZ:
            case SNOW:
                return argb(0xE8E8F0);
            case SLIME:
            case CACTUS:
            case MELON:
                return argb(0x6ABA3A);
            case BAT:
            case COAL:
            case OBSIDIAN:
            case WITHER:
                return argb(0x2A2A2A);
            case DIAMOND:
                return argb(0x4AEDD9);
            case EMERALD:
                return argb(0x2ECC71);
            case GOLD:
                return argb(0xF1C40F);
            case IRON:
            case IRON_GOLEM:
                return argb(0xD0D0D0);
            case LAPIS:
                return argb(0x1A3A8A);
            case REDSTONE:
                return argb(0xC03030);
            case GLOWSTONE:
            case SUNFLOWER:
                return argb(0xF0D030);
            case PUMPKIN:
            case CARROT:
                return argb(0xE08020);
            case BOOKSHELF:
            case CHEST:
            case HAYBALE:
            case SANDSTONE:
            case OVERWORLD:
                return argb(0xA08040);
            case CAKE:
            case COOKIE:
                return argb(0xE8C0A0);
            case SPONGE:
                return argb(0xD0D040);
            case RAINBOW:
                return argb(0xE050A0);
            case DELUXE:
            case MODDED_NETWORK:
                return argb(0x4A90D0);
            case MOOSHROOM:
            case RED_MUSHROOM:
                return argb(0xC04040);
            case BROWN_MUSHROOM:
                return argb(0x8A6040);
            case ELECTRIC:
                return argb(0x40C0E0);
            default:
                return fallbackKind.color;
        }
    }

    private static int dyeColorArgb(int dyeDamage) {
        if (dyeDamage < 0 || dyeDamage >= ItemDye.field_150922_c.length) {
            return LEATHER;
        }
        return argb(ItemDye.field_150922_c[dyeDamage]);
    }

    private static int argb(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }
}
