package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 6.2: texture is permitted only on campaign identity, narrative/parchment,
 * handout and intentionally in-world surfaces, and a vignette may remain on immersive
 * runtime/editor canvases only. A noise texture stretched across the whole document is what
 * section 2 calls decorative treatment competing with operational clarity, and section 5.2
 * answers directly: the foundation is neutral charcoal, warmth comes from selected moments.
 */
class SurfaceTextureContractTest {

    /** Surfaces that are in-world artifacts rather than tool chrome. */
    private static final String[] IN_WORLD_MARKERS = {
            ".parchment", ".read-aloud", ".book-cover", ".campaign-sigil", ".statblock",
            ".handout", ".note-body"};

    @Test
    void textureIsPaintedOnlyOnInWorldSurfaces() {
        var textured = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> !rule.file().equals("tokens.css"))
                .filter(rule -> Stream.of("background", "background-image")
                        .map(rule::value)
                        .filter(Objects::nonNull)
                        .anyMatch(value -> value.contains("--texture-")
                                || value.contains("--tone-parchment")))
                .toList();

        assertThat(textured)
                .as("no rule paints a texture at all — this scan would pass on an empty set")
                .isNotEmpty();
        assertThat(textured).allSatisfy(rule -> assertThat(rule.selector())
                .as("texture in %s — spec 6.2 limits it to in-world surfaces", rule.where())
                .containsAnyOf(IN_WORLD_MARKERS));
    }

    /**
     * The vignette mechanism stays; its resting value is what matters. Prep screens are not
     * immersive canvases, so the base intensity is zero and the runtime surfaces raise it.
     */
    @Test
    void theVignetteRestsAtZeroOutsideImmersiveCanvases() {
        var root = CssRules.of("tokens.css").stream()
                .filter(rule -> rule.selector().equals(":root"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(":root is not declared in tokens.css"));

        assertThat(root.value("--vignette-intensity"))
                .as("resting --vignette-intensity — spec 6.2 allows a vignette on immersive "
                        + "runtime and editor canvases only")
                .isEqualTo("0");

        List<String> raisedFor = CssRules.of(CssRules.ALL_FILES).stream()
                .filter(rule -> !rule.selector().equals(":root"))
                .filter(rule -> rule.declares("--vignette-intensity"))
                .map(CssRules.Rule::selector)
                .toList();
        assertThat(String.join(" ", raisedFor))
                .as("the immersive surfaces that raise the vignette")
                .contains("cockpit")
                .contains("battle")
                .contains("handout");
    }
}
