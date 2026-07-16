package dev.hendrikhoemberg.dmhelper.session.web;

import dev.hendrikhoemberg.dmhelper.session.service.SessionWorkspaceService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Comparator;
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
        SessionWorkspaceService.SessionWorkspace workspace = workspaces.load(campaignId, mapId);
        model.addAttribute("workspace", workspace);
        model.addAttribute("campaignId", campaignId);
        model.addAttribute("attendeeIds", workspace.session().getStatus()
                == dev.hendrikhoemberg.dmhelper.session.data.CampaignSession.Status.IDLE
                ? workspace.partyMembers().stream().map(member -> member.getId().toString()).toList()
                : workspace.session().getAttendees().stream().map(member -> member.getId().toString()).toList());
        var attendanceMembers = new ArrayList<>(workspace.partyMembers());
        workspace.session().getAttendees().stream()
                .filter(attendee -> attendanceMembers.stream()
                        .noneMatch(member -> member.getId().equals(attendee.getId())))
                .forEach(attendanceMembers::add);
        attendanceMembers.sort(Comparator.comparing(
                dev.hendrikhoemberg.dmhelper.party.data.PartyMember::getCharacterName,
                String.CASE_INSENSITIVE_ORDER));
        model.addAttribute("attendanceMembers", attendanceMembers);
        return "session/cockpit";
    }
}
