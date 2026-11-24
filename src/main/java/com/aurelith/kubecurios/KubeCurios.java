package com.aurelith.kubecurios;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(KubeCurios.MODID)
public class KubeCurios {
    public static final String MODID = "kubecurios";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KubeCurios() {
        NeoForge.EVENT_BUS.addListener(this::validateSlots);
        NeoForge.EVENT_BUS.addListener(CurioCombatEffects::modifyDamage);
        NeoForge.EVENT_BUS.addListener(CurioCombatEffects::onHurt);
    }

    private void validateSlots(ServerStartedEvent event) {
        CurioSlotWhitelist.validate();
    }
}
