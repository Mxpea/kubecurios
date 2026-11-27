package com.aurelith.kubecurios;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

public final class CurioDetection {
    private CurioDetection() {}

    public static double modifyVisibility(double vanilla, ServerPlayer wearer, @Nullable Entity observer) {
        if (!(observer instanceof Enemy)) {
            return vanilla;
        }
        double visibility = vanilla;
        for (var curio : CurioEquipment.snapshot(wearer, item -> item.hostileDetectionRangePercent() != 0).values()) {
            visibility = CurioCombatEffects.multiply(visibility, 1.0 + curio.item().hostileDetectionRangePercent() / 100.0);
        }
        return visibility;
    }
}
