package dev.hendrikhoemberg.dmhelper.support;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class LazyInitLogCapture implements AutoCloseable {

    private static final String TARGET = "LazyInitializationException";

    private final Logger root;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    public LazyInitLogCapture() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender.setContext(root.getLoggerContext());
        appender.start();
        root.addAppender(appender);
        root.setLevel(Level.WARN);
    }

    public List<String> lazyInitFailures() {
        return appender.list.stream()
                .filter(LazyInitLogCapture::mentionsLazyInit)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private static boolean mentionsLazyInit(ILoggingEvent event) {
        if (event.getFormattedMessage() != null && event.getFormattedMessage().contains(TARGET)) {
            return true;
        }
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            if (t.getClassName().contains(TARGET)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        root.detachAppender(appender);
        appender.stop();
    }
}
