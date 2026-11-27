package com.aurelith.kubecurios.mixin;

import com.aurelith.kubecurios.CurioCooldownOverlay;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GuiGraphicsExtractor.class)
abstract class ItemCooldownOverlayMixin {
    @ModifyExpressionValue(method = "itemCooldown", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;getCooldownPercent(Lnet/minecraft/world/item/ItemStack;F)F"))
    private float kubecurios$includeSkillCooldown(float vanilla, ItemStack stack) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return Math.max(vanilla, CurioCooldownOverlay.progress(stack, partialTick));
    }
}
