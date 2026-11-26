package com.aurelith.kubecurios;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

@EventBusSubscriber(modid = KubeCurios.MODID, value = Dist.CLIENT)
public final class CurioItemRenderer implements ICurioRenderer {
    private final CurioBuilder.ScriptedCurioItem item;
    private final CurioItemAppearance appearance;

    private CurioItemRenderer(CurioBuilder.ScriptedCurioItem item, CurioItemAppearance appearance) {
        this.item = item;
        this.appearance = appearance;
    }

    @SubscribeEvent
    public static void register(FMLClientSetupEvent event) {
        event.enqueueWork(() -> BuiltInRegistries.ITEM.forEach(item -> {
            if (item instanceof CurioBuilder.ScriptedCurioItem curio && curio.itemAppearance() != null) {
                var appearance = curio.itemAppearance();
                ICurioRenderer.register(item, () -> new CurioItemRenderer(curio, appearance));
            }
        }));
    }

    @Override
    public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(
            ItemStack stack, SlotContext slot, PoseStack pose, SubmitNodeCollector collector, int light, S state,
            RenderLayerParent<S, M> parent, EntityRendererProvider.Context context, float yRot, float xRot) {
        var wearer = slot.entity();
        if (wearer == null || !item.appliesTo(slot.identifier()) || !(parent.getModel() instanceof HumanoidModel<?> humanoid)) return;
        if (!slot.cosmetic()) {
            var inventory = wearer.getCapability(CuriosCapability.INVENTORY);
            if (!slot.visible() || inventory == null || !inventory.isSlotActive(slot.identifier(), slot.index())) return;
        }
        var part = switch (appearance.anchor()) {
            case HEAD -> humanoid.head;
            case BODY -> humanoid.body;
            case LEFT_ARM -> humanoid.leftArm;
            case RIGHT_ARM -> humanoid.rightArm;
            case LEFT_LEG -> humanoid.leftLeg;
            case RIGHT_LEG -> humanoid.rightLeg;
        };
        if (!part.visible) return;

        var model = new ItemStackRenderState();
        context.getItemModelResolver().updateForLiving(model, stack, ItemDisplayContext.FIXED, wearer);
        pose.pushPose();
        try {
            humanoid.root().translateAndRotate(pose);
            part.translateAndRotate(pose);
            pose.translate(appearance.x() / 16, appearance.y() / 16, appearance.z() / 16);
            pose.mulPose(Axis.XP.rotationDegrees(180));
            pose.scale(appearance.scale(), appearance.scale(), appearance.scale());
            model.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
        } finally {
            pose.popPose();
        }
    }
}
