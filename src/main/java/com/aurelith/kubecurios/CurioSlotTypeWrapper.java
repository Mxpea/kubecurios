package com.aurelith.kubecurios;

import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import dev.latvian.mods.rhino.util.wrap.DirectTypeWrapperFactory;

public final class CurioSlotTypeWrapper {
    private CurioSlotTypeWrapper() {}

    public static void register(TypeWrapperRegistry registry) {
        registry.register(CurioSlot.class, (DirectTypeWrapperFactory<CurioSlot>) value -> {
            if (value instanceof CurioSlot slot) {
                return slot;
            }
            if (value instanceof CharSequence id) {
                return new CurioSlot(id.toString());
            }
            throw new IllegalArgumentException("Expected a Curios slot ID string, got: " + value);
        });
    }
}
