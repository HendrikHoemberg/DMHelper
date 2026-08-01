package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 10: hierarchy is expressed through tokens. A var() that resolves
 * to nothing silently inherits, so the surface looks styled while carrying no decision.
 */
class DesignTokenContractTest {

    @Test
    void everyTokenReferencedByAStylesheetIsDefined() {
        Set<String> defined = CssRules.definedTokens();
        List<String> dangling = new ArrayList<>();

        for (String file : CssRules.ALL_FILES) {
            for (String token : CssRules.referencedTokens(CssRules.read(file))) {
                if (!defined.contains(token)) dangling.add(file + " → " + token);
            }
        }

        assertThat(dangling).as("var() references with no definition").isEmpty();
    }

    @Test
    void everyTokenReferencedByATemplateIsDefined() throws IOException {
        Set<String> defined = CssRules.definedTokens();
        List<String> dangling = new ArrayList<>();

        try (Stream<Path> templates = Files.walk(Path.of("src/main/resources/templates"))) {
            for (Path template : templates.filter(p -> p.toString().endsWith(".html")).toList()) {
                String html = Files.readString(template);
                for (String token : CssRules.referencedTokens(html)) {
                    if (!defined.contains(token)) dangling.add(template + " → " + token);
                }
            }
        }

        assertThat(dangling).as("var() references with no definition").isEmpty();
    }

    private static String tokenValue(String name) {
        var matcher = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(name) + "\\s*:\\s*([^;]+);")
                .matcher(CssRules.read("tokens.css"));
        assertThat(matcher.find()).as("%s is defined", name).isTrue();
        return matcher.group(1).trim();
    }

    /**
     * Spec section 6.1 fixes these values and permits adjustment "only when measured
     * contrast requires it". Two were adjusted under that clause, and the tests below hold
     * the measurement that justifies each: --border-strong #686c75 reached 2.87:1 on Raised
     * where every control boundary sits, and --state-danger #d36a61 reached 4.32:1 as a
     * foreground on Raised. Roles and relative ordering are unchanged.
     */
    @Test
    void semanticRolesKeepTheirApprovedValues() {
        assertThat(tokenValue("--surface-canvas")).isEqualTo("#101113");
        assertThat(tokenValue("--surface-navigation")).isEqualTo("#151619");
        assertThat(tokenValue("--surface-workspace")).isEqualTo("#18191d");
        assertThat(tokenValue("--surface-panel")).isEqualTo("#1d1f24");
        assertThat(tokenValue("--surface-raised")).isEqualTo("#24262c");
        assertThat(tokenValue("--surface-inset")).isEqualTo("#111216");
        assertThat(tokenValue("--border-subtle")).isEqualTo("#30333a");
        assertThat(tokenValue("--border-strong")).isEqualTo("#6f737d");
        assertThat(tokenValue("--text-primary")).isEqualTo("#eee8dc");
        assertThat(tokenValue("--text-secondary")).isEqualTo("#b8b4aa");
        assertThat(tokenValue("--text-tertiary")).isEqualTo("#929089");
        assertThat(tokenValue("--action-primary")).isEqualTo("#c9a35c");
        assertThat(tokenValue("--action-primary-hover")).isEqualTo("#ddb977");
        assertThat(tokenValue("--state-success")).isEqualTo("#89ad69");
        assertThat(tokenValue("--state-warning")).isEqualTo("#e0a34d");
        assertThat(tokenValue("--state-danger")).isEqualTo("#d96d64");
        assertThat(tokenValue("--state-info")).isEqualTo("#74a3c1");
        assertThat(tokenValue("--state-shield")).isEqualTo("#8fa8b8");
        assertThat(tokenValue("--state-concentration")).isEqualTo("#b58ac1");
    }

    /** The neutral surfaces a foreground is allowed to land on. Cards are Raised. */
    private static final List<String> NEUTRAL_SURFACES = List.of(
            "--surface-workspace", "--surface-panel", "--surface-raised");

    private static final List<String> STATE_ROLES = List.of(
            "success", "warning", "danger", "info", "shield", "concentration");

    private static double ratio(String foreground, String background) {
        return ColorContrast.ratio(TokenColors.resolve(foreground), TokenColors.resolve(background));
    }

    @Test
    void everyTextRoleMeetsWcagAaOnEverySurfaceItPaintsOn() {
        for (String surface : NEUTRAL_SURFACES) {
            for (String text : List.of("--text-primary", "--text-secondary", "--text-tertiary")) {
                assertThat(ratio(text, surface))
                        .as("%s on %s", text, surface)
                        .isGreaterThanOrEqualTo(4.5);
            }
        }
        assertThat(ratio("--text-primary", "--surface-canvas")).isGreaterThanOrEqualTo(4.5);
        assertThat(ratio("--action-on-primary", "--action-primary")).isGreaterThanOrEqualTo(4.5);
    }

    @Test
    void everySemanticForegroundIsReadableOnEveryNeutralSurface() {
        for (String state : STATE_ROLES) {
            for (String surface : NEUTRAL_SURFACES) {
                assertThat(ratio("--state-" + state, surface))
                        .as("--state-%s on %s", state, surface)
                        .isGreaterThanOrEqualTo(4.5);
            }
        }
    }

    @Test
    void everyStateSurfaceCarriesReadablePrimaryText() {
        for (String state : STATE_ROLES) {
            assertThat(ratio("--text-primary", "--state-" + state + "-surface"))
                    .as("--text-primary on --state-%s-surface", state)
                    .isGreaterThanOrEqualTo(4.5);
        }
    }

    /**
     * Spec section 17: 3:1 for interactive boundaries. Measured against every surface the
     * boundary sits on, not just the friendliest one — .btn fills with --surface-raised and
     * sits on cards that fill with --surface-raised too, so the 1px border is the only thing
     * separating the control from its container.
     */
    @Test
    void everyInteractiveBoundaryMeetsThreeToOneOnEverySurfaceItSitsOn() {
        for (String surface : List.of("--surface-canvas", "--surface-navigation",
                "--surface-workspace", "--surface-panel", "--surface-raised", "--surface-inset")) {
            assertThat(ratio("--border-strong", surface))
                    .as("--border-strong on %s", surface)
                    .isGreaterThanOrEqualTo(3.0);
        }
        for (String state : STATE_ROLES) {
            assertThat(ratio("--state-" + state + "-border", "--surface-raised"))
                    .as("--state-%s-border on --surface-raised", state)
                    .isGreaterThanOrEqualTo(3.0);
        }
    }

    @Test
    void theFocusRingMeetsThreeToOneWhereverItCanLand() {
        for (String surface : List.of("--surface-canvas", "--surface-navigation",
                "--surface-workspace", "--surface-panel", "--surface-raised", "--surface-inset")) {
            assertThat(ratio("--focus-ring", surface))
                    .as("--focus-ring on %s", surface)
                    .isGreaterThanOrEqualTo(3.0);
        }
    }
}
