package dev.hendrikhoemberg.dmhelper.session.service;

/**
 * Raised when an encounter activation needs the campaign's session to be open (a persisted
 * session that is not {@code IDLE}) and it is not.
 */
public class SessionNotOpenException extends IllegalStateException {

    public SessionNotOpenException() {
        super("Start the session before running an encounter.");
    }
}
