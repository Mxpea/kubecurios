package com.aurelith.kubecurios;

record CurioItemAppearance(Anchor anchor, float x, float y, float z, float scale) {
    enum Anchor { HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG }

    static CurioItemAppearance parse(String part, double x, double y, double z, double scale) {
        var anchor = switch (part) {
            case "head" -> Anchor.HEAD;
            case "body" -> Anchor.BODY;
            case "left_arm" -> Anchor.LEFT_ARM;
            case "right_arm" -> Anchor.RIGHT_ARM;
            case "left_leg" -> Anchor.LEFT_LEG;
            case "right_leg" -> Anchor.RIGHT_LEG;
            default -> throw new IllegalArgumentException("Unknown humanoid Curio anchor: " + part);
        };
        if (!Float.isFinite((float) x) || !Float.isFinite((float) y) || !Float.isFinite((float) z)
                || !Float.isFinite((float) scale) || (float) scale <= 0) {
            throw new IllegalArgumentException("Curio item appearance requires finite offsets and a positive finite scale");
        }
        return new CurioItemAppearance(anchor, (float) x, (float) y, (float) z, (float) scale);
    }
}
