package com.hepdd.backpackenhance.mixin;

import java.util.List;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hepdd.backpackenhance.integration.nei.OverlayNeiSupport;

/**
 * Overlay NEI integration kept deliberately thin so native multi-step auto-craft is not broken:
 * <ul>
 * <li>count active-tab items in {@code findInventoryQuantities}</li>
 * <li>before {@code moveIngredients}, supply shortfall from the active tab</li>
 * </ul>
 * Auto-craft pulls into the player inventory and lets NEI run its original craft/clear loop.
 * Manual + fill still pre-places into craft-matrix slots.
 */
@Pseudo
@Mixin(targets = "codechicken.nei.recipe.DefaultOverlayHandler", remap = false)
public abstract class MNeiDefaultOverlayHandler {

    @Inject(method = "moveIngredients", at = @At("HEAD"), require = 0)
    private void backpackenhance$prefillFromOverlay(GuiContainer gui, List assignedIngredients, int multiplier,
        CallbackInfo ci) {
        OverlayNeiSupport.prefillCraftSlotsFromOverlay(gui, assignedIngredients, multiplier);
    }

    @Inject(method = "findInventoryQuantities", at = @At("RETURN"), require = 0)
    private void backpackenhance$addOverlayQuantities(GuiContainer gui, List ingredStacks, CallbackInfo ci) {
        if (ingredStacks == null || !OverlayNeiSupport.hasOverlayItems()) {
            return;
        }
        for (Object object : ingredStacks) {
            if (object == null) {
                continue;
            }
            try {
                ItemStack match = (ItemStack) object.getClass()
                    .getField("stack")
                    .get(object);
                OverlayNeiSupport.addOverlayAmountsToDistributed(match, object);
            } catch (ReflectiveOperationException ignored) {
                // ignore NEI shape changes
            } catch (ClassCastException ignored) {
                // ignore
            }
        }
    }
}
