package dev.hendrikhoemberg.dmhelper.common.web;

import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail,
                                  HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(type));
        problem.setTitle(title);
        problem.setProperty(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
        return problem;
    }

    private ModelAndView htmxError(HttpStatus status, String message, HttpServletRequest request) {
        ModelAndView mav = new ModelAndView("common/_error");
        mav.addObject("message", message);
        mav.addObject(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
        mav.setStatus(status);
        return mav;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Object handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String message = "The request was not valid. Check the entered values and try again.";
        if ("true".equals(request.getHeader("HX-Request"))) {
            return htmxError(HttpStatus.BAD_REQUEST, message, request);
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
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem(
                HttpStatus.CONFLICT,
                "urn:dmhelper:conflict",
                "Conflict",
                message,
                request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        String message = "The requested item could not be found. Reload and try again.";
        if ("true".equals(request.getHeader("HX-Request"))) {
            return htmxError(HttpStatus.NOT_FOUND, message, request);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(
                HttpStatus.NOT_FOUND,
                "urn:dmhelper:not-found",
                "Not Found",
                message,
                request));
    }

    @ExceptionHandler(Exception.class)
    public Object handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request failure", ex);
        String message = "The request could not be completed.";
        if ("true".equals(request.getHeader("HX-Request"))) {
            return htmxError(HttpStatus.INTERNAL_SERVER_ERROR, message, request);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "urn:dmhelper:request-failed",
                "Request Failed",
                message,
                request));
    }
}
