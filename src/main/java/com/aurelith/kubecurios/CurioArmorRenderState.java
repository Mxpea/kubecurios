package com.aurelith.kubecurios;

import com.google.common.reflect.TypeToken;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import top.theillusivec4.curios.api.CuriosCapability;

@EventBusSubscriber(modid = KubeCurios.MODID, value = Dist.CLIENT)
public final class CurioArmorRenderState {
    private static final ContextKey<Map<EquipmentSlot, ItemStack>> ARMOR =
            new ContextKey<>(Identifier.fromNamespaceAndPath(KubeCurios.MODID, "curio_armor"));

    private record RenderPiece(ItemStack stack, CurioArmorAppearance appearance, String slot, int index, boolean cosmetic) {
        boolean precedes(RenderPiece other) {
            if (cosmetic != other.cosmetic) return cosmetic;
            int bySlot = slot.compareTo(other.slot);
            return bySlot < 0 || (bySlot == 0 && index < other.index);
        }
    }

    private CurioArmorRenderState() {}

    @SubscribeEvent
    public static void register(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {},
                CurioArmorRenderState::extract);
    }

    private static void extract(LivingEntity entity, LivingEntityRenderState state) {
        if (!(state instanceof HumanoidRenderState)) return;
        var inventory = entity.getCapability(CuriosCapability.INVENTORY);
        if (inventory == null) return;

        EnumMap<EquipmentSlot, RenderPiece> selected = null;
        for (var entry : inventory.getCurios().entrySet()) {
            String slot = entry.getKey();
            var handler = entry.getValue();
            var functional = handler.getStacks();
            var cosmetics = handler.getCosmeticStacks();
            var renders = handler.getRenders();
            for (int index = 0; index < functional.getSlots(); index++) {
                var stack = index < cosmetics.getSlots() ? cosmetics.getStackInSlot(index) : ItemStack.EMPTY;
                boolean cosmetic = !stack.isEmpty();
                if (!cosmetic) {
                    if (index >= renders.size() || !renders.get(index) || !inventory.isSlotActive(slot, index)) continue;
                    stack = functional.getStackInSlot(index);
                }
                if (stack.isEmpty() || !(stack.getItem() instanceof CurioBuilder.ScriptedCurioItem item)
                        || !item.appliesTo(slot) || item.armorAppearances().isEmpty()) continue;
                if (selected == null) selected = new EnumMap<>(EquipmentSlot.class);
                for (var appearance : item.armorAppearances().values()) {
                    var candidate = new RenderPiece(stack, appearance, slot, index, cosmetic);
                    selected.merge(appearance.slot(), candidate, (old, next) -> next.precedes(old) ? next : old);
                }
            }
        }
        if (selected == null) return;
        var rendered = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
        selected.forEach((slot, piece) -> {
            // Only the render snapshot is equippable. Never mutate the inventory stack.
            var copy = piece.stack.copy();
            copy.set(DataComponents.EQUIPPABLE, Equippable.builder(slot).setAsset(piece.appearance.asset()).build());
            rendered.put(slot, copy);
        });
        state.setRenderData(ARMOR, rendered);
    }

    public static ItemStack armorFor(ItemStack vanilla, EquipmentSlot slot, HumanoidRenderState state) {
        var selected = state.getRenderData(ARMOR);
        return selected == null || HumanoidArmorLayer.shouldRender(vanilla, slot) ? vanilla : selected.getOrDefault(slot, vanilla);
    }
}
