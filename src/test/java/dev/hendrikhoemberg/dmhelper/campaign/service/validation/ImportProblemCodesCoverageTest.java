package dev.hendrikhoemberg.dmhelper.campaign.service.validation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ImportProblemCodesCoverageTest {

    private static final Pattern CODE_LITERAL = Pattern.compile(
            "new CampaignImportProblem\\([^;]*?\"([A-Z][A-Z0-9_]+)\"");
    private static final Pattern ERROR_HELPER = Pattern.compile(
            "\\berror\\([^;]*?\"([A-Z][A-Z0-9_]+)\"");
    private static final Pattern WARNING_HELPER = Pattern.compile(
            "\\bwarning\\([^;]*?\"([A-Z][A-Z0-9_]+)\"");
    private static final Pattern PROBLEM_HELPER = Pattern.compile(
            "problem\\(\"([A-Z][A-Z0-9_]+)\"");

    @Test
    void everyEmittedCodeIsRegistered() throws Exception {
        Set<String> emitted = new HashSet<>();
        try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(path);
                for (Pattern p : new Pattern[]{CODE_LITERAL, ERROR_HELPER, WARNING_HELPER, PROBLEM_HELPER}) {
                    Matcher m = p.matcher(src);
                    while (m.find()) {
                        String code = m.group(1);
                        if (code.startsWith("SCHEMA_")) {
                            emitted.add("SCHEMA_VIOLATION");
                        } else {
                            emitted.add(code);
                        }
                    }
                }
            }
        }
        assertThat(emitted)
                .as("Validators still emit unregistered codes: update ImportProblemCodes")
                .isSubsetOf(ImportProblemCodes.all());
        assertThat(ImportProblemCodes.all()).isNotEmpty();
    }
}
