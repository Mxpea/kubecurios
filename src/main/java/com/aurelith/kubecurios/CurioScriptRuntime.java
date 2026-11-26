package com.aurelith.kubecurios;

import dev.latvian.mods.rhino.NativeArray;
import dev.latvian.mods.rhino.Undefined;
import dev.latvian.mods.rhino.Wrapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

final class CurioScriptRuntime {
    private static final Map<MinecraftServer, ServerState> SERVERS = new IdentityHashMap<>();

    record Damage(@Nullable LivingEntity attacker, LivingEntity target, DamageSource source, float amount) {}
    private record Diagnostic(String item, Identifier script, CurioScripts.Hook hook) {}
    private record ActiveCall(LivingEntity entity, String item, CurioScripts.Hook hook) {}
    private record ModifierKey(AttributeInstance instance, Identifier id, String item) {}

    private static final class ServerState {
        final Set<Diagnostic> logged = new HashSet<>();
        final Set<ActiveCall> active = new HashSet<>();
        final Map<LivingEntity, Map<ModifierKey, AttributeModifier>> applied = new IdentityHashMap<>();
    }

    static double multiplyDamage(double multiplier, CurioScripts.Hook hook, LivingEntity wearer, Damage damage,
                                 Map<String, CurioEquipment.Equipped> equipped) {
        for (var entry : new TreeMap<>(equipped).entrySet()) {
            double factor = invoke(hook, wearer, entry.getKey(), entry.getValue(), damage, result -> {
                if (noResult(result)) {
                    return 1.0;
                }
                if (Wrapper.unwrapped(result) instanceof CurioEffects.DamageMultiplier value) {
                    return value.factor();
                }
                throw new IllegalArgumentException("Expected CurioEffects.multiplyDamage(...) or no result");
            }, 1.0);
            multiplier = CurioCombatEffects.multiply(multiplier, factor);
        }
        return multiplier;
    }

    static void notifyDamage(CurioScripts.Hook hook, LivingEntity wearer, Damage damage,
                             Map<String, CurioEquipment.Equipped> equipped) {
        new TreeMap<>(equipped).forEach((item, curio) -> runAction(hook, wearer, item, curio, damage));
    }

    private static void runAction(CurioScripts.Hook hook, LivingEntity wearer, String item,
                                  CurioEquipment.Equipped curio, @Nullable Damage damage) {
        invoke(hook, wearer, item, curio, damage, result -> {
            // Non-cost return values were previously ignored; keep existing callbacks compatible.
            if (Wrapper.unwrapped(result) instanceof CurioEffects.DurabilityCost cost) {
                curio.source().damage(wearer, cost.amount());
                if (curio.source().stack().isEmpty() && wearer.level() instanceof ServerLevel level) {
                    var equipped = CurioEquipment.snapshot(wearer);
                    if (!equipped.containsKey(item)) {
                        var state = SERVERS.get(level.getServer());
                        var applied = state == null ? null : state.applied.get(wearer);
                        if (applied != null) {
                            applied.keySet().removeIf(key -> {
                                if (!key.item.equals(item)) {
                                    return false;
                                }
                                key.instance.removeModifier(key.id);
                                return true;
                            });
                            if (applied.isEmpty()) {
                                state.applied.remove(wearer);
                            }
                        }
                    }
                }
            }
            return null;
        }, null);
    }

