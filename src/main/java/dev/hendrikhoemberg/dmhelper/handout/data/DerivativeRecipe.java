package dev.hendrikhoemberg.dmhelper.handout.data;

import java.util.List;

public record DerivativeRecipe(
        int sourceWidth,
        int sourceHeight,
        int cropX,
        int cropY,
        int cropWidth,
        int cropHeight,
        List<RedactionRect> redactions
) {
    public record RedactionRect(int x, int y, int width, int height) {}
}
