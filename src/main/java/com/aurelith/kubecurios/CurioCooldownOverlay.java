package com.aurelith.kubecurios;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid = KubeCurios.MODID, value = Dist.CLIENT)
public final class CurioCooldownOverlay {
    private static final Map<Identifier, DisplayCooldown> COOLDOWNS = new HashMap<>();
    private static long clientTick;

    private CurioCooldownOverlay() {}

    private record DisplayCooldown(long endTick, int duration) {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(CurioActiveSkill.CooldownSync.TYPE, (packet, context) -> {
            COOLDOWNS.clear();
            for (var entry : packet.cooldowns()) {
                if (entry.remainingTicks() > 0 && entry.durationTicks() > 0) {
                    COOLDOWNS.put(entry.item(), new DisplayCooldown(clientTick + entry.remainingTicks(), entry.durationTicks()));
                }
            }
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var client = Minecraft.getInstance();
        if (!COOLDOWNS.isEmpty() && client.level != null && !client.isPaused()
                && client.level.tickRateManager().runsNormally()) {
            clientTick++;
            COOLDOWNS.values().removeIf(cooldown -> cooldown.endTick() <= clientTick);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        COOLDOWNS.clear();
        clientTick = 0L;
    }

    public static float progress(ItemStack stack, float partialTick) {
        if (COOLDOWNS.isEmpty()) {
            return 0.0F;
        }
        var cooldown = COOLDOWNS.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        return cooldown == null ? 0.0F
                : Mth.clamp((cooldown.endTick() - clientTick - partialTick) / cooldown.duration(), 0.0F, 1.0F);
    }
}
