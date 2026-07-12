package com.hepdd.backpackenhance.server.overlay;

import java.lang.reflect.Method;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;

/**
 * Shared slot classification for ModularUI / ModularUI2 / vanilla containers.
 * ModularUI2 {@code SlotItemHandler} always uses a dummy empty inventory, so
 * {@code slot.inventory == player.inventory} is never true for those slots.
 */
public final class OverlaySlotUtil {

    private static final String PLAYER_MAIN_INV_WRAPPER_MUI1 = "com.gtnewhorizons.modularui.api.forge.PlayerMainInvWrapper";
    private static final String PLAYER_MAIN_INV_WRAPPER_MUI2 = "com.cleanroommc.modularui.utils.item.PlayerMainInvWrapper";
    private static final String PLAYER_INV_WRAPPER_MUI2 = "com.cleanroommc.modularui.utils.item.PlayerInvWrapper";
    private static final String RANGED_WRAPPER_MUI1 = "com.gtnewhorizons.modularui.api.forge.RangedWrapper";
    private static final String RANGED_WRAPPER_MUI2 = "com.cleanroommc.modularui.utils.item.RangedWrapper";
    private static final String INV_WRAPPER_MUI1 = "com.gtnewhorizons.modularui.api.forge.InvWrapper";
    private static final String INV_WRAPPER_MUI2 = "com.cleanroommc.modularui.utils.item.InvWrapper";

    private OverlaySlotUtil() {}

    public static boolean isPlayerInventorySlot(EntityPlayer player, Slot sourceSlot) {
        if (player == null || sourceSlot == null) {
            return false;
        }
        if (sourceSlot.inventory instanceof InventoryPlayer || sourceSlot.inventory == player.inventory) {
            return true;
        }
        return isModularUiPlayerInventorySlot(sourceSlot);
    }

    /**
     * Crafting-table / workstation output slots must never be intercepted by prioritize-shift:
     * taking the result requires native craft logic (consume matrix, multi-craft on shift).
     */
    public static boolean isCraftingResultSlot(Slot slot) {
        if (slot == null) {
            return false;
        }
        if (slot instanceof SlotCrafting) {
            return true;
        }
        if (slot.inventory instanceof InventoryCraftResult) {
            return true;
        }
        // Mod workstations often use *SlotCrafting / *CraftResult* without extending SlotCrafting.
        Class<?> type = slot.getClass();
        while (type != null && type != Slot.class && type != Object.class) {
            String name = type.getName();
            if (name.endsWith("SlotCrafting") || name.contains("CraftingResult")
                || name.contains("CraftResult")
                || name.contains("SlotRecipe") && name.contains("Result")) {
                return true;
            }
            type = type.getSuperclass();
        }
        if (slot.inventory != null) {
            String invName = slot.inventory.getClass()
                .getName();
            if (invName.contains("InventoryCraftResult") || invName.contains("CraftResultInventory")
                || invName.contains("CraftingResult")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Slots that prioritize-shift must never steal from (craft output, phantoms).
     */
    public static boolean shouldNeverPrioritizeShiftFrom(EntityPlayer player, Slot sourceSlot) {
        return isCraftingResultSlot(sourceSlot) || isPhantomSlot(sourceSlot);
    }

    /**
     * Whether a shift-click on {@code sourceSlot} may be routed into the expanded overlay.
     * <ul>
     * <li>Craft result / phantom: never</li>
     * <li>Container / machine slots: yes (when session prioritizes)</li>
     * <li>Player inventory: only on the standalone player inventory GUI
     * ({@link ContainerPlayer}). When a chest/machine is open, vanilla must handle player-bar
     * shift first (into the container).</li>
     * </ul>
     */
    public static boolean shouldPrioritizeShiftFrom(EntityPlayer player, Slot sourceSlot) {
        if (player == null || sourceSlot == null) {
            return false;
        }
        if (shouldNeverPrioritizeShiftFrom(player, sourceSlot)) {
            return false;
        }
        if (isPlayerInventorySlot(player, sourceSlot)) {
            return isStandalonePlayerInventory(player);
        }
        return true;
    }

    /**
     * True when the open container is only the player inventory screen (no chest/machine).
     */
    public static boolean isStandalonePlayerInventory(EntityPlayer player) {
        return player != null && player.openContainer instanceof ContainerPlayer;
    }

    private static boolean isModularUiPlayerInventorySlot(Slot slot) {
        Object handler = getItemHandler(slot);
        return isPlayerInventoryHandler(handler);
    }

    public static Object getItemHandler(Slot slot) {
        if (slot == null) {
            return null;
        }
        try {
            Method getItemHandler = slot.getClass()
                .getMethod("getItemHandler");
            return getItemHandler.invoke(slot);
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isPlayerInventoryHandler(Object handler) {
        if (handler == null) {
            return false;
        }
        if (isClassOrSuperclass(handler.getClass(), PLAYER_MAIN_INV_WRAPPER_MUI1)
            || isClassOrSuperclass(handler.getClass(), PLAYER_MAIN_INV_WRAPPER_MUI2)
            || isClassOrSuperclass(handler.getClass(), PLAYER_INV_WRAPPER_MUI2)) {
            return true;
        }
        if (isClassOrSuperclass(handler.getClass(), RANGED_WRAPPER_MUI1)
            || isClassOrSuperclass(handler.getClass(), RANGED_WRAPPER_MUI2)) {
            Object compose = invokeNoArg(handler, "getCompose");
            if (compose == null) {
                return false;
            }
            if (isClassOrSuperclass(compose.getClass(), PLAYER_MAIN_INV_WRAPPER_MUI1)
                || isClassOrSuperclass(compose.getClass(), PLAYER_MAIN_INV_WRAPPER_MUI2)
                || isClassOrSuperclass(compose.getClass(), PLAYER_INV_WRAPPER_MUI2)) {
                return true;
            }
            if (isClassOrSuperclass(compose.getClass(), INV_WRAPPER_MUI1)
                || isClassOrSuperclass(compose.getClass(), INV_WRAPPER_MUI2)) {
                Object source = invokeNoArg(compose, "getSourceInventory");
                return source instanceof InventoryPlayer;
            }
        }
        Object source = invokeNoArg(handler, "getSourceInventory");
        if (source instanceof InventoryPlayer) {
            return true;
        }
        Object invPlayer = invokeNoArg(handler, "getInventoryPlayer");
        return invPlayer instanceof InventoryPlayer;
    }

    /**
     * ModularUI shift priority: lower is preferred. {@link Integer#MIN_VALUE} means shift-disabled.
     * Missing API → 0 (neutral).
     */
    public static int getShiftClickPriority(Slot slot) {
        if (slot == null) {
            return 0;
        }
        Object value = invokeNoArg(slot, "getShiftClickPriority");
        if (value instanceof Integer) {
            return ((Integer) value).intValue();
        }
        return 0;
    }

    public static boolean isPhantomSlot(Slot slot) {
        if (slot == null) {
            return false;
        }
        Object value = invokeNoArg(slot, "isPhantom");
        return value instanceof Boolean && ((Boolean) value).booleanValue();
    }

    public static boolean isShiftInsertDisabled(Slot slot) {
        return getShiftClickPriority(slot) == Integer.MIN_VALUE || isPhantomSlot(slot);
    }

    public static boolean canMerge(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.isItemEqual(b)
            && ItemStack.areItemStackTagsEqual(a, b)
            && a.stackSize < a.getMaxStackSize();
    }

    private static Object invokeNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass()
                .getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isClassOrSuperclass(Class<?> type, String className) {
        Class<?> current = type;
        while (current != null) {
            if (className.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }
}
