package dev.hendrikhoemberg.dmhelper.live.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PlayerViewController {

    @GetMapping("/player")
    public String playerView() {
        return "player/view";
    }
}
