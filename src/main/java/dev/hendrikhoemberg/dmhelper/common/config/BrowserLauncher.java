package dev.hendrikhoemberg.dmhelper.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;

/**
 * Best-effort: open the DM's default browser at the app URL once the server is
 * ready (SPEC 2.1). Never throws into startup; gated by {@code dmhelper.open-browser}
 * and skipped on headless environments (CI, tests, servers).
 */
@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    private final boolean enabled;
    private final int port;

    public BrowserLauncher(@Value("${dmhelper.open-browser:true}") boolean enabled,
                           @Value("${server.port:8080}") int port) {
        this.enabled = enabled;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser(ApplicationReadyEvent event) {
        if (!enabled || GraphicsEnvironment.isHeadless()) {
            return;
        }
        String url = "http://localhost:" + port;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("linux") && tryExec("xdg-open", url)) {
                return;
            }
            if (os.contains("mac") && tryExec("open", url)) {
                return;
            }
            if (os.contains("win") && tryExec("rundll32", "url.dll,FileProtocolHandler", url)) {
                return;
            }
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            }
            log.info("Open DMHelper in your browser: {}", url);
        } catch (Exception e) {
            log.info("Could not auto-open a browser; open DMHelper at {} ({})", url, e.getMessage());
        }
    }

    private boolean tryExec(String... command) {
        try {
            new ProcessBuilder(command).start();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
