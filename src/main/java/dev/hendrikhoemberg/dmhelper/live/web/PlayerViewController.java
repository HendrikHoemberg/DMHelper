package dev.hendrikhoemberg.dmhelper.live.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PlayerViewController {

    @GetMapping("/player")
    public String playerView(@RequestParam(defaultValue = "false") boolean embedded,
                             Model model) {
        model.addAttribute("embedded", embedded);
        return "player/view";
    }
}
