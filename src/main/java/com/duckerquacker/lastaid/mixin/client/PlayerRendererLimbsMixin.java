package com.duckerquacker.lastaid.mixin.client;

import com.duckerquacker.lastaid.client.medical.LimbRenderHelper;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides amputated limbs. setModelProperties resets visibility for every player each frame, so we only hide. */
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererLimbsMixin {
   @Inject(method = "setModelProperties(Lnet/minecraft/client/player/AbstractClientPlayer;)V", at = @At("TAIL"))
   private void lastaid$hideAmputatedLimbs(AbstractClientPlayer player, CallbackInfo ci) {
      LimbRenderHelper.hideAmputated(((PlayerRenderer)(Object)this).getModel(), player);
      com.duckerquacker.lastaid.client.medical.CarryClient.poseCarrier(((PlayerRenderer)(Object)this).getModel(), player);
   }
}
