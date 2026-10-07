package com.duckerquacker.lastaid.mixin;

import com.duckerquacker.lastaid.common.damagesystem.PlayerDamageModel;
import com.duckerquacker.lastaid.common.util.CommonUtils;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class PlayerDimensionsMixin {

    @Inject(method = "getDimensions", at = @At("HEAD"), cancellable = true)
    private void lastaid$getDimensions(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if ((Object) this instanceof Player player && !player.isPassenger()) {
            if (CommonUtils.getExistingDamageModel(player) instanceof PlayerDamageModel playerDamageModel
                    && playerDamageModel.isUnconscious()) {
                cir.setReturnValue(PlayerDamageModel.getUnconsciousDimensions(playerDamageModel.shouldUseCrampedUnconsciousDimensions(player)));
            }
        }
    }
}
