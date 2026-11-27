package com.aurelith.kubecurios;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class MyCuriosUtils {
    private MyCuriosUtils() {}

    /** Checks active functional slots only, not cosmetics. */
    public static boolean isEquipped(LivingEntity entity, CurioSlot slot) {
        return CurioEquipment.hasActive(entity, slot.id());
    }

    public static boolean setAggro(@Nullable LivingEntity mob, @Nullable LivingEntity target) {
        return CurioAggro.set(mob, target);
    }

    public static boolean clearAggro(@Nullable LivingEntity mob, @Nullable LivingEntity target) {
        return CurioAggro.clear(mob, target);
    }

    /** Returns null for an empty stack or an unregistered item. */
    public static @Nullable String itemId(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? null : id.toString();
    }
}
