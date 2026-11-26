package com.aurelith.kubecurios;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

public final class CurioEffects {
    record DamageMultiplier(double factor) {}
    record Attribute(Identifier id, double amount, AttributeModifier.Operation operation) {}
    record DurabilityCost(int amount) {}

    private CurioEffects() {}

    public static DamageMultiplier multiplyDamage(double factor) {
        if (!Double.isFinite(factor) || factor < 0) {
            throw new IllegalArgumentException("Damage factor must be finite and nonnegative");
        }
        return new DamageMultiplier(factor);
    }

    public static Attribute armor(double amount) {
        return attribute("minecraft:armor", amount, "add_value");
    }

    public static DurabilityCost damageDurability(double amount) {
        if (!(amount >= 1 && amount <= Integer.MAX_VALUE) || amount != Math.rint(amount)) {
            throw new IllegalArgumentException("Durability cost must be a positive integer");
        }
        return new DurabilityCost((int) amount);
    }

    public static Attribute attribute(String id, double amount, String operation) {
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("Attribute amount must be finite");
        }
        var attribute = Identifier.parse(id);
        if (!BuiltInRegistries.ATTRIBUTE.containsKey(attribute)) {
            throw new IllegalArgumentException("Unknown attribute " + attribute);
        }
        return new Attribute(attribute, amount, parseOperation(operation));
    }

    static AttributeModifier.Operation parseOperation(String operation) {
        return switch (operation) {
            case "addition", "add_value" -> AttributeModifier.Operation.ADD_VALUE;
            case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default -> throw new IllegalArgumentException("Unknown Curio attribute operation: " + operation);
        };
    }
}
