package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

/** Spec section 10: features specialize content, never recreate the primitives. */
class SharedComponentContractTest {

    private static final List<String> REQUIRED_FRAGMENTS = List.of(
            "fragments/_page-header.html|page-header(title, summary, breadcrumb, primary, secondary)",
            "fragments/_toolbar.html|toolbar(action, searchValue, searchPlaceholder, filters, actions)",
            "fragments/_toolbar.html|table-toolbar(selectionLabel, actions)",
            "fragments/_badge.html|badge(tone, icon, label)",
            "fragments/_context-rail.html|rail(body)",
            "fragments/_context-rail.html|rail-section(title, body)",
            "fragments/_states.html|empty(icon, title, description, cta)",
            "fragments/_states.html|loading(label)",
            "fragments/_states.html|skeleton(count)",
            "fragments/_states.html|unavailable(title, description, retry)",
            "fragments/_states.html|failed(title, description, retry)",
            "fragments/_banner.html|banner(tone, title, body, actions)",
            "fragments/_status.html|save-status(id)",
            "fragments/_overlay.html|dialog(id, title, body, actions)",
            "fragments/_overlay.html|side-sheet(id, title, body)",
            "fragments/_overlay.html|popover(id, label, body)",
            "fragments/_overlay.html|toast-region");

    @Test
    void everySharedContractExistsWithItsApprovedSignature() {
        for (String required : REQUIRED_FRAGMENTS) {
            String[] parts = required.split("\\|", 2);
            Path file = TemplateRules.ROOT.resolve(parts[0]);
            assertThat(file).as("%s exists", parts[0]).exists();
            assertThat(TemplateRules.read(file))
                    .as("fragment %s in %s", parts[1], parts[0])
                    .contains("th:fragment=\"" + parts[1] + "\"");
        }
    }

    /**
     * Matched on the parsed class token, not on the literal {@code class="page-rail"}. The
     * string form was trivially evadable by writing a second class first:
     * {@code adventure/_scene-rail.html} carried {@code class="scene-rail page-rail"} and
     * reimplemented the contextual rail — losing the {@code <aside>} landmark every other
     * detail page gets — while this test reported it clean.
     */
    @Test
    void featureTemplatesDoNotRecreateThePrimitives() {
        int scanned = 0;
        for (Path template : TemplateRules.allTemplates()) {
            if (template.toString().contains("/fragments/")) continue;
            // library/_sheet.html is the legacy statblock sheet — it reimplements
            // class="side-sheet" and is NOT a page. It is migrated onto the shared
            // side-sheet fragment in Part 4 (ui-redesign-4, statblock sheet task); until
            // then it keeps its own implementation and is exempted here.
            if (template.toString().endsWith("/library/_sheet.html")) continue;
            var document = TemplateRules.parse(template);
            scanned++;
            for (String privateCopy : List.of("toolbar", "page-rail", "toast",
                    "side-sheet", "page-header")) {
                assertThat(document.select("." + privateCopy))
                        .as("%s reimplements .%s — use the shared fragment",
                                template, privateCopy)
                        .isEmpty();
            }
        }
        assertThat(scanned).as("no template was scanned — this test has gone blind")
                .isGreaterThan(0);
    }

    @Test
    void badgeTonesAreTheStableSemanticSet() {
        String css = CssRules.allApplicationCss();
        for (String tone : List.of("success", "warning", "danger", "info", "shield", "neutral")) {
            assertThat(css).as("badge tone %s", tone).contains(".badge--" + tone);
        }
    }

    private static final Pattern RETIRED_TONE =
            Pattern.compile("badge-(success|warning|danger|info|muted|secondary)\\b");

    /** The six tone words, however a template spells the class that carries them. */
    private static final Pattern TONE_WORD =
            Pattern.compile("\\b(success|warning|danger|info|muted|secondary)\\b");

    /**
     * Spec 6.4: a semantic badge must combine at least two channels, and colour is the one
     * it always brings. Task 16 replaced the four single-dash semantic badge rules with the
     * {@code .badge--*} tone set. A template still naming a single-dash tone renders a plain
     * neutral chip, so GAIN and SPEND, or a death save and an inspiration point, come out
     * looking identical — and nothing complains, because an unmatched class is not an error
     * in CSS or in Thymeleaf.
     */
    @Test
    void noTemplateNamesARetiredSingleDashBadgeTone() {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (Path template : TemplateRules.allTemplates()) {
            scanned++;
            Matcher match = RETIRED_TONE.matcher(TemplateRules.read(template));
            while (match.find()) {
                offenders.add(template + " -> " + match.group());
            }
        }

        assertThat(scanned).as("no template was scanned — this test has gone blind")
                .isGreaterThan(0);
        assertThat(offenders)
                .as("retired badge tone: it has no CSS rule, so the state renders colourless")
                .isEmpty();
    }

    /**
     * The same defect, assembled at render time instead of written out. {@code
     * encounter/_waves.html} built {@code 'badge-' + (ACTIVE ? 'success' : 'warning')}, which
     * the literal scan above cannot see because no source line ever contains the string
     * {@code badge-success}. Checked per attribute rather than per file so the domain
     * families that legitimately concatenate a single-dash prefix — {@code 'badge-' + rarity}
     * on magic items, {@code 'badge-state-' + …} on inventory — do not have to be exempted:
     * their expressions carry no tone word.
     */
    @Test
    void noTemplateAssemblesARetiredBadgeToneAtRenderTime() {
        List<String> offenders = new ArrayList<>();
        int scanned = 0;

        for (Path template : TemplateRules.allTemplates()) {
            // Read the attributes off every element rather than selecting on them: a jsoup
            // CSS selector cannot address the "th:" prefix, and one that silently matches
            // nothing is exactly the blind test this scan is guarding against.
            for (var element : TemplateRules.parse(template).getAllElements()) {
                for (String attribute : List.of("th:class", "th:classappend")) {
                    String expression = element.attr(attribute);
                    if (expression.isBlank()) continue;
                    scanned++;
                    // Strip the approved prefix first; whatever "badge-" survives is single-dash.
                    boolean singleDashPrefix = expression.replace("badge--", "").contains("badge-");
                    if (singleDashPrefix && TONE_WORD.matcher(expression).find()) {
                        offenders.add(template + " -> " + attribute + "=\"" + expression + "\"");
                    }
                }
            }
        }

        assertThat(scanned)
                .as("no dynamic class expression was scanned — this test has gone blind")
                .isGreaterThan(0);
        assertThat(offenders)
                .as("a badge tone concatenated onto the retired single-dash prefix")
                .isEmpty();
    }
}