    static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity wearer) || !(wearer.level() instanceof ServerLevel level)) {
            return;
        }
        if (!wearer.isAlive()) {
            clear(level.getServer(), wearer);
            return;
        }
        if (wearer.tickCount % 5 != 0) {
            return;
        }
        var state = SERVERS.get(level.getServer());
        if (!CurioScripts.hasPeriodicHandlers()
                && (state == null || !state.applied.containsKey(wearer))) {
            return;
        }
        var equipped = new TreeMap<>(CurioEquipment.snapshot(wearer));
        var desired = new HashMap<ModifierKey, AttributeModifier>();
        for (var entry : equipped.entrySet()) {
            desired.putAll(invoke(CurioScripts.Hook.MODIFIERS, wearer, entry.getKey(), entry.getValue(), null,
                    result -> readModifiers(wearer, entry.getKey(), result), Map.of()));
        }
        if (wearer.level() != level || wearer.isRemoved() || !wearer.isAlive()) {
            clear(level.getServer(), wearer);
            return;
        }
        update(level.getServer(), wearer, desired);
        if (wearer.tickCount % 20 == 0) {
            equipped.forEach((item, curio) -> runAction(CurioScripts.Hook.TICK, wearer, item, curio, null));
        }
    }

    static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LivingEntity wearer) {
            clear(level.getServer(), wearer);
        }
    }

    static void onStop(ServerStoppedEvent event) {
        var state = SERVERS.remove(event.getServer());
        if (state != null) {
            state.applied.values().forEach(CurioScriptRuntime::removeModifiers);
        }
    }

    private static <T> T invoke(CurioScripts.Hook hook, LivingEntity wearer, String item, CurioEquipment.Equipped curio,
                                @Nullable Damage damage, Function<Object, T> readResult, T fallback) {
        var script = curio.script();
        if (script == null || !(wearer.level() instanceof ServerLevel level)) {
            return fallback;
        }
        var handler = script.handlers().get(hook);
        if (handler == null) {
            return fallback;
        }
        var state = SERVERS.computeIfAbsent(level.getServer(), ignored -> new ServerState());
        var key = new ActiveCall(wearer, item, hook);
        if (!state.active.add(key)) {
            return fallback;
        }
        var diagnostic = new Diagnostic(item, script.id(), hook);
        Consumer<String> report = reason -> {
            if (state.logged.add(diagnostic)) {
                KubeCurios.LOGGER.warn("Curio '{}' script '{}' callback {}: {}", item, script.id(), hook.name, reason);
            }
        };
        long ticks = CurioWearTime.get(wearer, item);
        CurioScriptContext context = damage == null ? new CurioScriptContext(wearer, item, curio.source(), ticks, report)
                : new CurioScriptContext.Combat(wearer, item, curio.source(), ticks, report, damage);
        try {
            return readResult.apply(handler.invoke(context));
        } catch (Exception exception) {
            report.accept(exception.toString());
            return fallback;
        } finally {
            context.expire();
            state.active.remove(key);
        }
    }

    private static boolean noResult(Object result) {
        return result == null || Undefined.isUndefined(result);
    }

    private record AttributeKey(Identifier id, AttributeModifier.Operation operation) {}

    private static Map<ModifierKey, AttributeModifier> readModifiers(LivingEntity wearer, String item, Object result) {
        var modifiers = new HashMap<ModifierKey, AttributeModifier>();
        if (noResult(result)) {
            return modifiers;
        }
        var seen = new HashSet<AttributeKey>();
        var itemId = Identifier.parse(item);
        if (result instanceof NativeArray array) {
            for (long i = 0; i < array.getLength(); i++) {
                readModifier(wearer, itemId, array.get(Math.toIntExact(i)), seen, modifiers);
            }
        } else {
            readModifier(wearer, itemId, result, seen, modifiers);
        }
        return modifiers;
    }

    private static void readModifier(LivingEntity wearer, Identifier item, Object result, Set<AttributeKey> seen,
                                     Map<ModifierKey, AttributeModifier> modifiers) {
        if (!(Wrapper.unwrapped(result) instanceof CurioEffects.Attribute attribute)) {
            throw new IllegalArgumentException("Expected a CurioEffects attribute declaration");
        }
        if (!seen.add(new AttributeKey(attribute.id(), attribute.operation()))) {
            throw new IllegalArgumentException("Duplicate attribute and operation: " + attribute.id() + " / " + attribute.operation());
        }
        var holder = BuiltInRegistries.ATTRIBUTE.get(attribute.id()).orElseThrow(
                () -> new IllegalArgumentException("Unknown attribute " + attribute.id()));
        var instance = wearer.getAttribute(holder);
        if (instance != null) {
            var id = Identifier.fromNamespaceAndPath(KubeCurios.MODID,
                    "script/" + item.getNamespace() + "/" + item.getPath() + "/" + attribute.operation().getSerializedName());
            modifiers.put(new ModifierKey(instance, id, item.toString()), new AttributeModifier(id, attribute.amount(), attribute.operation()));
        }
    }

    private static void update(MinecraftServer server, LivingEntity wearer, Map<ModifierKey, AttributeModifier> desired) {
        var state = SERVERS.get(server);
        var previous = state == null ? null : state.applied.get(wearer);
        if (previous != null) {
            previous.keySet().stream().filter(key -> !desired.containsKey(key)).forEach(key -> key.instance.removeModifier(key.id));
        }
        for (var entry : desired.entrySet()) {
            var key = entry.getKey();
            if (!entry.getValue().equals(key.instance.getModifier(key.id))) {
                key.instance.addOrUpdateTransientModifier(entry.getValue());
            }
        }
        if (desired.isEmpty()) {
            if (state != null) {
                state.applied.remove(wearer);
            }
        } else {
            SERVERS.computeIfAbsent(server, ignored -> new ServerState()).applied.put(wearer, desired);
        }
    }

    private static void clear(MinecraftServer server, LivingEntity wearer) {
        var state = SERVERS.get(server);
        if (state != null) {
            var removed = state.applied.remove(wearer);
            if (removed != null) {
                removeModifiers(removed);
            }
        }
    }

    private static void removeModifiers(Map<ModifierKey, AttributeModifier> modifiers) {
        modifiers.keySet().forEach(key -> key.instance.removeModifier(key.id));
    }
}
