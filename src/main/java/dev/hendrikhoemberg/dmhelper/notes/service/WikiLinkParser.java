package dev.hendrikhoemberg.dmhelper.notes.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class WikiLinkParser {

    private static final Pattern WIKI_LINK_PATTERN = Pattern.compile("\\[\\[([^\\]]+)\\]\\]");

    public record WikiLinkReference(String prefix, String title, String url, boolean resolved) {}

    public List<WikiLinkTarget> extractReferences(String markdown) {
        var targets = new ArrayList<WikiLinkTarget>();
        if (markdown == null || markdown.isBlank()) return targets;

        var matcher = WIKI_LINK_PATTERN.matcher(markdown);
        while (matcher.find()) {
            String raw = matcher.group(1).trim();
            String prefix = "NOTE";
            String title = raw;

            int colonIdx = raw.indexOf(':');
            if (colonIdx > 0) {
                String maybePrefix = raw.substring(0, colonIdx).toUpperCase();
                if (maybePrefix.equals("HANDOUT") || maybePrefix.equals("MAP")
                        || maybePrefix.equals("STATBLOCK") || maybePrefix.equals("ENCOUNTER")) {
                    prefix = maybePrefix;
                    title = raw.substring(colonIdx + 1).trim();
                }
            }

            if (!title.isBlank()) {
                targets.add(new WikiLinkTarget(prefix, title));
            }
        }
        return targets;
    }

    public record WikiLinkTarget(String targetType, String title) {}

    public String replaceLinks(String markdown, List<WikiLinkReference> refs) {
        if (markdown == null) return "";

        String result = markdown;
        for (var ref : refs) {
            String search;
            if (ref.prefix().equals("NOTE")) {
                search = "(?i)\\[\\[" + Pattern.quote(ref.title()) + "\\]\\]";
            } else {
                search = "(?i)\\[\\[" + Pattern.quote(ref.prefix() + ":" + ref.title()) + "\\]\\]";
            }
            String replacement;
            if (ref.url() != null && ref.resolved()) {
                replacement = "<a href=\"" + ref.url() + "\" class=\"wiki-link wiki-link-resolved\">" + ref.title() + "</a>";
            } else {
                replacement = "<span class=\"wiki-link wiki-link-broken\" title=\"Not found\">" + ref.title() + "</span>";
            }
            result = result.replaceAll(search, Matcher.quoteReplacement(replacement));
        }
        return result;
    }
}
