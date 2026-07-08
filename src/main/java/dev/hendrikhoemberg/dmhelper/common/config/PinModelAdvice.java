package dev.hendrikhoemberg.dmhelper.common.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class PinModelAdvice {

    private final PinManager pinManager;

    public PinModelAdvice(ObjectProvider<PinManager> pinManagerProvider) {
        this.pinManager = pinManagerProvider.getIfAvailable();
    }

    @ModelAttribute("pinDisplay")
    public String getPinDisplay() {
        if (pinManager != null) {
            return "PIN: " + pinManager.getPin();
        }
        return "PIN: -----";
    }
}
