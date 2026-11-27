package com.aurelith.kubecurios;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = KubeCurios.MODID, value = Dist.CLIENT)
public final class CurioKeyMappings {
    private static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(KubeCurios.MODID, "curios"));
    private static final KeyMapping ACTIVATE = new KeyMapping(CurioActiveSkill.KEY_NAME, KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    private CurioKeyMappings() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(ACTIVATE);
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        handleInput(InputConstants.getKey(event.getKeyEvent()), event.getAction());
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Post event) {
        handleInput(InputConstants.Type.MOUSE.getOrCreate(event.getButton()), event.getAction());
    }

    private static void handleInput(InputConstants.Key key, int action) {
        if (action == GLFW.GLFW_RELEASE || !ACTIVATE.isActiveAndMatches(key)) {
            return;
        }
        boolean clicked = ACTIVATE.consumeClick();
        if (!clicked || action != GLFW.GLFW_PRESS) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var connection = minecraft.getConnection();
        var player = minecraft.player;
        if (minecraft.screen != null || player == null || !player.isAlive() || player.isSpectator()
                || connection == null || !connection.hasChannel(CurioActiveSkill.Activate.TYPE)) {
            return;
        }
        ClientPacketDistributor.sendToServer(CurioActiveSkill.Activate.INSTANCE);
    }
}
