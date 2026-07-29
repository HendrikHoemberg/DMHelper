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
            "`([A-Z][A-Za-z0-9_]*Test(?:[$.][A-Za-z][A-Za-z0-9_]*)*(?:#[A-Za-z_][A-Za-z0-9_]*)?)`");
    private static final Pattern SECTION = Pattern.compile(
            "(?ms)^##\\s+§%s\\b.*?(?=^##\\s+|\\z)");
    private static final Pattern CLASS_DECLARATION = Pattern.compile(
            "\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b");

    private record RequirementRow(String label, String proofCell) {
    }

    private record ProofReference(String raw, String outerClass, List<String> nestedClasses, String method) {
    }

    private record RequirementTable(int headerLine, List<String> headers, int proofColumn, boolean validSeparator) {
    }

    private record ClassScope(int bodyStart, int bodyEnd) {
    }

    @Test
    void everyRequirementInSection11HasARow() throws IOException {
        String index = Files.readString(INDEX);

        assertThat(requirementRows(index, "11.1").stream().map(RequirementRow::label).toList())
                .containsExactly("11.1.1", "11.1.2", "11.1.3", "11.1.4", "11.1.7", "11.1.8", "11.1.9");
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
                .containsExactlyInAnyOrder(
                        "ReleaseRehearsalFixtureTest",
                        "ReleaseRehearsalTest$RehearsalSteps",
                        "ReleaseRehearsalTest$RehearsalSteps#step1_readinessReportIsInspectedAndClear",
                        "ReleaseRehearsalTest$RehearsalSteps#step2_sessionStartsAtTheSelectedScene",
                        "ReleaseRehearsalTest$RehearsalSteps#step3_explorationAndABranchAreNavigatedWithoutLeavingTheCockpit",
                        "ReleaseRehearsalTest$RehearsalSteps#step4_theEncounterIsPreparedAndRunThroughTheReadinessFlow",
                        "ReleaseRehearsalTest$RehearsalSteps#step5_initiativeDamageConditionsDefeatAndTurnsResolve",
                        "ReleaseRehearsalTest$RehearsalSteps#step6_statblocksAndRulesAreConsultedInsideDmhelper",
                        "ReleaseRehearsalTest$RehearsalSteps#step7_notesAreCapturedAndThePlanIsUpdated",
                                                "ReleaseRehearsalTest$RehearsalSteps#step9_theEncounterAndSessionAreCompleted",
                        "ReleaseRehearsalTest$RehearsalSteps#step10_theGeneratedLogAgreesWithWhatHappened");
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

    @Test
    void aMethodDeclaredOutsideTheReferencedNestedClassIsRejected() {
        String index = """
                ## §11.1 Automated coverage

                | Req | Requirement | Proved by |
                |---|---|---|
                | 11.1.1 | rehearsal | `ReleaseRehearsalTest$Linear#step1_readinessReportIsInspectedAndClear` |
                """;

        assertThat(proofReferenceProblems(index))
                .as("inherited methods are not proof for the referencing nested class")
                .containsExactly("ReleaseRehearsalTest$Linear#step1_readinessReportIsInspectedAndClear");
    }

    @Test
    void explicitNestedClassMethodReferencesResolveToTheNestedClassBody() {
        String index = """
                ## §11.1 Automated coverage

                | Req | Requirement | Proved by |
                |---|---|---|
                | 11.1.1 | rehearsal | `ReleaseRehearsalTest$RehearsalSteps#step1_readinessReportIsInspectedAndClear`, `ReleaseRehearsalTest.RehearsalSteps#step2_sessionStartsAtTheSelectedScene` |
                """;

        assertThat(executableReferences(index))
                .containsExactly(
                        "ReleaseRehearsalTest$RehearsalSteps#step1_readinessReportIsInspectedAndClear",
                        "ReleaseRehearsalTest.RehearsalSteps#step2_sessionStartsAtTheSelectedScene");
        assertThat(proofReferenceProblems(index)).isEmpty();
    }

    @Test
    void aMalformedRequirementRowIsRejectedInsteadOfOmitted() {
        String index = """
                ## §11.1 Automated coverage

                | Req | Requirement | Proved by |
                |---|---|---|
                | 11.1.1 | malformed row |
                """;

        assertThat(requirementRowsWithoutExecutableProofs(index))
                .as("malformed rows must remain visible to validation")
                .containsExactly("11.1.1");
    }

    @Test
    void everySection11RequirementRowIsWellFormed() throws IOException {
        assertThat(requirementRowProblems(Files.readString(INDEX)))
                .as("section 11 requirement rows must be parseable and executable")
                .isEmpty();
    }

    @Test
    void aRequirementRowWithUnexpectedColumnsIsRejected() {
        String index = """
                ## §11.1 Automated coverage

                | Req | Requirement | Proved by |
                |---|---|---|
                | 11.1.1 | malformed row | `ReadinessProductionParityTest` | unexpected |
                """;

        assertThat(requirementRowProblems(index))
                .containsExactly("§11.1 row 5 (11.1.1): expected 3 columns but found 4");
    }

    private static List<String> proofReferenceProblems(String index) {
        Map<String, List<Path>> sourcesByClass = testSourcesByClass();
        List<String> problems = new ArrayList<>();
        for (String reference : executableReferences(index)) {
            ProofReference target = parseReference(reference);
            List<Path> sources = sourcesByClass.getOrDefault(target.outerClass(), List.of());
            if (sources.isEmpty()) {
                problems.add(reference);
                continue;
            }

            boolean resolved = sources.stream().anyMatch(source -> {
                String scope = sourceScope(source, target.outerClass(), target.nestedClasses());
                return scope != null && (target.method() == null || declaresMethod(scope, target.method()));
            });
            if (!resolved) problems.add(reference);
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
        for (String problem : requirementRowProblems(index)) {
            Matcher matcher = Pattern.compile("\\(([^)]*)\\):").matcher(problem);
            if (matcher.find() && !missing.contains(matcher.group(1))) missing.add(matcher.group(1));
        }
        return missing;
    }

    private static List<RequirementRow> requirementRows(String index, String section) {
        List<String> lines = sectionBody(index, section).lines().toList();
        List<RequirementRow> rows = new ArrayList<>();
        for (RequirementTable table : requirementTables(lines)) {
            if (!table.validSeparator()) continue;
            for (int rowIndex = table.headerLine() + 2;
                 rowIndex < lines.size() && isTableRow(lines.get(rowIndex)); rowIndex++) {
                List<String> cells = tableCells(lines.get(rowIndex));
                if (cells.size() == table.headers().size() && !isSeparatorRow(cells)) {
                    rows.add(new RequirementRow(cells.get(0), cells.get(table.proofColumn())));
                }
            }
        }
        return rows;
    }

    private static List<String> requirementRowProblems(String index) {
        List<String> problems = new ArrayList<>();
        for (String section : List.of("11.1", "11.2")) {
            List<String> lines = sectionBody(index, section).lines().toList();
            for (RequirementTable table : requirementTables(lines)) {
                if (!table.validSeparator()) {
                    problems.add("§" + section + " header row " + (table.headerLine() + 1)
                            + ": expected a valid separator with " + table.headers().size() + " columns");
                    continue;
                }
                for (int rowIndex = table.headerLine() + 2; rowIndex < lines.size(); rowIndex++) {
                    String line = lines.get(rowIndex);
                    if (line.isBlank() || line.trim().startsWith("## ")) break;
                    if (!isTableRow(line)) {
                        problems.add("§" + section + " row " + (rowIndex + 1)
                                + ": expected a pipe-delimited table row");
                        continue;
                    }

                    List<String> cells = tableCells(line);
                    String label = cells.isEmpty() ? "<blank>" : cells.get(0);
                    String prefix = "§" + section + " row " + (rowIndex + 1) + " (" + label + "): ";
                    if (isSeparatorRow(cells)) {
                        problems.add(prefix + "unrecognized separator row");
                    } else if (cells.size() != table.headers().size()) {
                        problems.add(prefix + "expected " + table.headers().size()
                                + " columns but found " + cells.size());
                    } else if (label.isBlank()) {
                        problems.add(prefix + "requirement label must not be blank");
                    } else if (executableReferences(cells.get(table.proofColumn())).isEmpty()) {
                        problems.add(prefix + "Proved by must contain at least one recognized executable proof reference");
                    }
                }
            }
        }
        return problems;
    }

    private static List<RequirementTable> requirementTables(List<String> lines) {
        List<RequirementTable> tables = new ArrayList<>();
        for (int i = 0; i + 1 < lines.size(); i++) {
            if (!isTableRow(lines.get(i)) || !isTableRow(lines.get(i + 1))) continue;
            List<String> headers = tableCells(lines.get(i));
            int proofColumn = headers.indexOf("Proved by");
            if (proofColumn < 0) continue;
            List<String> separator = tableCells(lines.get(i + 1));
            tables.add(new RequirementTable(i, headers, proofColumn,
                    separator.size() == headers.size() && isSeparatorRow(separator)));
        }
        return tables;
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

    private static ProofReference parseReference(String reference) {
        int separator = reference.indexOf('#');
        String classReference = separator >= 0 ? reference.substring(0, separator) : reference;
        String method = separator >= 0 ? reference.substring(separator + 1) : null;
        int dollar = classReference.indexOf('$');
        int dot = classReference.indexOf('.');
        int nestedStart = dollar < 0 ? dot : dot < 0 ? dollar : Math.min(dollar, dot);
        String outerClass = nestedStart < 0 ? classReference : classReference.substring(0, nestedStart);
        List<String> nestedClasses = nestedStart < 0
                ? List.of()
                : Stream.of(classReference.substring(nestedStart + 1).split("[$.]"))
                        .filter(name -> !name.isBlank())
                        .toList();
        return new ProofReference(reference, outerClass, nestedClasses, method);
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

    private static String sourceScope(Path source, String outerClass, List<String> nestedClasses) {
        try {
            String contents = Files.readString(source);
            String masked = maskNonCode(contents);
            ClassScope scope = findClassScope(masked, outerClass, 0, masked.length());
            if (scope == null) return null;
            for (String nestedClass : nestedClasses) {
                scope = findClassScope(masked, nestedClass, scope.bodyStart() + 1, scope.bodyEnd());
                if (scope == null) return null;
            }
            return contents.substring(scope.bodyStart() + 1, scope.bodyEnd());
        } catch (IOException exception) {
            throw new IllegalStateException("Could not inspect test source " + source, exception);
        }
    }

    private static ClassScope findClassScope(String masked, String className, int start, int end) {
        Matcher matcher = CLASS_DECLARATION.matcher(masked);
        matcher.region(start, end);
        while (matcher.find()) {
            if (!matcher.group(1).equals(className)) continue;
            int openingBrace = masked.indexOf('{', matcher.end());
            int semicolon = masked.indexOf(';', matcher.end());
            if (openingBrace < 0 || openingBrace >= end || (semicolon >= 0 && semicolon < openingBrace)) continue;
            int closingBrace = matchingBrace(masked, openingBrace, end);
            if (closingBrace >= 0) return new ClassScope(openingBrace, closingBrace);
        }
        return null;
    }

    private static int matchingBrace(String masked, int openingBrace, int end) {
        int depth = 0;
        for (int i = openingBrace; i < end; i++) {
            if (masked.charAt(i) == '{') depth++;
            if (masked.charAt(i) == '}' && --depth == 0) return i;
        }
        return -1;
    }

    private static boolean declaresMethod(String sourceScope, String methodName) {
        String masked = maskNonCode(sourceScope);
        String declaration = "(?m)^[ \\t]*(?:(?:public|protected|private|static|final|synchronized|native|abstract|default)[ \\t]+)*"
                + "(?:<[^\\n>]+>[ \\t]+)?[\\w$<>\\[\\],.? ]+[ \\t]+"
                + Pattern.quote(methodName) + "\\s*\\(";
        Matcher matcher = Pattern.compile(declaration).matcher(masked);
        while (matcher.find()) {
            if (braceDepth(masked, matcher.start()) == 0) return true;
        }
        return false;
    }

    private static int braceDepth(String source, int position) {
        int depth = 0;
        for (int i = 0; i < position; i++) {
            if (source.charAt(i) == '{') depth++;
            if (source.charAt(i) == '}') depth--;
        }
        return depth;
    }

    private static String maskNonCode(String source) {
        StringBuilder masked = new StringBuilder(source.length());
        int state = 0;
        for (int i = 0; i < source.length(); i++) {
            char current = source.charAt(i);
            if (state == 0) {
                if (source.startsWith("//", i)) {
                    masked.append("  ");
                    i++;
                    state = 1;
                } else if (source.startsWith("/*", i)) {
                    masked.append("  ");
                    i++;
                    state = 2;
                } else if (source.startsWith("\"\"\"", i)) {
                    masked.append("   ");
                    i += 2;
                    state = 3;
                } else if (current == '\"') {
                    masked.append(' ');
                    state = 4;
                } else if (current == '\'') {
                    masked.append(' ');
                    state = 5;
                } else {
                    masked.append(current);
                }
            } else if (state == 1) {
                masked.append(current == '\n' ? '\n' : ' ');
                if (current == '\n') state = 0;
            } else if (state == 2) {
                masked.append(current == '\n' ? '\n' : ' ');
                if (current == '*' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                    masked.append(' ');
                    i++;
                    state = 0;
                }
            } else if (state == 3) {
                if (source.startsWith("\"\"\"", i)) {
                    masked.append("   ");
                    i += 2;
                    state = 0;
                } else {
                    masked.append(current == '\n' ? '\n' : ' ');
                }
            } else {
                masked.append(current == '\n' ? '\n' : ' ');
                if (current == '\\' && i + 1 < source.length()) {
                    masked.append(source.charAt(++i) == '\n' ? '\n' : ' ');
                } else if ((state == 4 && current == '\"') || (state == 5 && current == '\'')) {
                    state = 0;
                }
            }
        }
        return masked.toString();
    }
}
