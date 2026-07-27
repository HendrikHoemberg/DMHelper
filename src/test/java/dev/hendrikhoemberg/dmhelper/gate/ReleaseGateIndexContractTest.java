package dev.hendrikhoemberg.dmhelper.gate;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 2026-07-22 section 11: "No plan may claim the overall premise until section 11 passes."
 * The index is how that claim is checked, so every test class it names must exist.
 */
class ReleaseGateIndexContractTest {

    private static final Path INDEX = Path.of("docs/product/all-in-one-release-gate.md");
    private static final Path TEST_SOURCE_ROOT = Path.of("src/test/java");
    private static final Pattern TEST_REFERENCE = Pattern.compile(
            "`([A-Z][A-Za-z0-9_]*Test(?:#[A-Za-z_][A-Za-z0-9_]*)?)`");
    private static final Pattern SECTION = Pattern.compile(
            "(?ms)^##\\s+§%s\\b.*?(?=^##\\s+|\\z)");

    private record RequirementRow(String label, String proofCell) {
    }

    @Test
    void everyRequirementInSection11HasARow() throws IOException {
        String index = Files.readString(INDEX);

        assertThat(requirementRows(index, "11.1").stream().map(RequirementRow::label).toList())
                .containsExactly("11.1.1", "11.1.2", "11.1.3", "11.1.4", "11.1.5", "11.1.6", "11.1.7", "11.1.8");
        assertThat(requirementRows(index, "11.2").stream().map(RequirementRow::label).toList())
                .containsExactly(
                        "No document-level scrolling",
                        "Modules do not overlap or clip",
                        "Command bar fully reachable",
                        "Minimum module sizes respected",
                        "Keyboard: edit mode, tabs, focus/restore, splitters",
                        "Focus visible and restored after dialogs",
                        "Reduced motion respected");
    }

    @Test
    void everyProofReferenceResolvesToAnExistingClassAndMethod() throws IOException {
        assertThat(proofReferenceProblems(Files.readString(INDEX)))
                .as("indexed proof references that no longer resolve")
                .isEmpty();
    }

    @Test
    void everySection11RequirementRowHasAnExecutableProofReference() throws IOException {
        assertThat(requirementRowsWithoutExecutableProofs(Files.readString(INDEX)))
                .as("section 11 requirement rows without executable proof references")
                .isEmpty();
    }

    @Test
    void theReleaseRehearsalClaimUsesTheExecutableTestAndItsStepMethods() throws IOException {
        List<String> references = executableReferences(sectionBody(Files.readString(INDEX), "11.3"));

        assertThat(references)
                .contains("ReleaseRehearsalTest")
                .containsExactlyInAnyOrder(
                        "ReleaseRehearsalTest",
                        "ReleaseRehearsalFixtureTest",
                        "ReleaseRehearsalTest#step1_readinessReportIsInspectedAndClear",
                        "ReleaseRehearsalTest#step2_sessionStartsAtTheSelectedScene",
                        "ReleaseRehearsalTest#step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit",
                        "ReleaseRehearsalTest#step4_theEncounterIsCreatedFromTheSceneInAtMostTwoActions",
                        "ReleaseRehearsalTest#step5_initiativeDamageConditionsDefeatAndTurnsResolve",
                        "ReleaseRehearsalTest#step6_statblocksAndRulesAreConsultedInsideDmhelper",
                        "ReleaseRehearsalTest#step7_notesAreCapturedAndThePlanIsUpdated",
                        "ReleaseRehearsalTest#step8_aReviewedPlayerSafeAssetIsPresentedAndTheDisplayAgrees",
                        "ReleaseRehearsalTest#step9_theEncounterAndSessionAreCompleted",
                        "ReleaseRehearsalTest#step10_theGeneratedLogAgreesWithWhatHappened");
    }

    @Test
    void theIndexIsLinkedFromTheProductDocs() throws IOException {
        assertThat(Files.readString(Path.of("docs/product/README.md")))
                .contains("all-in-one-release-gate.md");
    }

    @Test
    void aProofReferenceToAMissingClassIsRejected() {
        String index = """
                ## §11.1 Automated coverage

                | Req | Requirement | Proved by |
                |---|---|---|
                | 11.1.1 | parity | `MissingProofTest` |
                """;

        assertThat(proofReferenceProblems(index))
                .containsExactly("MissingProofTest");
    }

    @Test
    void aProofReferenceToAMissingMethodIsRejected() {
        String index = """
                ## §11.2 Viewport and accessibility gate

                | Requirement | Proved by |
                |---|---|
                | No scrolling | `ViewportAccessibilityGateTest#missingMethod` |
                """;

        assertThat(proofReferenceProblems(index))
                .containsExactly("ViewportAccessibilityGateTest#missingMethod");
    }

    @Test
    void aRequirementRowWithoutAnExecutableProofReferenceIsRejected() {
        String index = """
                ## §11.1 Automated coverage

                | Req | Requirement | Proved by |
                |---|---|---|
                | 11.1.1 | parity | prose only |

                ## §11.2 Viewport and accessibility gate

                | Requirement | Proved by |
                |---|---|
                | No scrolling | no executable proof |
                """;

        assertThat(requirementRowsWithoutExecutableProofs(index))
                .containsExactly("11.1.1", "No scrolling");
    }

