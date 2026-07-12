package com.hepdd.backpackenhance.mixin;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hepdd.backpackenhance.server.overlay.OverlayShiftInHandler;

/**
 * Protocol-level shift-click intercept: when the overlay is expanded, routes machine/chest
 * (and standalone-player-inventory) shifts into the overlay. Player-bar shift while a chest or
 * machine is open is left to vanilla. Does not depend on per-mod transfer overrides.
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class MNetHandlerPlayServer {

    @Shadow
    public EntityPlayerMP playerEntity;

    @Inject(method = "processClickWindow", at = @At("HEAD"), cancellable = true)
    private void backpackenhance$shiftPrioritizeOverlay(C0EPacketClickWindow packet, CallbackInfo ci) {
        if (this.playerEntity == null || packet == null) {
            return;
        }
        // mode 1 = shift / quick-move in 1.7.10
        if (packet.func_149542_h() != 1) {
            return;
        }
        if (this.playerEntity.openContainer == null
            || this.playerEntity.openContainer.windowId != packet.func_149548_c()
            || !this.playerEntity.openContainer.isPlayerNotUsingContainer(this.playerEntity)) {
            return;
        }

        int slotId = packet.func_149544_d();
        if (slotId < 0 || slotId >= this.playerEntity.openContainer.inventorySlots.size()) {
            return;
        }

        Slot slot;
        try {
            slot = this.playerEntity.openContainer.getSlot(slotId);
        } catch (Throwable t) {
            return;
        }

        if (!OverlayShiftInHandler.tryShiftIntoOverlay(this.playerEntity, slot)) {
            return;
        }

        // Handled by overlay — skip container.slotClick and finish the click transaction cleanly.
        this.playerEntity.func_143004_u();
        this.playerEntity.playerNetServerHandler
            .sendPacket(new S32PacketConfirmTransaction(packet.func_149548_c(), packet.func_149547_f(), true));
        this.playerEntity.isChangingQuantityOnly = true;
        this.playerEntity.openContainer.detectAndSendChanges();
        this.playerEntity.updateHeldItem();
        this.playerEntity.isChangingQuantityOnly = false;
        ci.cancel();
    }
}
