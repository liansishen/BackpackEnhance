package com.hepdd.backpackenhance.integration;

import net.minecraft.item.ItemStack;

public final class ForestryModeBridge {

    public static final int NO_MODE = -1;

    private static final String ACCESS_CLASS =
        "com.hepdd.backpackenhance.integration.forestry.ForestryBackpackAccess";

    private ForestryModeBridge() {}

    public static ModeState state(ItemStack stack) {
        try {
            Class<?> access = Class.forName(ACCESS_CLASS);
            boolean available = ((Boolean) access.getMethod("supportsModeCycle", ItemStack.class)
                .invoke(null, stack)).booleanValue();
            if (!available) {
                return ModeState.NONE;
            }
            int modeId = ((Integer) access.getMethod("modeId", ItemStack.class)
                .invoke(null, stack)).intValue();
            int nextModeId = ((Integer) access.getMethod("nextModeId", ItemStack.class)
                .invoke(null, stack)).intValue();
            boolean resupplyEnabled = ((Boolean) access.getMethod("isResupplyEnabled")
                .invoke(null)).booleanValue();
            return new ModeState(modeId, nextModeId, true, resupplyEnabled);
        } catch (ReflectiveOperationException ignored) {
            return ModeState.NONE;
        } catch (LinkageError ignored) {
            return ModeState.NONE;
        }
    }

    public static boolean hasUid(ItemStack stack) {
        try {
            Class<?> access = Class.forName(ACCESS_CLASS);
            return ((Boolean) access.getMethod("hasUid", ItemStack.class)
                .invoke(null, stack)).booleanValue();
        } catch (ReflectiveOperationException ignored) {
            return false;
        } catch (LinkageError ignored) {
            return false;
        }
    }

    public static int uid(ItemStack stack) {
        try {
            Class<?> access = Class.forName(ACCESS_CLASS);
            return ((Integer) access.getMethod("uid", ItemStack.class)
                .invoke(null, stack)).intValue();
        } catch (ReflectiveOperationException ignored) {
            return 0;
        } catch (LinkageError ignored) {
            return 0;
        }
    }

    public static boolean isItemValid(ItemStack backpackStack, ItemStack candidate) {
        try {
            Class<?> access = Class.forName(ACCESS_CLASS);
            return ((Boolean) access.getMethod("isItemValid", ItemStack.class, ItemStack.class)
                .invoke(null, backpackStack, candidate)).booleanValue();
        } catch (ReflectiveOperationException ignored) {
            return false;
        } catch (LinkageError ignored) {
            return false;
        }
    }

    public static boolean cycleMode(ItemStack stack, int expectedMode, boolean expectedHasUid, int expectedUid) {
        try {
            Class<?> access = Class.forName(ACCESS_CLASS);
            return ((Boolean) access
                .getMethod("cycleMode", ItemStack.class, Integer.TYPE, Boolean.TYPE, Integer.TYPE)
                .invoke(
                    null,
                    stack,
                    Integer.valueOf(expectedMode),
                    Boolean.valueOf(expectedHasUid),
                    Integer.valueOf(expectedUid)))
                        .booleanValue();
        } catch (ReflectiveOperationException ignored) {
            return false;
        } catch (LinkageError ignored) {
            return false;
        }
    }

    public static final class ModeState {

        public static final ModeState NONE = new ModeState(NO_MODE, NO_MODE, false, false);

        public final int modeId;
        public final int nextModeId;
        public final boolean available;
        public final boolean resupplyEnabled;

        private ModeState(int modeId, int nextModeId, boolean available, boolean resupplyEnabled) {
            this.modeId = modeId;
            this.nextModeId = nextModeId;
            this.available = available;
            this.resupplyEnabled = resupplyEnabled;
        }
    }
}
