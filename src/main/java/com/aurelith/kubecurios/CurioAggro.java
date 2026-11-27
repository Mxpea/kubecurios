package com.aurelith.kubecurios;

import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.LivingTargetType;
import org.jetbrains.annotations.Nullable;

public final class CurioAggro {
    // Vanilla PiglinAi.setAngerTarget uses this expiry for ANGRY_AT.
    private static final long BRAIN_ANGER_TICKS = 600L;

    private CurioAggro() {}

    record Rules(double provokeRadius, Set<HostileFilter> ignored) {}

    record HostileFilter(Identifier id, boolean tag) {
        static HostileFilter parse(String value) {
            boolean tag = value.startsWith("#");
            return new HostileFilter(Identifier.parse(tag ? value.substring(1) : value), tag);
        }

        boolean matches(Mob mob) {
            return tag ? mob.typeHolder().is(TagKey.create(Registries.ENTITY_TYPE, id))
                    : mob.typeHolder().is(ResourceKey.create(Registries.ENTITY_TYPE, id));
        }

        Component label() {
            return tag ? Component.literal("#" + id) : Component.translatableWithFallback(id.toLanguageKey("entity"), id.toString());
        }
    }

    public static boolean ignoresProactiveTargeting(ServerLevel level, @Nullable LivingEntity observer, LivingEntity target) {
        if (!(observer instanceof Mob mob) || !(mob instanceof Enemy) || mob.getTarget() == target
                || mob.getLastHurtByMob() == target || mob.getBrain().isMemoryValue(MemoryModuleType.HURT_BY_ENTITY, target)
                || mob.getBrain().isMemoryValue(MemoryModuleType.ANGRY_AT, target.getUUID())
                || (mob instanceof NeutralMob neutral && neutral.isAngryAt(target, level))) {
            return false;
        }
        return CurioEquipment.ignoresProactiveTargeting(target, mob);
    }

    static void tickProvocation(LivingEntity wearer, ServerLevel level, String slot, int index) {
        var source = CurioEquipment.provocationSource(wearer);
        if (source == null || !source.source().id().equals(slot) || source.source().index() != index) {
            return;
        }
        double radius = source.item().aggroRules().provokeRadius();
        for (var mob : level.getEntitiesOfClass(Mob.class, wearer.getBoundingBox().inflate(radius),
                candidate -> candidate != wearer && candidate instanceof NeutralMob
                        && !(candidate instanceof TamableAnimal tame && tame.isTame())
                        && candidate.distanceToSqr(wearer) <= radius * radius)) {
            var current = mob.getTarget();
            if (current == null || (current == wearer && !((NeutralMob) mob).isAngry())) {
                set(mob, wearer);
            }
        }
    }

    static boolean set(@Nullable LivingEntity entity, @Nullable LivingEntity target) {
        if (!(entity instanceof Mob mob) || target == null || !canModify(mob, target)
                || !target.isAlive() || !mob.canAttack(target) || mob.isAlliedTo(target)) {
            return false;
        }

        var brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            var event = CommonHooks.onLivingChangeTarget(mob, target, LivingTargetType.BEHAVIOR_TARGET);
            if (event.isCanceled() || event.getNewAboutToBeSetTarget() != target) {
                return false;
            }
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        } else {
            mob.setTarget(target);
            if (mob.getTarget() != target) {
                return false;
            }
        }

        if (mob instanceof NeutralMob neutral) {
            neutral.setPersistentAngerTarget(EntityReference.of(target));
            neutral.startPersistentAngerTimer();
        }
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED)) {
            brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), BRAIN_ANGER_TICKS);
        }
        return true;
    }

    static boolean clear(@Nullable LivingEntity entity, @Nullable LivingEntity target) {
        if (!(entity instanceof Mob mob) || target == null || !canModify(mob, target)) {
            return false;
        }

        boolean changed = false;
        if (mob.getTargetUnchecked() == target) {
            mob.setTarget(null);
            if (mob.getTargetUnchecked() == target) {
                return false;
            }
            // A running target goal can restore its cached target after Mob#setTarget(null).
            for (var goal : List.copyOf(mob.targetSelector.getAvailableGoals())) {
                if (mob.getTargetUnchecked() != null) {
                    break;
                }
                if (goal.isRunning() && goal.getGoal() instanceof TargetGoal) {
                    goal.stop();
                }
            }
            changed = true;
        }
        if (mob instanceof NeutralMob neutral) {
            var angerTarget = neutral.getPersistentAngerTarget();
            if (angerTarget != null && angerTarget.matches(target)) {
                neutral.setPersistentAngerTarget(null);
                neutral.setPersistentAngerEndTime(NeutralMob.NO_ANGER_END_TIME);
                changed = true;
            }
        }
        if (mob.getLastHurtByMob() == target) {
            mob.setLastHurtByMob(null);
            changed = true;
        }

        var brain = mob.getBrain();
        if (brain.isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            changed = true;
        }
        if (brain.isMemoryValue(MemoryModuleType.ANGRY_AT, target.getUUID())) {
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
            changed = true;
        }
        if (brain.isMemoryValue(MemoryModuleType.HURT_BY_ENTITY, target)) {
            brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            changed = true;
        }
        return changed;
    }

    private static boolean canModify(Mob mob, LivingEntity target) {
        return mob.level() instanceof ServerLevel level && level.getServer().isSameThread()
                && mob.isAlive() && mob != target && target.level() == level;
    }
}
