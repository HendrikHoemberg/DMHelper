package dev.hendrikhoemberg.dmhelper.common;

/** Missing domain entity → HTTP 404 via GlobalExceptionHandler. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
