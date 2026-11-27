package com.aurelith.kubecurios;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(KubeCurios.MODID)
public class KubeCurios {
    public static final String MODID = "kubecurios";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KubeCurios(IEventBus modBus) {
        CurioWearTime.register(modBus);
        modBus.addListener(CurioActiveSkill::registerPayload);
        NeoForge.EVENT_BUS.addListener(this::validateSlots);
        NeoForge.EVENT_BUS.addListener(CurioCombatEffects::modifyDamage);
        NeoForge.EVENT_BUS.addListener(CurioCombatEffects::onHurt);
        NeoForge.EVENT_BUS.addListener(CurioActiveSkill::onLogin);
        NeoForge.EVENT_BUS.addListener(CurioActiveSkill::onRespawn);
        NeoForge.EVENT_BUS.addListener(CurioScriptRuntime::onTick);
        NeoForge.EVENT_BUS.addListener(CurioScriptRuntime::onLeave);
        NeoForge.EVENT_BUS.addListener(CurioScriptRuntime::onStop);
    }

    private void validateSlots(ServerStartedEvent event) {
        CurioSlotWhitelist.validate();
    }
}
