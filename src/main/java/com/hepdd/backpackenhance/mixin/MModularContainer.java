package com.hepdd.backpackenhance.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.backpackenhance.server.overlay.OverlayShiftInHandler;

/**
 * ModularUI2 {@code ModularContainer.slotClick} handles QUICK_MOVE without calling
 * {@code super.slotClick}, so {@link MContainer} never runs for GT shift-clicks.
 * Mirror the same prioritize-backpack intercept at HEAD here.
 */
@Pseudo
@Mixin(targets = "com.cleanroommc.modularui.screen.ModularContainer", remap = false)
public abstract class MModularContainer {

    @Inject(
        method = "slotClick(IIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;",
        at = @At("HEAD"),
        cancellable = true,
        require = 0)
    private void backpackenhance$shiftIntoOverlay(int slotId, int mouseButton, int mode, EntityPlayer player,
        CallbackInfoReturnable<ItemStack> cir) {
        // mode 1 == QUICK_MOVE / shift-click in 1.7.10
        if (mode != 1 || slotId < 0 || player == null) {
            return;
        }

        Container self = (Container) (Object) this;
        if (slotId >= self.inventorySlots.size()) {
            return;
        }

        Slot slot = self.getSlot(slotId);
        if (OverlayShiftInHandler.handleShiftClick(player, slot)) {
            cir.setReturnValue(null);
        }
    }
}
