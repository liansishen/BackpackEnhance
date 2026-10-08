package com.hepdd.backpackenhance.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hepdd.backpackenhance.client.overlay.OverlayClientState;
import com.hepdd.backpackenhance.client.overlay.OverlayController;
import com.hepdd.backpackenhance.server.overlay.OverlaySlotUtil;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Client: while the overlay is expanded, suppress local {@code slotClick} for shift-clicks that
 * the server will route into the overlay (machine/chest slots; player inventory only on
 * standalone player GUI). Still sends {@link C0EPacketClickWindow} for the protocol handler.
 */
@SideOnly(Side.CLIENT)
@Mixin(PlayerControllerMP.class)
public abstract class MPlayerControllerMP {

    @Shadow
    @Final
    private Minecraft mc;

    @Shadow
    @Final
    private NetHandlerPlayClient netClientHandler;

    @Inject(method = "windowClick", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$suppressShiftPredict(int windowId, int slotId, int mouseButton, int mode,
        EntityPlayer player, CallbackInfoReturnable<ItemStack> cir) {
        OverlayClientState.invalidateCursor();
        // mode 1 = shift
        if (mode != 1 || slotId < 0 || player == null || player.openContainer == null) {
            return;
        }
        if (!OverlayController.hasActiveOverlaySession()) {
            return;
        }
        if (player.openContainer.windowId != windowId) {
            return;
        }
        if (slotId >= player.openContainer.inventorySlots.size()) {
            return;
        }

        Slot slot;
        try {
            slot = player.openContainer.getSlot(slotId);
        } catch (Throwable t) {
            return;
        }
        if (slot == null || !slot.getHasStack() || !OverlaySlotUtil.shouldPrioritizeShiftFrom(player, slot)) {
            return;
        }

        short txn = player.openContainer.getNextTransactionID(player.inventory);
        ItemStack expected = null;
        this.netClientHandler
            .addToSendQueue(new C0EPacketClickWindow(windowId, slotId, mouseButton, mode, expected, txn));
        cir.setReturnValue(null);
    }
}
