package dev.hendrikhoemberg.dmhelper.config;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

import java.util.Set;

/**
 * Exposes {@link MarkdownUtil} to templates as the {@code #markdown} expression object.
 *
 * <p>Thymeleaf 3.1 evaluates {@code th:text}/{@code th:utext} expressions in a restricted
 * context that forbids Spring bean references ({@code ${@markdownUtil.toHtml(...)}}), object
 * instantiation and static access. Expression objects ({@code #}-prefixed) are permitted in
 * that context, so templates call {@code ${#markdown.toHtml(...)}} instead.
 */
@Component
public class MarkdownDialect extends AbstractDialect implements IExpressionObjectDialect {

    private static final String NAME = "markdown";

    private final IExpressionObjectFactory factory;

    public MarkdownDialect(MarkdownUtil markdownUtil) {
        super("markdown");
        this.factory = new IExpressionObjectFactory() {
            @Override
            public Set<String> getAllExpressionObjectNames() {
                return Set.of(NAME);
            }

            @Override
            public Object buildObject(IExpressionContext context, String expressionObjectName) {
                return markdownUtil;
            }

            @Override
            public boolean isCacheable(String expressionObjectName) {
                return true;
            }
        };
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return factory;
    }
}
