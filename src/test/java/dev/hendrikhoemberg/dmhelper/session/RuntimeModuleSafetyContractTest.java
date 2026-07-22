package dev.hendrikhoemberg.dmhelper.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every runtime module inserted into or declared on the cockpit page must carry a stable
 * {@code data-runtime-module} key and a {@code data-table-safe-behavior} attribute.
 *
 * <p>The test parses cockpit.html and every fragment that can be inserted into it, collects
 * all declarations, and asserts:
 * <ul>
 *   <li>All expected module keys are present and stable (no duplicates, no empty keys)</li>
 *   <li>Every declared behavior is one of the three valid values</li>
 *   <li>No module declares an unknown behavior</li>
 * </ul>
 */
class RuntimeModuleSafetyContractTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Path COCKPIT = TEMPLATES.resolve("session/cockpit.html");

    /** Module keys that must exist on the cockpit page or its inserted fragments. */
    private static final Set<String> EXPECTED_MODULES = Set.of(
            "story", "map", "encounter", "session-plan", "party",
            "quick-notes", "presentation", "audio", "session-log"
    );

    /** Valid table-safe behavior values. */
    private static final Set<String> VALID_BEHAVIORS = Set.of("FILTER", "HIDE", "PLAYER_PROJECTION");

    /** Regex to extract data-runtime-module declarations. */
    private static final Pattern MODULE_PATTERN = Pattern.compile(
            "data-runtime-module\\s*=\\s*\"([^\"]+)\"");

    /** Regex to extract data-table-safe-behavior declarations. */
    private static final Pattern BEHAVIOR_PATTERN = Pattern.compile(
            "data-table-safe-behavior\\s*=\\s*\"([^\"]+)\"");

    /** Fragments that can be inserted into the cockpit (by th:insert, th:replace or th:block). */
    private static final List<String> RUNTIME_FRAGMENTS = List.of(
            "session/_story-rail.html",
            "session/_encounter-rail.html",
            "session/_session-plan.html",
            "session/_empty-table.html",
            "session/_scene-picker.html",
            "session/_lifecycle-dialog.html",
            "session/_linked-tables.html",
            "session/_presentation-preview.html",
            "encounter/_tracker.html",
            "notes/_quicknotes-strip.html",
            "party/_summary-bar.html",
            "audio/_cockpit-widget.html"
    );

    @Test
    void allExpectedModulesHaveAUniqueStableKey() throws IOException {
        List<String> cockpitLines = Files.readAllLines(COCKPIT);
        String cockpitHtml = String.join("\n", cockpitLines);

        Set<String> foundKeys = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        List<String> emptyKeys = new ArrayList<>();

        Matcher m = MODULE_PATTERN.matcher(cockpitHtml);
        while (m.find()) {
            String key = m.group(1);
            if (key.isEmpty()) emptyKeys.add("empty key in cockpit.html");
            else if (!foundKeys.add(key)) duplicates.add(key);
        }

        // Also scan all runtime fragments
        for (String fragment : RUNTIME_FRAGMENTS) {
            Path fragmentPath = TEMPLATES.resolve(fragment);
            if (!Files.exists(fragmentPath)) continue;
            String content = Files.readString(fragmentPath);
            Matcher fm = MODULE_PATTERN.matcher(content);
            while (fm.find()) {
                String key = fm.group(1);
                if (key.isEmpty()) emptyKeys.add("empty key in " + fragment);
                else if (!foundKeys.add(key)) duplicates.add(key);
            }
        }

        assertThat(emptyKeys)
                .as("all data-runtime-module values must be non-empty")
                .isEmpty();
        assertThat(duplicates)
                .as("data-runtime-module keys must be unique across the cockpit and its fragments")
                .isEmpty();
        assertThat(foundKeys)
                .as("all expected runtime modules must be declared with data-runtime-module")
                .containsAll(EXPECTED_MODULES);
    }

    @Test
    void everyModuleDeclaresAValidTableSafeBehavior() throws IOException {
        List<String> allContent = new ArrayList<>();

        // Collect cockpit.html content
        allContent.add(Files.readString(COCKPIT));

        // Collect all fragment content
        for (String fragment : RUNTIME_FRAGMENTS) {
            Path fragmentPath = TEMPLATES.resolve(fragment);
            if (Files.exists(fragmentPath)) {
                allContent.add(Files.readString(fragmentPath));
            }
        }

        String combined = String.join("\n", allContent);

        // Every data-runtime-module must have a sibling or nearby data-table-safe-behavior
        Matcher moduleMatcher = MODULE_PATTERN.matcher(combined);
        List<String> missingBehavior = new ArrayList<>();
        List<String> invalidBehavior = new ArrayList<>();

        while (moduleMatcher.find()) {
            String key = moduleMatcher.group(1);
            int keyPos = moduleMatcher.start();

            // Find the element tag that contains this declaration
            int tagStart = combined.lastIndexOf('<', keyPos);
            int tagEnd = combined.indexOf('>', keyPos);
            if (tagStart < 0 || tagEnd < 0) continue;

            String tagContent = combined.substring(tagStart, tagEnd + 1);
            Matcher behaviorMatcher = BEHAVIOR_PATTERN.matcher(tagContent);

            if (behaviorMatcher.find()) {
                String behavior = behaviorMatcher.group(1);
                if (!VALID_BEHAVIORS.contains(behavior)) {
                    invalidBehavior.add(key + " has invalid behavior: " + behavior);
                }
            } else {
                missingBehavior.add(key + " is missing data-table-safe-behavior");
            }
        }

        assertThat(missingBehavior)
                .as("every data-runtime-module must declare data-table-safe-behavior")
                .isEmpty();
        assertThat(invalidBehavior)
                .as("all data-table-safe-behavior values must be one of " + VALID_BEHAVIORS)
                .isEmpty();
    }

    @Test
    void everyFragmentUsedInCockpitHasModuleDeclaration() throws IOException {
        String cockpit = Files.readString(COCKPIT);

        // Find all th:insert and th:replace that reference fragments
        Pattern fragmentRef = Pattern.compile("th:(insert|replace)=\\\"~\\{([^}]+)\"");
        Matcher refMatcher = fragmentRef.matcher(cockpit);

        List<String> missingModule = new ArrayList<>();

        while (refMatcher.find()) {
            String ref = refMatcher.group(2);
            // Only check template fragments, not static fragments like fragments/navbar
            if (ref.startsWith("session/") || ref.startsWith("encounter/")
                    || ref.startsWith("notes/") || ref.startsWith("party/")
                    || ref.startsWith("audio/")) {

                String fragFile = ref.split("::")[0].trim() + ".html";
                Path fragPath = TEMPLATES.resolve(fragFile);

                if (Files.exists(fragPath)) {
                    String content = Files.readString(fragPath);
                    if (!content.contains("data-runtime-module")) {
                        missingModule.add(fragFile + " (referenced by " + ref + ")");
                    }
                }
            }
        }

        assertThat(missingModule)
                .as("all runtime fragments inserted into the cockpit must declare data-runtime-module")
                .isEmpty();
    }

    @Test
    void screenSafetyToggleExistsOnCockpitAndNavbar() throws IOException {
        String cockpit = Files.readString(COCKPIT);
        String navbar = Files.readString(TEMPLATES.resolve("fragments/navbar.html"));

        assertThat(cockpit)
                .as("cockpit must have a screen safety toggle with id screenSafetyCheckbox")
                .contains("screenSafetyCheckbox");
        assertThat(navbar)
                .as("navbar must have a screen safety toggle with id screenSafetyCheckbox")
                .contains("screenSafetyCheckbox");
    }

    @Test
    void noDmModeTerminologyRemains() throws IOException {
        try (Stream<Path> files = Files.walk(TEMPLATES)) {
            List<Path> htmlFiles = files.filter(p -> p.toString().endsWith(".html")).toList();

            // cockpit.html and navbar.html use their own inline scripts; check those too
            for (Path htmlFile : htmlFiles) {
                String content = Files.readString(htmlFile);
                assertThat(content)
                        .as("DM Mode terminology must not appear in " + htmlFile.getFileName())
                        .doesNotContain("dmMode", "dm-mode", "DM Mode", "DM_MODE", "DmMode");
            }
        }
    }

    @Test
    void screenSafetyEventNamesAreUsed() throws IOException {
        String js = Files.readString(Path.of("src/main/resources/static/js/session-cockpit.js"));
        String safetyJs = Files.readString(Path.of("src/main/resources/static/js/screen-safety.js"));
        String keyboardJs = Files.readString(Path.of("src/main/resources/static/js/keyboard.js"));

        assertThat(safetyJs)
                .as("the central screen-safety controller must dispatch screen-safety-changed")
                .contains("screen-safety-changed");
        assertThat(js)
                .as("session-cockpit.js must not duplicate the central change event")
                .doesNotContain("new CustomEvent('screen-safety-changed'");
        assertThat(keyboardJs)
                .as("keyboard.js must dispatch screen-safety-toggle")
                .contains("screen-safety-toggle");
        assertThat(js)
                .as("session-cockpit.js must not reference dm-mode-changed")
                .doesNotContain("dm-mode-changed");
        assertThat(js)
                .as("session-cockpit.js must use screen safety terminology")
                .doesNotContain("dmMode")
                .contains("tableSafe");
    }
}
