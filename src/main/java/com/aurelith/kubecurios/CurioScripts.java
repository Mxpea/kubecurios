package com.aurelith.kubecurios;

import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Function;
import dev.latvian.mods.rhino.NativeObject;
import dev.latvian.mods.rhino.ScriptRuntime;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;

final class CurioScripts {
    enum Hook {
        OUTGOING("modifyOutgoingDamage"), INCOMING("modifyIncomingDamage"),
        HURT("onHurt"), HIT("onHit"), MODIFIERS("equipmentModifiers"), TICK("onTick");

        final String name;

        Hook(String name) {
            this.name = name;
        }
    }

    record Handler(Context context, Scriptable scope, Function function) {
        Object invoke(CurioScriptContext argument) {
            return context.callSync(function, scope, scope, new Object[]{context.wrap(scope, argument)});
        }
    }

    record Definition(Identifier id, Map<Hook, Handler> handlers) {}

    private static Map<Identifier, Definition> loading;
    private static volatile Map<Identifier, Definition> definitions = Map.of();
    private static volatile boolean periodic;

    static void begin() {
        loading = new HashMap<>();
        definitions = Map.of();
    }

    static void freeze() {
        definitions = Map.copyOf(loading);
        loading = null;
    }

    static boolean hasPeriodicHandlers() {
        return periodic;
    }

    static Definition get(Identifier id) {
        var definition = definitions.get(id);
        if (definition != null && (definition.handlers.containsKey(Hook.MODIFIERS) || definition.handlers.containsKey(Hook.TICK))) {
            periodic = true;
        }
        return definition;
    }

    static Identifier parseId(String value) {
        if (value.indexOf(':') <= 0 || value.endsWith(":")) {
            throw new IllegalArgumentException("Curio script ID requires namespace:path: " + value);
        }
        return Identifier.parse(value);
    }

    static void bind(BindingRegistry bindings) {
        var namespace = (ScriptableObject) bindings.context().newObject(bindings.scope());
        var define = new BaseFunction() {
            @Override
            public Object call(Context context, Scriptable scope, Scriptable self, Object[] args) {
                if (args.length != 2 || !(args[0] instanceof CharSequence id) || !(args[1] instanceof NativeObject handlers)) {
                    throw ScriptRuntime.typeError(context, "CurioScripts.define expects an ID and a handler object");
                }
                try {
                    register(context, id.toString(), handlers);
                } catch (IllegalArgumentException | IllegalStateException error) {
                    throw ScriptRuntime.typeError(context, error.getMessage());
                }
                return dev.latvian.mods.rhino.Undefined.INSTANCE;
            }
        };
        define.setParentScope(bindings.scope());
        namespace.defineProperty(bindings.context(), "define", define, ScriptableObject.READONLY | ScriptableObject.PERMANENT);
        bindings.add("CurioScripts", namespace);
        bindings.add("CurioEffects", CurioEffects.class);
    }

    private static void register(Context context, String name, NativeObject handlers) {
        if (loading == null) {
            throw new IllegalStateException("CurioScripts.define must be called at startup file top level");
        }
        var id = parseId(name);
        var callbacks = new EnumMap<Hook, Handler>(Hook.class);
        for (Object field : handlers.getAllIds(context)) {
            Hook hook = null;
            for (var candidate : Hook.values()) {
                if (candidate.name.equals(field)) {
                    hook = candidate;
                    break;
                }
            }
            if (hook == null) {
                throw new IllegalArgumentException("Unknown Curio script callback " + field + " in " + id);
            }
            if (!(handlers.get(context, hook.name, handlers) instanceof Function function)) {
                throw new IllegalArgumentException("Curio script callback " + hook.name + " must be a function in " + id);
            }
            callbacks.put(hook, new Handler(context, function.getParentScope(), function));
        }
        if (callbacks.isEmpty()) {
            throw new IllegalArgumentException("Curio script needs at least one callback: " + id);
        }
        if (loading.putIfAbsent(id, new Definition(id, Map.copyOf(callbacks))) != null) {
            throw new IllegalArgumentException("Duplicate Curio script definition: " + id);
        }
    }
}
