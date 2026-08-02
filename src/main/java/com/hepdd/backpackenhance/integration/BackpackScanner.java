package com.hepdd.backpackenhance.integration;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.hepdd.backpackenhance.Config;

import cpw.mods.fml.common.Loader;

public class BackpackScanner {

    private static final String ADVENTURE_ITEM = "com.darkona.adventurebackpack.item.ItemAdventureBackpack";
    private static final String BRADS_BASE_ITEM = "de.eydamos.backpack.item.ItemBackpackBase";
    private static final String BRADS_WORKBENCH_ITEM = "de.eydamos.backpack.item.ItemWorkbenchBackpack";
    private static final String FORESTRY_ITEM = "forestry.storage.items.ItemBackpack";

    private final Class<?> adventureBackpackItemClass = findClass(ADVENTURE_ITEM);
    private final Class<?> bradsBackpackBaseClass = findClass(BRADS_BASE_ITEM);
    private final Class<?> bradsWorkbenchBackpackClass = findClass(BRADS_WORKBENCH_ITEM);
    private final Class<?> forestryBackpackItemClass = findClass(FORESTRY_ITEM);

    public List<BackpackTab> scan(EntityPlayer player) {
        List<BackpackTab> result = new ArrayList<BackpackTab>();
        if (player == null || player.inventory == null) {
            return result;
        }

        int start = Config.overlayScanHotbar ? 0 : 9;
        for (int slot = start; slot < player.inventory.mainInventory.length; slot++) {
            ItemStack stack = player.inventory.mainInventory[slot];
            BackpackKind kind = detectKind(stack);
            if (kind != null && Config.isModEnabled(kind.modKey)) {
                int storageSlots = BackpackInventoryAccess.storageSlots(kind, stack);
                int columns = BackpackInventoryAccess.columns(kind, storageSlots, null, stack);
                result.add(new BackpackTab(result.size(), slot, kind, stack, columns, storageSlots));
            }
        }
        return result;
    }

    public BackpackKind detectKind(ItemStack stack) {
        if (stack == null) {
            return null;
        }

        Item item = stack.getItem();
        if (item == null) {
            return null;
        }

        if (isInstance(adventureBackpackItemClass, item)) {
            return BackpackKind.ADVENTURE;
        }

        if (isInstance(bradsBackpackBaseClass, item)) {
            if (isInstance(bradsWorkbenchBackpackClass, item)) {
                return BackpackKind.BRADS_WORKBENCH;
            }
            if (isEnderBackpack(stack)) {
                return BackpackKind.BRADS_ENDER;
            }
            return BackpackKind.BRADS;
        }

        if (isInstance(forestryBackpackItemClass, item)) {
            return BackpackKind.FORESTRY;
        }

        return null;
    }

    private static boolean isEnderBackpack(ItemStack stack) {
        String name = stack.getUnlocalizedName();
        return name != null && name.toLowerCase()
            .contains("ender");
    }

    private static boolean isInstance(Class<?> type, Object object) {
        return type != null && type.isInstance(object);
    }

    private static Class<?> findClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    public boolean hasSupportedModLoaded() {
        return Loader.isModLoaded("adventurebackpack") || Loader.isModLoaded("Backpack")
            || Loader.isModLoaded("backpack")
            || Loader.isModLoaded("Forestry");
    }
}
