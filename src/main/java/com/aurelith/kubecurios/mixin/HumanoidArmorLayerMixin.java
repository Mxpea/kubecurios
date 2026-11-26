package com.aurelith.kubecurios.mixin;

import com.aurelith.kubecurios.CurioArmorRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(HumanoidArmorLayer.class)
abstract class HumanoidArmorLayerMixin {
    @ModifyVariable(method = "renderArmorPiece", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack kubecurios$selectArmorAppearance(ItemStack vanilla, PoseStack pose, SubmitNodeCollector collector,
            ItemStack original, EquipmentSlot slot, int light, HumanoidRenderState state) {
        return CurioArmorRenderState.armorFor(vanilla, slot, state);
    }
}
