package com.aurelith.kubecurios;

import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;

public final class CurioSlotWhitelist {
    private static final Identifier VALIDATOR = Identifier.fromNamespaceAndPath(KubeCurios.MODID, "whitelist");
    private static final ConcurrentMap<String, Set<String>> ITEMS_BY_SLOT = new ConcurrentHashMap<>();

    private CurioSlotWhitelist() {}

    static void registerPredicate() {
        CuriosSlotTypes.registerPredicate(VALIDATOR, CurioSlotWhitelist::test);
    }

    /** Gives an existing item access to a slot. Both IDs are resolved after startup. */
    public static void allow(CurioSlot slot, Identifier itemId) {
        allow(slot.id(), itemId);
    }

    static void allow(String slot, Identifier itemId) {
        ITEMS_BY_SLOT.computeIfAbsent(slot, ignored -> ConcurrentHashMap.newKeySet()).add(itemId.toString());
    }

    private static boolean test(SlotContext context, ItemStack stack) {
        Set<String> allowed = ITEMS_BY_SLOT.get(context.identifier());
        if (allowed == null) {
            return false;
        }
        String itemId = MyCuriosUtils.itemId(stack);
        return itemId != null && allowed.contains(itemId);
    }

    static void validate() {
        var slots = CuriosSlotTypes.getSlotTypes(false);
        var playerSlots = CuriosSlotTypes.getDefaultPlayerSlotTypes(false);
        for (var entry : new TreeMap<>(ITEMS_BY_SLOT).entrySet()) {
            String slotId = entry.getKey();
            var items = new TreeSet<>(entry.getValue());
            for (String itemId : items) {
                if (!BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemId))) {
                    KubeCurios.LOGGER.warn("Curios whitelist item '{}' is not registered for slot '{}'", itemId, slotId);
                }
            }

            var slot = slots.get(slotId);
            if (slot == null) {
                KubeCurios.LOGGER.warn("Curios slot '{}' is not defined (items: {})", slotId, items);
                continue;
            }
            if (!slot.getValidators().contains(VALIDATOR)) {
                KubeCurios.LOGGER.warn("Curios slot '{}' does not reference validator '{}' (items: {})", slotId, VALIDATOR, items);
            }
            if (!playerSlots.containsKey(slotId)) {
                boolean assigned = BuiltInRegistries.ENTITY_TYPE.stream()
                        .anyMatch(type -> CuriosSlotTypes.getDefaultEntitySlotTypes(type, false).containsKey(slotId));
                if (assigned) {
                    KubeCurios.LOGGER.info("Curios slot '{}' is assigned to other entities only (items: {})", slotId, items);
                } else {
                    KubeCurios.LOGGER.warn("Curios slot '{}' is not assigned to any entity (items: {})", slotId, items);
                }
            } else if (slot.getSize() == 0) {
                KubeCurios.LOGGER.info("Curios slot '{}' starts with zero slots for players (items: {})", slotId, items);
            }
        }
    }
}
