package dev.hendrikhoemberg.dmhelper.party.web;

import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService.PartyLiveStateDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/campaigns/{campaignId}/party")
public class PartyApiController {

    private final PartyMemberService partyService;

    public PartyApiController(PartyMemberService partyService) {
        this.partyService = partyService;
    }

    @PutMapping("/{memberId}/live-state")
    public ResponseEntity<PartyMember> updateLiveState(
            @PathVariable UUID campaignId,
            @PathVariable UUID memberId,
            @RequestBody PartyLiveStateDto body) {
        PartyMember updated = partyService.updateLiveState(memberId, body);
        return ResponseEntity.ok(updated);
    }
}
