package com.aurelith.kubecurios;

import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.CustomMember;
import dev.latvian.mods.rhino.NativeJavaObject;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptRuntime;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.CustomJavaToJsWrapper;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.Nullable;

public class CurioScriptContext implements CustomJavaToJsWrapper {
    private final LivingEntity entity;
    private final String itemId;
    private final CurioEquipment.Slot source;
    private final long equippedTicks;
    private final Consumer<String> diagnostic;
    private boolean active = true;

    CurioScriptContext(LivingEntity entity, String itemId, CurioEquipment.Slot source, long equippedTicks,
                       Consumer<String> diagnostic) {
        this.entity = entity;
        this.itemId = itemId;
        this.source = source;
        this.equippedTicks = equippedTicks;
        this.diagnostic = diagnostic;
    }

    public LivingEntity entity() {
        return entity;
    }

    public String itemId() {
        return itemId;
    }

    public boolean commandSucceeds(String command) {
        return active && CurioScriptCommands.run(entity, command, diagnostic).accepted();
    }

    public @Nullable Integer commandResult(String command) {
        return active ? CurioScriptCommands.run(entity, command, diagnostic).result() : null;
    }

    public boolean setAggro(@Nullable LivingEntity mob, @Nullable LivingEntity target) {
        return active && mob != null && mob.level() == entity.level() && CurioAggro.set(mob, target);
    }

    public boolean clearAggro(@Nullable LivingEntity mob, @Nullable LivingEntity target) {
        return active && mob != null && mob.level() == entity.level() && CurioAggro.clear(mob, target);
    }

    public int countOtherEquipped(String tag) {
        if (!tag.startsWith("#")) {
            throw new IllegalArgumentException("Expected an item tag, e.g. #mypack:blood_set");
        }
        return CurioEquipment.countOtherEquipped(entity, source, CurioScripts.parseId(tag.substring(1)));
    }

    /** Completed equipped ticks for this wearer and item ID, including past sessions. */
    public long equippedTicks() {
        return equippedTicks;
    }

    public @Nullable Integer maxDurability() {
        int max = source.stack().getMaxDamage();
        return max > 0 ? max : null;
    }

    public @Nullable Integer remainingDurability() {
        var max = maxDurability();
        return max == null ? null : Math.clamp(max - source.stack().getDamageValue(), 0, max);
    }

    public @Nullable Double remainingDurabilityPercent() {
        var max = maxDurability();
        return max == null ? null : remainingDurability() * 100.0 / max;
    }

    public int enchantmentLevel(String id) {
        var key = ResourceKey.create(Registries.ENCHANTMENT, CurioScripts.parseId(id));
        var enchantment = entity.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getTagEnchantmentLevel(enchantment, source.stack());
    }

    void expire() {
        active = false;
    }

    @Override
    public Scriptable convertJavaToJs(Context context, Scriptable scope, TypeInfo type) {
        return new ScriptContextWrapper(scope, this, type, context);
    }

    private static final class ScriptContextWrapper extends NativeJavaObject {
        ScriptContextWrapper(Scriptable scope, CurioScriptContext value, TypeInfo type, Context context) {
            super(scope, value, type, context);
            // Return nullable numbers directly; reflection wraps boxed primitives as Java objects.
            addCustomFunction("remainingDurability", TypeInfo.NONE, cx -> value.remainingDurability());
            addCustomFunction("maxDurability", TypeInfo.NONE, cx -> value.maxDurability());
            addCustomFunction("remainingDurabilityPercent", TypeInfo.NONE, cx -> value.remainingDurabilityPercent());
            var commandResult = new BaseFunction() {
                @Override
                public Object call(Context cx, Scriptable scope, Scriptable self, Object[] args) {
                    if (args.length != 1 || !(args[0] instanceof CharSequence command)) {
                        throw ScriptRuntime.typeError(cx, "commandResult expects one command string");
                    }
                    return value.commandResult(command.toString());
                }
            };
            commandResult.setParentScope(scope);
            addCustomMember(new CustomMember("commandResult", TypeInfo.NONE, commandResult));
        }
    }

    public static final class Combat extends CurioScriptContext {
        private final CurioScriptRuntime.Damage damage;

        Combat(LivingEntity entity, String itemId, CurioEquipment.Slot source, long equippedTicks,
               Consumer<String> diagnostic, CurioScriptRuntime.Damage damage) {
            super(entity, itemId, source, equippedTicks, diagnostic);
            this.damage = damage;
        }

        public @Nullable LivingEntity attacker() { return damage.attacker(); }
        public LivingEntity target() { return damage.target(); }
        public DamageSource damageSource() { return damage.source(); }
        public float damage() { return damage.amount(); }
    }
}
