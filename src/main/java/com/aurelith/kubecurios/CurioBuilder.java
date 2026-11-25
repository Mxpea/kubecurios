package com.aurelith.kubecurios;

import dev.latvian.mods.kubejs.item.ItemBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CurioAttributeModifiers;
import top.theillusivec4.curios.api.CuriosDataComponents;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class CurioBuilder extends ItemBuilder {
    private final Set<String> slots = new LinkedHashSet<>();
    private final List<AttributeSpec> attributes = new ArrayList<>();
    private Consumer<EquipContext> equipCallback;
    private Consumer<EquipContext> unequipCallback;
    private @Nullable CurioCombatEffects.HurtTrigger hurtTrigger;
    private double incomingDamagePercent;
    private double outgoingDamagePercent;
    private final Map<CurioCombatEffects.DamageFilter, Double> damageByType = new LinkedHashMap<>();
    private boolean hideAttributes;

    public CurioBuilder(Identifier id) {
        super(id);
        maxStackSize(1);
    }

    public CurioBuilder slot(CurioSlot slot) {
        slots.add(slot.id());
        return this;
    }

    public CurioBuilder attribute(String attribute, double amount, String operation) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Non-finite Curio attribute amount for " + id);
        }
        var op = switch (operation) {
            case "addition", "add_value" -> AttributeModifier.Operation.ADD_VALUE;
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> throw new IllegalArgumentException("Unknown Curio attribute operation: " + operation);
        };
        attributes.add(new AttributeSpec(Identifier.parse(attribute), amount, op));
        return this;
    }

    public CurioBuilder onEquip(Consumer<EquipContext> callback) {
        this.equipCallback = callback;
        return this;
    }

    public CurioBuilder onUnequip(Consumer<EquipContext> callback) {
        this.unequipCallback = callback;
        return this;
    }

    public CurioBuilder onHurtCommand(String command, int cooldownTicks) {
        return onHurtCommand(command, cooldownTicks, "self");
    }

    public CurioBuilder onHurtCommand(String command, int cooldownTicks, String executor) {
        return onHurtCommand(command, cooldownTicks, executor, 100);
    }

    public CurioBuilder onHurtCommand(String command, int cooldownTicks, double chancePercent) {
        return onHurtCommand(command, cooldownTicks, "self", chancePercent);
    }

    public CurioBuilder onHurtCommand(String command, int cooldownTicks, String executor, double chancePercent) {
        String line = command.strip();
        if (command.contains("\n") || command.contains("\r") || line.isEmpty() || line.equals("/")) {
            throw new IllegalArgumentException("Expected one nonempty Curio command for " + id);
        }
        requireCooldown(cooldownTicks);
        if (!Double.isFinite(chancePercent) || chancePercent < 0 || chancePercent > 100) {
            throw new IllegalArgumentException("Curio command chance must be between 0 and 100 for " + id);
        }
        hurtTrigger = switch (executor) {
            case "self" -> new CurioCombatEffects.HurtTrigger(cooldownTicks, chancePercent,
                    (wearer, attacker) -> CurioCombatEffects.runCommand(wearer, line));
            case "attacker" -> new CurioCombatEffects.HurtTrigger(cooldownTicks, chancePercent,
                    (wearer, attacker) -> CurioCombatEffects.runCommand(attacker, line));
            default -> throw new IllegalArgumentException("Curio command executor must be 'self' or 'attacker' for " + id);
        };
        return this;
    }

    /** Existing scripts can migrate to onHurtCommand. */
    @Deprecated
    public CurioBuilder resistanceOnHurt(int durationTicks, int cooldownTicks) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("Curio resistance duration must be positive for " + id);
        }
        requireCooldown(cooldownTicks);
        hurtTrigger = new CurioCombatEffects.HurtTrigger(cooldownTicks, 100,
                (wearer, attacker) -> wearer.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, durationTicks)));
        return this;
    }

    /** Existing scripts can migrate to outgoingDamagePercent. */
    @Deprecated
    public CurioBuilder damageBonusPercent(double percent) {
        return outgoingDamagePercent(percent);
    }

    public CurioBuilder incomingDamagePercent(double percent) {
        incomingDamagePercent = requireDamagePercent(percent);
        return this;
    }

    public CurioBuilder outgoingDamagePercent(double percent) {
        outgoingDamagePercent = requireDamagePercent(percent);
        return this;
    }

    public CurioBuilder incomingDamageTypePercent(String typeOrTag, double percent) {
        requireDamagePercent(percent);
        var filter = CurioCombatEffects.DamageFilter.parse(typeOrTag);
        if (percent == 0) {
            damageByType.remove(filter);
        } else {
            damageByType.put(filter, percent);
        }
        return this;
    }

    public CurioBuilder armor(double amount) {
        return attribute("minecraft:armor", amount, "add_value");
    }

    public CurioBuilder armorToughness(double amount) {
        return attribute("minecraft:armor_toughness", amount, "add_value");
    }

    public CurioBuilder hideAttribute() {
        hideAttributes = true;
        return this;
    }

    private void requireCooldown(int ticks) {
        if (ticks <= 0) {
            throw new IllegalArgumentException("Curio hurt cooldown must be positive for " + id);
        }
    }

    private double requireDamagePercent(double percent) {
        if (!Double.isFinite(percent) || percent < -100) {
            throw new IllegalArgumentException("Curio damage percent must be finite and at least -100 for " + id);
        }
        return percent;
    }

    @Override
    public Item createObject() {
        if (slots.isEmpty()) {
            throw new IllegalStateException("Specify at least one Curios slot for " + id);
        }
        Item.Properties properties = createItemProperties();
        if (!attributes.isEmpty()) {
            var modifiers = CurioAttributeModifiers.builder();
            String[] slotIds = slots.toArray(String[]::new);
            for (int i = 0; i < attributes.size(); i++) {
                var spec = attributes.get(i);
                var attribute = BuiltInRegistries.ATTRIBUTE.get(spec.id()).orElseThrow(
                        () -> new IllegalArgumentException("Unknown attribute " + spec.id() + " for " + id));
                var modifierId = id.withPrefix("curio/").withSuffix("/" + i);
                modifiers.addModifier(attribute, new AttributeModifier(modifierId, spec.amount(), spec.operation()), slotIds);
            }
            properties.component(CuriosDataComponents.ATTRIBUTE_MODIFIERS, modifiers.build().withTooltip(!hideAttributes));
        }
        var combatEffects = hurtTrigger == null && incomingDamagePercent == 0 && outgoingDamagePercent == 0 && damageByType.isEmpty()
                ? null : new CurioCombatEffects(Set.copyOf(slots), hurtTrigger, incomingDamagePercent,
                        outgoingDamagePercent, new LinkedHashMap<>(damageByType));
        var combatTooltips = hideAttributes || combatEffects == null ? List.<Component>of() : combatEffects.tooltips();
        Item item = new ScriptedCurioItem(properties, equipCallback, unequipCallback, combatEffects, combatTooltips, hideAttributes);
        slots.forEach(slot -> CurioSlotWhitelist.allow(slot, id));
        return item;
    }

    private record AttributeSpec(Identifier id, double amount, AttributeModifier.Operation operation) {}

    /** Java-owned item ID and slot data; scripts do not have to inspect ItemStack wrappers. */
    public record EquipContext(LivingEntity entity, String slot, int index, String itemId) {}

    static final class ScriptedCurioItem extends Item implements ICurioItem {
        private final Consumer<EquipContext> equipCallback;
        private final Consumer<EquipContext> unequipCallback;
        private final @Nullable CurioCombatEffects combatEffects;
        private final List<Component> combatTooltips;
        private final boolean hideAttributes;

        private ScriptedCurioItem(Properties properties, Consumer<EquipContext> equipCallback,
                                  Consumer<EquipContext> unequipCallback, @Nullable CurioCombatEffects combatEffects,
                                  List<Component> combatTooltips, boolean hideAttributes) {
            super(properties);
            this.equipCallback = equipCallback;
            this.unequipCallback = unequipCallback;
            this.combatEffects = combatEffects;
            this.combatTooltips = combatTooltips;
            this.hideAttributes = hideAttributes;
        }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                    Consumer<Component> builder, TooltipFlag flag) {
            super.appendHoverText(stack, context, display, builder, flag);
            combatTooltips.forEach(builder);
        }

        @Override
        public List<Component> getAttributesTooltip(List<Component> tooltips, TooltipContext context, ItemStack stack) {
            // Curios 15 builds these lines without checking the component's showInTooltip flag.
            return hideAttributes ? List.of() : ICurioItem.super.getAttributesTooltip(tooltips, context, stack);
        }

        @Nullable CurioCombatEffects combatEffects() {
            return combatEffects;
        }

        @Override
        public void onEquip(SlotContext context, ItemStack previous, ItemStack stack) {
            invokeCallback("onEquip", equipCallback, context, stack);
        }

        @Override
        public void onUnequip(SlotContext context, ItemStack next, ItemStack stack) {
            invokeCallback("onUnequip", unequipCallback, context, stack);
        }

        private void invokeCallback(String name, Consumer<EquipContext> callback, SlotContext context, ItemStack stack) {
            var entity = context.entity();
            if (callback == null || entity == null || entity.level().isClientSide() || context.cosmetic()) {
                return;
            }
            String itemId = MyCuriosUtils.itemId(stack);
            if (itemId == null) {
                return;
            }
            try {
                callback.accept(new EquipContext(entity, context.identifier(), context.index(), itemId));
            } catch (RuntimeException ex) {
                KubeCurios.LOGGER.error("Curio {} failed for {} in {}[{}]", name, itemId, context.identifier(), context.index(), ex);
            }
        }
    }
}
