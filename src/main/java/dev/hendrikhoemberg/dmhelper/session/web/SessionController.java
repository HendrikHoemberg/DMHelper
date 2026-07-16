package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
public class SessionController {

    private final SessionWorkspaceService workspaces;

    public SessionController(SessionWorkspaceService workspaces) {
        this.workspaces = workspaces;
    }

    @GetMapping("/campaigns/{campaignId}/session")
    public String cockpit(@PathVariable UUID campaignId,
                          @RequestParam(required = false) UUID mapId,
                          Model model) {
        model.addAttribute("workspace", workspaces.load(campaignId, mapId));
        model.addAttribute("campaignId", campaignId);
        return "session/cockpit";
    }
}
