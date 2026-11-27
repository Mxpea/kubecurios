package com.aurelith.kubecurios;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

final class CurioEquipment {
    record Slot(String id, int index, ItemStack stack) {
        boolean comesBefore(Slot other) {
            int order = id.compareTo(other.id);
            return order < 0 || (order == 0 && index < other.index);
        }

        void damage(LivingEntity wearer, int amount) {
            if (!(wearer.level() instanceof ServerLevel level) || wearer.isRemoved() || !stack.isDamageableItem()) {
                return;
            }
            var handler = currentHandler(wearer);
            if (handler == null) {
                return;
            }
            var stacks = handler.getStacks();
            var renders = handler.getRenders();
            var context = new SlotContext(id, wearer, index, false, index < renders.size() && renders.get(index));
            // Limit the addition without bypassing vanilla's durability enchantment processing.
            stack.hurtAndBreak(Math.min(amount, Integer.MAX_VALUE - stack.getDamageValue()), level, wearer,
                    item -> CuriosApi.broadcastCurioBreakEvent(context));
            if (stack.isEmpty() && index < stacks.getSlots() && stacks.getStackInSlot(index) == stack) {
                stacks.setStackInSlot(index, ItemStack.EMPTY);
            }
        }

        boolean isCurrent(LivingEntity wearer) {
            return currentHandler(wearer) != null;
        }

        private @Nullable ICurioStacksHandler currentHandler(LivingEntity wearer) {
            var inventory = wearer.getCapability(CuriosCapability.INVENTORY);
            var handler = inventory == null ? null : inventory.getCurios().get(id);
            if (handler == null || stack.isEmpty()) {
                return null;
            }
            var stacks = handler.getStacks();
            return index < stacks.getSlots() && stacks.getStackInSlot(index) == stack && inventory.isSlotActive(id, index)
                    ? handler : null;
        }
    }

    record Equipped(Slot source, CurioBuilder.ScriptedCurioItem item) {
        @Nullable CurioCombatEffects combatEffects() { return item.combatEffects(); }
        @Nullable CurioScripts.Definition script() { return item.script(); }
    }

    static boolean hasActive(LivingEntity entity, String slot) {
        return anyActive(entity, slot, source -> true);
    }

    static boolean ignoresProactiveTargeting(LivingEntity wearer, Mob observer) {
        return anyActive(wearer, null, source -> source.stack.getItem() instanceof CurioBuilder.ScriptedCurioItem item
                && item.appliesTo(source.id) && item.aggroRules().ignored().stream().anyMatch(filter -> filter.matches(observer)));
    }

    static @Nullable Equipped provocationSource(LivingEntity wearer) {
        Equipped best = null;
        double bestRadius = 0;
        for (var curio : snapshot(wearer, item -> item.aggroRules().provokeRadius() > 0).values()) {
            double radius = curio.item.aggroRules().provokeRadius();
            if (best == null || radius > bestRadius || (radius == bestRadius && curio.source.comesBefore(best.source))) {
                best = curio;
                bestRadius = radius;
            }
        }
        return best;
    }

    static int countOtherEquipped(LivingEntity entity, Slot excluded, Identifier id) {
        var tag = TagKey.create(Registries.ITEM, id);
        int[] count = {0};
        anyActive(entity, null, slot -> {
            if (!(slot.id.equals(excluded.id) && slot.index == excluded.index) && slot.stack.is(tag)) {
                count[0]++;
            }
            return false;
        });
        return count[0];
    }

    private static boolean anyActive(LivingEntity entity, @Nullable String slotFilter, Predicate<Slot> test) {
        var inventory = entity.getCapability(CuriosCapability.INVENTORY);
        if (inventory == null) {
            return false;
        }
        for (var entry : inventory.getCurios().entrySet()) {
            String slot = entry.getKey();
            if (slotFilter != null && !slot.equals(slotFilter)) {
                continue;
            }
            var stacks = entry.getValue().getStacks();
            for (int index = 0; index < stacks.getSlots(); index++) {
                var stack = stacks.getStackInSlot(index);
                if (!stack.isEmpty() && inventory.isSlotActive(slot, index) && test.test(new Slot(slot, index, stack))) {
                    return true;
                }
            }
        }
        return false;
    }

    static Map<String, Equipped> snapshot(LivingEntity wearer) {
        return snapshot(wearer, item -> item.combatEffects() != null || item.script() != null);
    }

    static Map<String, Equipped> snapshot(LivingEntity wearer, Predicate<CurioBuilder.ScriptedCurioItem> filter) {
        var items = new LinkedHashMap<String, Equipped>();
        anyActive(wearer, null, slot -> {
            if (slot.stack.getItem() instanceof CurioBuilder.ScriptedCurioItem item && item.appliesTo(slot.id)
                    && filter.test(item)) {
                var id = MyCuriosUtils.itemId(slot.stack);
                if (id != null) {
                    // Replacement does not change the traversal order used by built-in effects.
                    items.merge(id, new Equipped(slot, item), (first, next) ->
                            next.source.comesBefore(first.source) ? next : first);
                }
            }
            return false;
        });
        return items;
    }
}
