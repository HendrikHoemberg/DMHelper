package dev.hendrikhoemberg.dmhelper.config;

import org.springframework.stereotype.Component;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

import java.util.Set;

/**
 * Exposes {@link EnumLabelUtil} to templates as the {@code #enums} expression object,
 * so badges read "Not Started" instead of NOT_STARTED.
 *
 * <p>See {@link MarkdownDialect} for why an expression object is required rather than a
 * Spring bean reference: Thymeleaf 3.1 evaluates th:text in a restricted context that
 * forbids ${@bean...}, object instantiation and static access.
 */
@Component
public class EnumLabelDialect extends AbstractDialect implements IExpressionObjectDialect {

    private static final String NAME = "enums";

    private final IExpressionObjectFactory factory;

    public EnumLabelDialect() {
        super("enums");
        // Stateless helper with no dependencies, so the dialect owns its own instance. This
        // keeps it working in sliced contexts (@WebMvcTest) that auto-include IDialect beans
        // but not arbitrary @Components.
        EnumLabelUtil util = new EnumLabelUtil();
        this.factory = new IExpressionObjectFactory() {
            @Override
            public Set<String> getAllExpressionObjectNames() {
                return Set.of(NAME);
            }

            @Override
            public Object buildObject(IExpressionContext context, String expressionObjectName) {
                return util;
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
