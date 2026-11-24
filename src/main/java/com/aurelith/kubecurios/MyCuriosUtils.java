package com.aurelith.kubecurios;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosCapability;

public final class MyCuriosUtils {
    private MyCuriosUtils() {}

    /** Checks active functional slots only, not cosmetics. */
    public static boolean isEquipped(LivingEntity entity, CurioSlot slot) {
        var inventory = entity.getCapability(CuriosCapability.INVENTORY);
        if (inventory == null) {
            return false;
        }
        var handler = inventory.getCurios().get(slot.id());
        if (handler == null) {
            return false;
        }
        var stacks = handler.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (inventory.isSlotActive(slot.id(), i) && !stacks.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
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
