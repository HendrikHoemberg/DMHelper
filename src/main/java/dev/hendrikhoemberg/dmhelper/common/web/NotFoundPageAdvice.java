package dev.hendrikhoemberg.dmhelper.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;

@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NotFoundPageAdvice {

    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleMissingPage(NoResourceFoundException ex, HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        boolean htmx = "true".equals(request.getHeader("HX-Request"));
        if (!htmx && accept != null && accept.contains("text/html")) {
            ModelAndView mav = new ModelAndView("error");
            mav.addObject("status", 404);
            mav.addObject(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
            mav.setStatus(HttpStatus.NOT_FOUND);
            return mav;
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
                "The requested item could not be found. Reload and try again.");
        problem.setTitle("Not Found");
        problem.setType(URI.create("urn:dmhelper:http-404"));
        problem.setProperty(CorrelationIdFilter.ATTRIBUTE, CorrelationIdFilter.current(request));
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }
}
