package dev.hendrikhoemberg.dmhelper.common.web;

import java.util.Map;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.RequestAttributes;

/**
 * Keeps framework internals out of error responses.
 *
 * <p>The {@code server.error.include-stacktrace} / {@code include-message}
 * properties are not honoured for {@link org.springframework.web.servlet.resource.NoResourceFoundException}
 * on Spring Boot 4 — a stray URL still serialised a full Java stack trace to
 * JSON clients. We strip the sensitive keys unconditionally so no error path,
 * HTML or JSON, can leak them regardless of that property.
 */
@Component
public class SafeErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
        Object correlationId = webRequest.getAttribute(
                CorrelationIdFilter.ATTRIBUTE, WebRequest.SCOPE_REQUEST);
        if (correlationId != null) {
            attributes.put(CorrelationIdFilter.ATTRIBUTE, correlationId);
        }
        attributes.remove("trace");
        attributes.remove("exception");
        attributes.remove("message");
        return attributes;
    }
}
