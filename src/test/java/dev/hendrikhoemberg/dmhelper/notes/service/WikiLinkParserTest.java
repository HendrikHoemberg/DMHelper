package dev.hendrikhoemberg.dmhelper.notes.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WikiLinkParserTest {

    private final WikiLinkParser parser = new WikiLinkParser();

    @Test
    void extractsSimpleWikiLink() {
        var refs = parser.extractReferences("See [[Goblin Cave]] for details.");
        assertEquals(1, refs.size());
        assertEquals("NOTE", refs.get(0).targetType());
        assertEquals("Goblin Cave", refs.get(0).title());
    }

    @Test
    void extractsPrefixedLinks() {
        var refs = parser.extractReferences("[[handout:The Regent's Letter]] [[map:Throne Room]] [[statblock:Goblin]]");
        assertEquals(3, refs.size());
        assertEquals("HANDOUT", refs.get(0).targetType());
        assertEquals("The Regent's Letter", refs.get(0).title());
        assertEquals("MAP", refs.get(1).targetType());
        assertEquals("Throne Room", refs.get(1).title());
        assertEquals("STATBLOCK", refs.get(2).targetType());
        assertEquals("Goblin", refs.get(2).title());
    }

    @Test
    void returnsEmptyForNoLinks() {
        var refs = parser.extractReferences("Just plain text.");
        assertTrue(refs.isEmpty());
    }

    @Test
    void handlesNull() {
        var refs = parser.extractReferences(null);
        assertTrue(refs.isEmpty());
    }

    @Test
    void convertsLinksToHtml() {
        var refs = List.of(
            new WikiLinkParser.WikiLinkReference("NOTE", "Goblin Cave", "/campaigns/x/notes/y", true),
            new WikiLinkParser.WikiLinkReference("NOTE", "Unknown", null, false)
        );
        String result = parser.replaceLinks(
            "See [[Goblin Cave]] and [[Unknown]].",
            refs
        );
        assertTrue(result.contains("<a href=\"/campaigns/x/notes/y\" class=\"wiki-link wiki-link-resolved\">Goblin Cave</a>"));
        assertTrue(result.contains("<span class=\"wiki-link wiki-link-broken\" title=\"Not found\">Unknown</span>"));
    }
}
