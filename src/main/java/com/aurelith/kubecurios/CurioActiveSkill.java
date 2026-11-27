package com.aurelith.kubecurios;

import java.util.TreeMap;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

public record CurioActiveSkill(@Nullable String command, int cooldownTicks) {
    static final String KEY_NAME = "key.kubecurios.activate_curios";
    private static final String COOLDOWNS = "kubecurios:active_skill_cooldowns";

    enum Activate implements CustomPacketPayload {
        INSTANCE;

        static final Type<Activate> TYPE = new Type<>(Identifier.fromNamespaceAndPath(KubeCurios.MODID, "activate_curios"));

        @Override
        public Type<Activate> type() {
            return TYPE;
        }
    }

    static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(Activate.TYPE, StreamCodec.unit(Activate.INSTANCE), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                activate(player);
            }
        });
    }

    public static void activate(ServerPlayer player) {
        var level = player.level();
        var server = level.getServer();
        if (!server.isSameThread() || !canActivate(player) || !CurioScriptRuntime.beginActivation(player)) {
            return;
        }
        var equipped = CurioEquipment.snapshot(player, item -> item.activeSkill() != null);
        long now = server.overworld().getGameTime();
        for (var entry : new TreeMap<>(equipped).entrySet()) {
            if (!canActivate(player) || player.level() != level) {
                break;
            }
            var curio = entry.getValue();
            var skill = curio.item().activeSkill();
            if (skill == null || !curio.source().isCurrent(player)) {
                continue;
            }
            String item = entry.getKey();
            var data = player.getPersistentData();
            var deadlines = data.getCompoundOrEmpty(COOLDOWNS);
            if (now < deadlines.getLongOr(item, Long.MIN_VALUE)) {
                continue;
            }
            deadlines.putLong(item, now + skill.cooldownTicks);
            data.put(COOLDOWNS, deadlines);
            if (skill.command != null) {
                CurioCombatEffects.runCommand(player, skill.command);
            }
            if (canActivate(player) && player.level() == level && curio.source().isCurrent(player)) {
                CurioScriptRuntime.runAction(CurioScripts.Hook.ACTIVATE, player, item, curio, null);
            }
        }
    }

    private static boolean canActivate(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.hasDisconnected();
    }
}
