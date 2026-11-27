package com.aurelith.kubecurios;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
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

    record Cooldown(Identifier item, int remainingTicks, int durationTicks) {
        static final StreamCodec<RegistryFriendlyByteBuf, Cooldown> CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, Cooldown::item,
                ByteBufCodecs.VAR_INT, Cooldown::remainingTicks,
                ByteBufCodecs.VAR_INT, Cooldown::durationTicks,
                Cooldown::new);
    }

    record CooldownSync(List<Cooldown> cooldowns) implements CustomPacketPayload {
        static final Type<CooldownSync> TYPE = new Type<>(
                Identifier.fromNamespaceAndPath(KubeCurios.MODID, "active_cooldowns"));
        static final StreamCodec<RegistryFriendlyByteBuf, CooldownSync> CODEC =
                Cooldown.CODEC.apply(ByteBufCodecs.list()).map(CooldownSync::new, CooldownSync::cooldowns);

        @Override
        public Type<CooldownSync> type() {
            return TYPE;
        }
    }

    static void registerPayload(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1").optional();
        registrar.playToServer(Activate.TYPE, StreamCodec.unit(Activate.INSTANCE), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                activate(player);
            }
        });
        registrar.playToClient(CooldownSync.TYPE, CooldownSync.CODEC);
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncCooldowns(player);
        }
    }

    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncCooldowns(player);
        }
    }

    private static void syncCooldowns(ServerPlayer player) {
        if (!player.connection.getConnection().isConnected()
                || !NetworkRegistry.hasChannel(player.connection, CooldownSync.TYPE.id())) {
            return;
        }
        var deadlines = player.getPersistentData().getCompoundOrEmpty(COOLDOWNS);
        long now = player.level().getServer().overworld().getGameTime();
        var cooldowns = new ArrayList<Cooldown>();
        for (String itemId : deadlines.keySet()) {
            long deadline = deadlines.getLongOr(itemId, Long.MIN_VALUE);
            if (deadline <= now) {
                continue;
            }
            var id = Identifier.tryParse(itemId);
            if (id != null && BuiltInRegistries.ITEM.getValue(id) instanceof CurioBuilder.ScriptedCurioItem item) {
                var skill = item.activeSkill();
                if (skill != null) {
                    int remaining = (int) Math.min(deadline - now, Integer.MAX_VALUE);
                    cooldowns.add(new Cooldown(id, remaining, Math.max(remaining, skill.cooldownTicks)));
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new CooldownSync(cooldowns));
    }

    public static void activate(ServerPlayer player) {
        var level = player.level();
        var server = level.getServer();
        if (!server.isSameThread() || !canActivate(player) || !CurioScriptRuntime.beginActivation(player)) {
            return;
        }
        var equipped = CurioEquipment.snapshot(player, item -> item.activeSkill() != null);
        long now = server.overworld().getGameTime();
        boolean started = false;
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
            started = true;
            if (skill.command != null) {
                CurioCombatEffects.runCommand(player, skill.command);
            }
            if (canActivate(player) && player.level() == level && curio.source().isCurrent(player)) {
                CurioScriptRuntime.runAction(CurioScripts.Hook.ACTIVATE, player, item, curio, null);
            }
        }
        if (started) {
            syncCooldowns(player);
        }
    }

    private static boolean canActivate(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.hasDisconnected();
    }
}