    @Test
    void validMultiClassRowsAndNestedMethodReferencesRemainExecutable() {
        String index = """
                ## §11.1 Automated coverage

                | Req | Requirement | Proved by |
                |---|---|---|
                | 11.1.1 | parity | `ReadinessProductionParityTest`, `FullPageRenderSmokeTest` |

                ## §11.2 Viewport and accessibility gate

                | Requirement | Proved by |
                |---|---|
                | No scrolling | `ViewportAccessibilityGateTest#theCockpitNeverScrollsTheDocument` |
                """;

        assertThat(proofReferenceProblems(index)).isEmpty();
        assertThat(requirementRowsWithoutExecutableProofs(index)).isEmpty();
    }

    private static List<String> proofReferenceProblems(String index) {
        Map<String, List<Path>> sourcesByClass = testSourcesByClass();
        List<String> problems = new ArrayList<>();
        for (String reference : executableReferences(index)) {
            String className = reference.substring(0, reference.indexOf('#') >= 0
                    ? reference.indexOf('#') : reference.length());
            List<Path> sources = sourcesByClass.getOrDefault(className, List.of());
            if (sources.isEmpty()) {
                problems.add(reference);
                continue;
            }

            int separator = reference.indexOf('#');
            if (separator >= 0) {
                String methodName = reference.substring(separator + 1);
                boolean declared = sources.stream().anyMatch(source -> declaresMethod(source, methodName));
                if (!declared) problems.add(reference);
            }
        }
        return problems;
    }

    private static List<String> requirementRowsWithoutExecutableProofs(String index) {
        List<String> missing = new ArrayList<>();
        for (String section : List.of("11.1", "11.2")) {
            for (RequirementRow row : requirementRows(index, section)) {
                if (executableReferences(row.proofCell()).isEmpty()) missing.add(row.label());
            }
        }
        return missing;
    }

    private static List<RequirementRow> requirementRows(String index, String section) {
        List<String> lines = sectionBody(index, section).lines().toList();
        List<RequirementRow> rows = new ArrayList<>();
        for (int i = 0; i + 1 < lines.size(); i++) {
            if (!isTableRow(lines.get(i)) || !isTableRow(lines.get(i + 1))) continue;
            List<String> headers = tableCells(lines.get(i));
            List<String> separator = tableCells(lines.get(i + 1));
            int proofColumn = headers.indexOf("Proved by");
            if (proofColumn < 0 || !isSeparatorRow(separator)) continue;

            for (int rowIndex = i + 2; rowIndex < lines.size() && isTableRow(lines.get(rowIndex)); rowIndex++) {
                List<String> cells = tableCells(lines.get(rowIndex));
                if (cells.size() > proofColumn && !isSeparatorRow(cells)) {
                    rows.add(new RequirementRow(cells.get(0), cells.get(proofColumn)));
                }
            }
        }
        return rows;
    }

    private static String sectionBody(String index, String section) {
        Matcher matcher = Pattern.compile(SECTION.pattern().formatted(Pattern.quote(section))).matcher(index);
        return matcher.find() ? matcher.group() : "";
    }

    private static boolean isTableRow(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("|") && trimmed.endsWith("|");
    }

    private static List<String> tableCells(String line) {
        String trimmed = line.trim();
        return Stream.of(trimmed.substring(1, trimmed.length() - 1).split("\\|", -1))
                .map(String::trim)
                .toList();
    }

    private static boolean isSeparatorRow(List<String> cells) {
        return !cells.isEmpty() && cells.stream().allMatch(cell -> cell.matches(":?-{3,}:?"));
    }

    private static List<String> executableReferences(String markdown) {
        Set<String> references = new LinkedHashSet<>();
        Matcher matcher = TEST_REFERENCE.matcher(markdown);
        while (matcher.find()) references.add(matcher.group(1));
        return new ArrayList<>(references);
    }

    private static Map<String, List<Path>> testSourcesByClass() {
        Map<String, List<Path>> sourcesByClass = new LinkedHashMap<>();
        try (Stream<Path> sources = Files.walk(TEST_SOURCE_ROOT)) {
            sources.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> sourcesByClass
                            .computeIfAbsent(path.getFileName().toString().replaceFirst("\\.java$", ""), ignored -> new ArrayList<>())
                            .add(path));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not scan test sources", exception);
        }
        return sourcesByClass;
    }

    private static boolean declaresMethod(Path source, String methodName) {
        try {
            String declaration = "(?m)^[ \\t]*(?:(?:public|protected|private|static|final|synchronized|native|abstract|default)[ \\t]+)*"
                    + "(?:<[^\\n>]+>[ \\t]+)?[\\w$<>\\[\\],.? ]+[ \\t]+"
                    + Pattern.quote(methodName) + "\\s*\\(";
            return Pattern.compile(declaration).matcher(Files.readString(source)).find();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect test source " + source, exception);
        }
    }
}
