package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The --color-* names are a temporary migration bridge (spec section 18.1). They may only
 * ever shrink. Task 49 deletes the last of them.
 */
class LegacyVisualAliasContractTest {

    /** Fill each ceiling with the count printed by the first run of this test. */
    private static final Map<String, Long> BUDGETS = new LinkedHashMap<>();

    static {
        BUDGETS.put("--color-bg", 82L);
        BUDGETS.put("--color-surface", 45L);
        BUDGETS.put("--color-surface-hover", 24L);
        BUDGETS.put("--color-text", 113L);
        BUDGETS.put("--color-text-muted", 182L);
        BUDGETS.put("--color-accent", 67L);
        BUDGETS.put("--color-accent-hover", 0L);
        BUDGETS.put("--color-danger", 35L);
        BUDGETS.put("--color-danger-hover", 0L);
        BUDGETS.put("--color-success", 23L);
        BUDGETS.put("--color-warning", 32L);
        BUDGETS.put("--color-warning-bg", 1L);
        BUDGETS.put("--color-warning-text", 1L);
        BUDGETS.put("--color-border", 173L);
        BUDGETS.put("--color-border-strong", 40L);
        BUDGETS.put("--color-concentration", 1L);
        BUDGETS.put("--color-info", 1L);
        BUDGETS.put("--color-ember", 10L);
        BUDGETS.put("--color-gold-soft", 12L);
        BUDGETS.put("--color-overlay", 3L);
        BUDGETS.put("--color-shield", 2L);
        BUDGETS.put("--color-shield-hover", 0L);
        BUDGETS.put("--color-shield-soft", 1L);
        BUDGETS.put("--color-attack-bonus", 0L);
        BUDGETS.put("--color-text-secondary", 2L);
        BUDGETS.put("--color-border-subtle", 1L);
        BUDGETS.put("--color-bg-elevated", 2L);
        BUDGETS.put("--color-surface-muted", 2L);
    }

    @Test
    void legacyAliasUseNeverGrows() {
        BUDGETS.forEach((token, ceiling) -> assertThat(CssRules.tokenReferenceCount(token))
                .as("%s references — budgets only ratchet down", token)
                .isLessThanOrEqualTo(ceiling));
    }

    @Test
    void noAliasOutsideTheApprovedVocabularyIsIntroduced() {
        String tokens = CssRules.read("tokens.css");
        var declared = java.util.regex.Pattern.compile("(--color-[a-z0-9-]+)\\s*:")
                .matcher(tokens).results()
                .map(r -> r.group(1))
                .collect(java.util.stream.Collectors.toSet());
        assertThat(declared)
                .as("new legacy aliases are forbidden")
                .isSubsetOf(BUDGETS.keySet());
    }
}
