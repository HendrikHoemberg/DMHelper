package dev.hendrikhoemberg.dmhelper.common;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Screen safety coverage used to be a whitelist: ScreenSafetyCoverageTest asserts tagging on
 * the surfaces it names, so a new DM-facing field shipped exposed and the suite stayed green.
 * A section labelled "Secret" sat on screen underneath a badge reading PLAYER-SAFE.
 *
 * <p>This test inverts that. It walks every template and fails when a DM-sensitive entity
 * field is rendered outside a {@code data-screen-sensitive} subtree. To add a genuinely
 * player-facing field, you must either tag its block or justify an entry below -- not simply
 * forget.
 */
class DmSensitiveFieldCoverageTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /**
     * Entity properties that describe plot, mechanics, rewards, secrets, monster statistics
     * or placement. Player-visible by policy: scene title, READ_ALOUD section bodies, the
     * map/handout surface, the party bar. Everything else is DM-facing.
     */
    private static final Pattern DM_SENSITIVE = Pattern.compile(
            "\\.(secrets?|motivation|dmNote|rewards|prerequisites|outcomeNotes|reputationNotes"
            + "|goals|success|failure|partial|dc|placementHint|statBlock)\\b");

    /**
     * Only these attributes render content to the page. Deliberately excludes th:if (a
     * visibility guard, not a render) and every non-Thymeleaf attribute: encounter/_tracker.html
     * carries Alpine x-text bindings and a JavaScript method literally named failure(), which
     * a looser rule flags twenty times over. Thymeleaf's [[${...}]] inline syntax is never
     * used with these fields -- verified across all templates.
     */
    private static final Set<String> RENDERING_ATTRIBUTES = Set.of("th:text", "th:utext");

    /**
     * Edit forms are excluded by policy. They render the same DM-sensitive fields into
     * <input> and <textarea> values, but are reachable only by deliberate DM navigation --
     * a DM does not open "Edit Faction" with the laptop facing the table. This exclusion is
     * explicit rather than accidental; if that judgement changes, delete this method and tag
     * the form blocks instead.
     */
    private static boolean isEditForm(Path template) {
        String name = template.getFileName().toString();
        return name.endsWith("-form.html") || name.equals("_form.html") || name.equals("form.html");
    }

    private record Violation(String file, int approximateLine, String expression) {
        @Override public String toString() {
            return "%s (near line %d): %s".formatted(file, approximateLine, expression);
        }
    }

    @Test
    void everyDmSensitiveFieldRendersInsideAScreenSensitiveSubtree() throws IOException {
        List<Violation> violations = new ArrayList<>();
        List<Path> scanned = new ArrayList<>();

        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            for (Path template : files.filter(p -> p.toString().endsWith(".html")).toList()) {
                if (isEditForm(template)) {
                    continue;
                }
                scanned.add(template);
                String source = Files.readString(template);
                Document doc = Jsoup.parse(source, "", Parser.htmlParser());

                for (Element element : doc.getAllElements()) {
                    String expression = dmSensitiveExpression(element);
                    if (expression == null || isScreenSensitive(element)) {
                        continue;
                    }
                    violations.add(new Violation(
                            TEMPLATES.relativize(template).toString(),
                            approximateLine(source, expression),
                            expression));
                }
            }
        }

        assertThat(scanned)
                .as("the scan must actually reach the templates; a broken path would pass vacuously")
                .hasSizeGreaterThan(100);

        assertThat(violations)
                .as("""
                    These render DM-facing content outside any data-screen-sensitive subtree, so they stay on \
                    screen when a DM turns the laptop to the table under the TABLE-SAFE badge. \
                    Add data-screen-sensitive to the enclosing block -- never to a READ_ALOUD body.""")
                .isEmpty();
    }

    /** The first DM-sensitive rendering expression on this element, or null. */
    private static String dmSensitiveExpression(Element element) {
        for (var attribute : element.attributes()) {
            if (!RENDERING_ATTRIBUTES.contains(attribute.getKey())) {
                continue;
            }
            if (DM_SENSITIVE.matcher(attribute.getValue()).find()) {
                return attribute.getKey() + "=\"" + attribute.getValue() + "\"";
            }
        }
        return null;
    }

    /** True if this element or any ancestor carries data-screen-sensitive. */
    private static boolean isScreenSensitive(Element element) {
        if (element.hasAttr("data-screen-sensitive")) {
            return true;
        }
        for (Element ancestor : element.parents()) {
            if (ancestor.hasAttr("data-screen-sensitive")) {
                return true;
            }
        }
        return false;
    }

    /** Line number of the expression in the source, for a message a human can act on. */
    private static int approximateLine(String source, String expression) {
        String needle = expression.substring(expression.indexOf('"') + 1, expression.length() - 1);
        int at = source.indexOf(needle);
        return at < 0 ? 0 : (int) source.substring(0, at).chars().filter(c -> c == '\n').count() + 1;
    }

    /**
     * A rule nobody can read is a rule that gets deleted. READ_ALOUD is the one section kind
     * a DM shows the table, and tagging it defeats screen safety entirely.
     */
    @Test
    void readAloudBlockIsNotTagged() throws IOException {
        String sections = Files.readString(TEMPLATES.resolve("adventure/_scene-sections.html"));
        int readAloudBlock = sections.indexOf("structured-read-aloud");
        assertThat(readAloudBlock).as("the read-aloud block must exist").isGreaterThan(-1);
        String openingTag = sections.substring(sections.lastIndexOf('<', readAloudBlock),
                sections.indexOf('>', readAloudBlock) + 1);
        assertThat(openingTag)
                .as("read-aloud is meant to be shown; tagging it defeats screen safety")
                .doesNotContain("data-screen-sensitive");
    }
}
