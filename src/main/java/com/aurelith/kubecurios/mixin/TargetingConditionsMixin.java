package com.aurelith.kubecurios.mixin;

import com.aurelith.kubecurios.CurioAggro;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetingConditions.class)
abstract class TargetingConditionsMixin {
    @Shadow @Final private boolean isCombat;
    @Shadow private boolean testInvisible;

    @Inject(method = "test", at = @At("RETURN"), cancellable = true)
    private void kubecurios$filterProactiveTargets(ServerLevel level, @Nullable LivingEntity observer,
            LivingEntity target, CallbackInfoReturnable<Boolean> result) {
        if (result.getReturnValueZ() && isCombat && testInvisible && CurioAggro.ignoresProactiveTargeting(level, observer, target)) {
            result.setReturnValue(false);
        }
    }
}
