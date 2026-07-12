package com.hepdd.backpackenhance.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.backpackenhance.server.overlay.OverlayShiftInHandler;

@Mixin(Container.class)
public abstract class MContainer {

    @Shadow
    public abstract Slot getSlot(int slotId);

    @Inject(method = "slotClick", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$shiftIntoOverlay(int slotId, int button, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        // mode 1 = quick-move/shift in 1.7.10
        if (mode != 1 || slotId < 0 || player == null) {
            return;
        }

        Slot slot;
        try {
            slot = getSlot(slotId);
        } catch (Throwable t) {
            return;
        }

        if (OverlayShiftInHandler.handleShiftClick(player, slot)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * Also intercept transferStackInSlot: some containers override it and shift always goes
     * through this method. Parent inject still helps for containers that call super.
     */
    @Inject(method = "transferStackInSlot", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$transferShiftIntoOverlay(EntityPlayer player, int index,
        CallbackInfoReturnable<ItemStack> cir) {
        if (player == null || index < 0) {
            return;
        }

        Slot slot;
        try {
            slot = getSlot(index);
        } catch (Throwable t) {
            return;
        }

        if (OverlayShiftInHandler.handleShiftClick(player, slot)) {
            cir.setReturnValue(null);
        }
    }
}
