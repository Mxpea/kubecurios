package com.aurelith.kubecurios;

import java.util.regex.Pattern;

/** A slot identifier, not a live slot definition (those belong to the world datapack). */
public record CurioSlot(String id) {
    private static final Pattern SLOT_ID = Pattern.compile("[a-z0-9_.-]+");

    public CurioSlot {
        if (id == null || !SLOT_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Expected a Curios slot ID, such as 'ring', got: " + id);
        }
    }
}
