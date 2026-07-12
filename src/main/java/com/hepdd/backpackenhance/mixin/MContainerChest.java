package com.hepdd.backpackenhance.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.backpackenhance.server.overlay.OverlayShiftInHandler;

/**
 * ContainerChest overrides {@code transferStackInSlot} (does not call super). Intercept there so
 * prioritize-backpack runs for chest shift.
 */
@Mixin(ContainerChest.class)
public abstract class MContainerChest {

    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$shiftIntoOverlay(EntityPlayer player, int index,
        CallbackInfoReturnable<ItemStack> cir) {
        if (player == null || index < 0) {
            return;
        }

        Container self = (Container) (Object) this;
        if (index >= self.inventorySlots.size()) {
            return;
        }

        Slot slot = self.getSlot(index);
        if (OverlayShiftInHandler.handleShiftClick(player, slot)) {
            cir.setReturnValue(null);
        }
    }
}
