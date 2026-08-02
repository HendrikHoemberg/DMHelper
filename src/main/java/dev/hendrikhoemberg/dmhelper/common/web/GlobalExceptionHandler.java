package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.session.service.ActiveEncounterReplacementRequiredException;
import dev.hendrikhoemberg.dmhelper.session.service.EncounterNotReadyException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.HtmlUtils;

import java.net.URI;

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setProperty(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
        return problem;
    }

    private ResponseEntity<String> htmxError(HttpStatus status, String message, HttpServletRequest request) {
        String reference = CorrelationIdFilter.current(request);
        String fragment = "<div class=\"state state--failed\" role=\"alert\" aria-live=\"polite\">"
                + "<svg class=\"icon icon--24\" width=\"24\" height=\"24\" aria-hidden=\"true\" focusable=\"false\">"
                + "<use href=\"/icons/ui.svg#icon-alert-triangle\"></use></svg>"
                + "<p class=\"state__title\">" + HtmlUtils.htmlEscape(message) + "</p>"
                + "<span class=\"u-text-sm\"> Reference: "
                + HtmlUtils.htmlEscape(reference) + "</span></div>";
        return ResponseEntity.status(status).body(fragment);
    }

    /** True for full-page browser navigations, which need an HTML page rather than a JSON ProblemDetail. */
    private boolean prefersHtml(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    private ModelAndView htmlErrorPage(HttpStatus status, HttpServletRequest request) {
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", status.value());
        mav.addObject(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
        mav.setStatus(status);
        return mav;
    }

    @ExceptionHandler(ActiveEncounterReplacementRequiredException.class)
    public Object handleActiveEncounterReplacement(ActiveEncounterReplacementRequiredException ex,
                                                    HttpServletRequest request) {
        if ("true".equals(request.getHeader("HX-Request"))) {
            return htmxError(HttpStatus.CONFLICT, ex.getMessage(), request);
        }
        if (prefersHtml(request)) {
            return htmlErrorPage(HttpStatus.CONFLICT, request);
        }
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "urn:dmhelper:active-encounter-replacement",
                "Active Encounter Requires Disposition",
                ex.getMessage(),
                request);
        problem.setProperty("code", "ACTIVE_ENCOUNTER_REPLACEMENT_REQUIRED");
        problem.setProperty("activeEncounterId", ex.getActiveEncounterId().toString());
        problem.setProperty("activeEncounterName", ex.getActiveEncounterName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(EncounterNotReadyException.class)
    public Object handleEncounterNotReady(EncounterNotReadyException ex,
                                           HttpServletRequest request) {
        if ("true".equals(request.getHeader("HX-Request"))) {
            return htmxError(HttpStatus.CONFLICT, ex.getMessage(), request);
        }
        if (prefersHtml(request)) {
            return htmlErrorPage(HttpStatus.CONFLICT, request);
        }
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT,
                "urn:dmhelper:encounter-not-ready",
                "Encounter Not Ready",
                ex.getMessage(),
                request);
        problem.setProperty("code", "ENCOUNTER_NOT_READY");
        problem.setProperty("readiness", ex.getReadiness());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String message = "The request was not valid. Check the entered values and try again.";
        if ("true".equals(request.getHeader("HX-Request"))) {
            return htmxError(HttpStatus.BAD_REQUEST, message, request);
        }
        if (prefersHtml(request)) {
            return htmlErrorPage(HttpStatus.BAD_REQUEST, request);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem(
                HttpStatus.BAD_REQUEST,
                "urn:dmhelper:validation-error",
                "Validation Error",
                message,
                request));
    }

    @ExceptionHandler(NotFoundException.class)
    public Object handleNotFound(NotFoundException ex, HttpServletRequest request) {
        String message = "The requested item could not be found. Reload and try again.";
        if ("true".equals(request.getHeader("HX-Request"))) {
            return htmxError(HttpStatus.NOT_FOUND, message, request);
        }
        if (prefersHtml(request)) {
            return htmlErrorPage(HttpStatus.NOT_FOUND, request);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(
                HttpStatus.NOT_FOUND,
                "urn:dmhelper:not-found",
                "Not Found",
                message,
                request));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public Object handleConflict(OptimisticLockingFailureException ex, HttpServletRequest request) {
        String message = "The item changed before this request completed. Reload and try again.";
        if ("true".equals(request.getHeader("HX-Request"))) {
            return htmxError(HttpStatus.CONFLICT, message, request);
        }
        if (prefersHtml(request)) {
            return htmlErrorPage(HttpStatus.CONFLICT, request);
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(
                HttpStatus.CONFLICT,
                "urn:dmhelper:conflict",
                "Conflict",
                message,
                request));
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                           HttpStatusCode statusCode,
                                                           WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String detail = safeFrameworkDetail(status);
        ProblemDetail safeBody = ProblemDetail.forStatusAndDetail(status, detail);
        safeBody.setTitle(status.getReasonPhrase());
        safeBody.setType(URI.create("urn:dmhelper:http-" + status.value()));
        Object correlationId = request.getAttribute(
                CorrelationIdFilter.ATTRIBUTE, WebRequest.SCOPE_REQUEST);
        String reference = correlationId == null ? "unavailable" : correlationId.toString();
        safeBody.setProperty(CorrelationIdFilter.ATTRIBUTE, reference);
        if ("true".equals(request.getHeader("HX-Request"))) {
            HttpHeaders fragmentHeaders = new HttpHeaders();
            fragmentHeaders.putAll(headers);
            fragmentHeaders.setContentType(org.springframework.http.MediaType.TEXT_HTML);
            String fragment = "<div class=\"alert alert-error\" role=\"alert\">"
                    + "<span>" + HtmlUtils.htmlEscape(detail) + "</span>"
                    + "<span class=\"u-text-sm\"> Reference: "
                    + HtmlUtils.htmlEscape(reference) + "</span></div>";
            return new ResponseEntity<>(fragment, fragmentHeaders, statusCode);
        }
        return new ResponseEntity<>(safeBody, headers, statusCode);
    }

    private String safeFrameworkDetail(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "The request was not valid. Check the entered values and try again.";
            case NOT_FOUND -> "The requested item could not be found. Reload and try again.";
            case METHOD_NOT_ALLOWED -> "That action is not supported at this address.";
            case NOT_ACCEPTABLE -> "The requested response format is not available.";
            case CONTENT_TOO_LARGE -> "The uploaded content is too large.";
            case UNSUPPORTED_MEDIA_TYPE -> "The submitted content type is not supported.";
            default -> status.is4xxClientError()
                    ? "The request could not be accepted. Check it and try again."
                    : "The request could not be completed.";
        };
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request failure", ex);
        String message = "The request could not be completed.";
        if ("true".equals(request.getHeader("HX-Request"))) {
            return htmxError(HttpStatus.INTERNAL_SERVER_ERROR, message, request);
        }
        if (prefersHtml(request)) {
            return htmlErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "urn:dmhelper:request-failed",
                "Request Failed",
                message,
                request));
    }
}
