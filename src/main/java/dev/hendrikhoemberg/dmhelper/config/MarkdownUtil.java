package dev.hendrikhoemberg.dmhelper.config;

import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.CoreHtmlNodeRenderer;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component("markdownUtil")
public class MarkdownUtil {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder()
            .escapeHtml(false)
            .nodeRendererFactory(ReadAloudNodeRenderer::new)
            .build();

    public String toHtml(String markdown) {
        if (markdown == null) return "";
        return renderer.render(parser.parse(markdown));
    }

    static class ReadAloudNodeRenderer implements NodeRenderer {

        private final HtmlNodeRendererContext context;
        private final HtmlWriter html;

        ReadAloudNodeRenderer(HtmlNodeRendererContext context) {
            this.context = context;
            this.html = context.getWriter();
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(FencedCodeBlock.class);
        }

        @Override
        public void render(Node node) {
            FencedCodeBlock block = (FencedCodeBlock) node;
            String info = block.getInfo();
            if (info == null || !info.trim().equalsIgnoreCase("read-aloud")) {
                new CoreHtmlNodeRenderer(context).render(node);
                return;
            }
            html.line();
            html.tag("div", Map.of("class", "read-aloud"));
            String literal = block.getLiteral() == null ? "" : block.getLiteral();
            for (String para : literal.split("\\n\\s*\\n")) {
                if (para.isBlank()) continue;
                html.tag("p");
                html.text(para.trim());
                html.tag("/p");
            }
            html.tag("/div");
            html.line();
        }
    }
}
