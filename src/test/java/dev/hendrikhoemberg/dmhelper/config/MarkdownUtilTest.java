package dev.hendrikhoemberg.dmhelper.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownUtilTest {

    private final MarkdownUtil markdownUtil = new MarkdownUtil();

    @Test
    void rendersReadAloudFenceAsBoxedDiv() {
        String md = """
                Before text.

                ```read-aloud
                Gilded amber pillars rise to a vaulted ceiling.

                A voice echoes: "Kneel."
                ```

                After text.
                """;
        String html = markdownUtil.toHtml(md);

        assertThat(html).contains("<div class=\"read-aloud\">");
        assertThat(html).contains("<p>Gilded amber pillars rise to a vaulted ceiling.</p>");
        assertThat(html).contains("<p>A voice echoes: &quot;Kneel.&quot;</p>");
        assertThat(html).contains("</div>");
        assertThat(html).doesNotContain("<pre>");
    }

    @Test
    void escapesHtmlInsideReadAloud() {
        String html = markdownUtil.toHtml("```read-aloud\n<script>alert(1)</script>\n```");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void ordinaryCodeFencesStillRenderAsCode() {
        String html = markdownUtil.toHtml("```\nplain code\n```");
        assertThat(html).contains("<pre>");
        assertThat(html).contains("plain code");
    }

    @Test
    void plainMarkdownUnchanged() {
        assertThat(markdownUtil.toHtml("# Title")).contains("<h1>Title</h1>");
        assertThat(markdownUtil.toHtml(null)).isEmpty();
    }

    @Test
    void escapesRawHtmlInOrdinaryMarkdown() {
        String html = markdownUtil.toHtml("# Safe\n<script>alert(1)</script><img src=x onerror=alert(2)>");
        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).doesNotContain("<img");
        assertThat(html).contains("&lt;img");
    }
}
