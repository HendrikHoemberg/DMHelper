package dev.hendrikhoemberg.dmhelper.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PinManager {

    private static final Logger log = LoggerFactory.getLogger(PinManager.class);
    private static final String PIN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int PIN_LENGTH = 6;
    private static final SecureRandom RNG = new SecureRandom();

    private final String pin;

    public PinManager() {
        this.pin = generatePin();
    }

    public String getPin() {
        return pin;
    }

    public boolean isValid(String candidate) {
        return pin.equals(candidate);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void announcePin() {
        log.info("");
        log.info("╔══════════════════════════════════════════════╗");
        log.info("║  DMHelper session PIN generated ({} chars) ║", pin.length());
        log.info("║  Player view:  http://<your-ip>:8081/player  ║");
        log.info("╚══════════════════════════════════════════════╝");
        log.info("");
    }

    private String generatePin() {
        StringBuilder sb = new StringBuilder(PIN_LENGTH);
        for (int i = 0; i < PIN_LENGTH; i++) {
            sb.append(PIN_CHARS.charAt(RNG.nextInt(PIN_CHARS.length())));
        }
        return sb.toString();
    }
}
