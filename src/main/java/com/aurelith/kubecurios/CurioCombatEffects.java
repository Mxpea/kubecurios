package com.aurelith.kubecurios;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jetbrains.annotations.Nullable;

record CurioCombatEffects(@Nullable HurtTrigger hurtTrigger,
                          double incomingPercent, double outgoingPercent, Map<DamageFilter, Double> damageByType) {
    // Keep cooldowns written by scripts using resistanceOnHurt.
    private static final String HURT_COOLDOWNS = "kubecurios:resistance_cooldowns";
    private static final String TOOLTIP = "kubecurios.tooltip.damage.";

    record HurtTrigger(int cooldownTicks, double chancePercent, BiConsumer<LivingEntity, LivingEntity> action) {
        void tryRun(LivingEntity wearer, LivingEntity attacker, String itemId, long gameTime) {
            if (chancePercent == 0) {
                return;
            }
            var data = wearer.getPersistentData();
            var deadlines = data.getCompoundOrEmpty(HURT_COOLDOWNS);
            if (gameTime < deadlines.getLongOr(itemId, Long.MIN_VALUE)) {
                return;
            }
            if (chancePercent < 100 && wearer.getRandom().nextDouble() * 100 >= chancePercent) {
                return;
            }
            deadlines.putLong(itemId, gameTime + cooldownTicks);
            data.put(HURT_COOLDOWNS, deadlines);
            action.accept(wearer, attacker);
        }
    }

    record DamageFilter(Identifier id, boolean tag) {
        static DamageFilter parse(String value) {
            boolean tag = value.startsWith("#");
            return new DamageFilter(Identifier.parse(tag ? value.substring(1) : value), tag);
        }

        boolean matches(DamageSource source) {
            return tag ? source.is(TagKey.create(Registries.DAMAGE_TYPE, id))
                    : source.is(ResourceKey.create(Registries.DAMAGE_TYPE, id));
        }

        Component label() {
            String key = id.toLanguageKey(tag ? "kubecurios.damage_tag" : "kubecurios.damage_type");
            return Component.translatableWithFallback(key, (tag ? "#" : "") + id);
        }
    }

    static void runCommand(LivingEntity executor, String command) {
        if (executor.level() instanceof ServerLevel level) {
            var source = executor.createCommandSourceStackForNameResolution(level)
                    .withPermission(LevelBasedPermissionSet.GAMEMASTER);
            level.getServer().getCommands().performPrefixedCommand(source, command);
        }
    }

    static void modifyDamage(LivingDamageEvent.Pre event) {
        var victim = event.getEntity();
        if (!(event.getNewDamage() > 0) || !(victim.level() instanceof ServerLevel)) {
            return;
        }
        var source = event.getSource();
        var attacker = source.getEntity() instanceof LivingEntity living ? living : null;
        var damage = new CurioScriptRuntime.Damage(attacker, victim, source, event.getNewDamage());
        var outgoing = attacker != null && attacker != victim ? CurioEquipment.snapshot(attacker)
                : Map.<String, CurioEquipment.Equipped>of();
        var incoming = CurioEquipment.snapshot(victim);
        double multiplier = 1.0;
        for (var item : outgoing.values()) {
            var effects = item.combatEffects();
            if (effects != null) {
                multiplier = multiply(multiplier, factor(effects.outgoingPercent));
            }
        }
        for (var item : incoming.values()) {
            var effects = item.combatEffects();
            if (effects != null) {
                multiplier = multiply(multiplier, factor(effects.incomingPercent));
                for (var rule : effects.damageByType.entrySet()) {
                    if (rule.getKey().matches(source)) {
                        multiplier = multiply(multiplier, factor(rule.getValue()));
                    }
                }
            }
        }
        if (attacker != null && attacker != victim) {
            multiplier = CurioScriptRuntime.multiplyDamage(multiplier, CurioScripts.Hook.OUTGOING, attacker, damage, outgoing);
        }
        multiplier = CurioScriptRuntime.multiplyDamage(multiplier, CurioScripts.Hook.INCOMING, victim, damage, incoming);
        if (multiplier != 1.0) {
            event.setNewDamage((float) Math.min(Float.MAX_VALUE, damage.amount() * multiplier));
        }
    }

    private static double factor(double percent) {
        return 1.0 + percent / 100.0;
    }

    static double multiply(double left, double right) {
        return left == 0 || right == 0 ? 0 : left * right;
    }

    static void onHurt(LivingDamageEvent.Post event) {
        var wearer = event.getEntity();
        if (!(event.getHealthDamage() > 0) || !(wearer.level() instanceof ServerLevel level)
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == wearer) {
            return;
        }
        var damage = new CurioScriptRuntime.Damage(attacker, wearer, event.getSource(), event.getHealthDamage());
        if (wearer.isAlive()) {
            long gameTime = level.getServer().overworld().getGameTime();
            CurioEquipment.snapshot(wearer).forEach((itemId, item) -> {
                var effects = item.combatEffects();
                if (effects != null && effects.hurtTrigger != null) {
                    effects.hurtTrigger.tryRun(wearer, attacker, itemId, gameTime);
                }
            });
            if (wearer.isAlive()) {
                CurioScriptRuntime.notifyDamage(CurioScripts.Hook.HURT, wearer, damage, CurioEquipment.snapshot(wearer));
            }
        }
        CurioScriptRuntime.notifyDamage(CurioScripts.Hook.HIT, attacker, damage, CurioEquipment.snapshot(attacker));
    }

    List<Component> tooltips() {
        var lines = new ArrayList<Component>();
        if (incomingPercent != 0) {
            lines.add(damageLine("incoming", incomingPercent, incomingPercent > 0));
        }
        if (outgoingPercent != 0) {
            lines.add(damageLine("outgoing", outgoingPercent, outgoingPercent < 0));
        }
        damageByType.forEach((filter, percent) ->
                lines.add(damageLine("typed", percent, percent > 0, filter.label())));
        return List.copyOf(lines);
    }

    private static Component damageLine(String category, double percent, boolean penalty, Object... prefix) {
        Object[] arguments = new Object[prefix.length + 1];
        System.arraycopy(prefix, 0, arguments, 0, prefix.length);
        arguments[prefix.length] = BigDecimal.valueOf(Math.abs(percent)).stripTrailingZeros().toPlainString() + "%";
        return Component.translatable(TOOLTIP + category + (percent > 0 ? ".increase" : ".decrease"), arguments)
                .withStyle(penalty ? ChatFormatting.RED : ChatFormatting.BLUE);
    }
}
