package com.aurelith.kubecurios;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

record CurioArmorAppearance(EquipmentSlot slot, ResourceKey<EquipmentAsset> asset) {
    static CurioArmorAppearance parse(String part, String assetId) {
        var slot = switch (part) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            default -> throw new IllegalArgumentException("Curio armor part must be head, chest, legs or feet: " + part);
        };
        return new CurioArmorAppearance(slot, ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.parse(assetId)));
    }
}
