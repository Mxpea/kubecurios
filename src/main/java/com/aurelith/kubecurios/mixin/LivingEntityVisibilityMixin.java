package com.aurelith.kubecurios.mixin;

import com.aurelith.kubecurios.CurioDetection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityVisibilityMixin {
    @Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
    private void kubecurios$applyDetectionRange(@Nullable Entity observer, CallbackInfoReturnable<Double> result) {
        if ((Object) this instanceof ServerPlayer player) {
            result.setReturnValue(CurioDetection.modifyVisibility(result.getReturnValueD(), player, observer));
        }
    }
}
