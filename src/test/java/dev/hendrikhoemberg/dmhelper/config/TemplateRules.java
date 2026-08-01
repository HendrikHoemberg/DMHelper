package dev.hendrikhoemberg.dmhelper.config;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/** Flat reader for the Thymeleaf templates, mirroring CssRules for markup contracts. */
final class TemplateRules {

    static final Path ROOT = Path.of("src/main/resources/templates");

    private TemplateRules() {
    }

    static List<Path> allTemplates() {
        try (Stream<Path> files = Files.walk(ROOT)) {
            return files.filter(path -> path.toString().endsWith(".html")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Templates that render a whole document rather than a fragment. A page template is one
     * whose file name does not start with "_" and that declares a body or delegates to the
     * shell.
     */
    static List<Path> pageTemplates() {
        return allTemplates().stream()
                .filter(path -> !path.getFileName().toString().startsWith("_"))
                .filter(path -> {
                    String markup = read(path);
                    return markup.contains("<body") || markup.contains("fragments/_shell");
                })
                .toList();
    }

    static String read(Path template) {
        try {
            return Files.readString(template);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parsed with the XML parser so Thymeleaf attributes survive intact. */
    static Document parse(Path template) {
        return Jsoup.parse(read(template), "", Parser.xmlParser());
    }
}
