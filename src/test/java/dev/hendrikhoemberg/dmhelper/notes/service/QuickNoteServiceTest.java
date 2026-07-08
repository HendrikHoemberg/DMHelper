package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({QuickNoteService.class, NoteService.class, WikiLinkParser.class, StatBlockService.class})
class QuickNoteServiceTest {

    @Autowired private QuickNoteRepository quickNoteRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private QuickNoteService quickNoteService;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaignRepository.save(campaign);
    }

    @Test
    void createsQuickNote() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "ENCOUNTER", UUID.randomUUID(), "Watch for traps.");
        assertNotNull(qn.getId());
        assertEquals("Watch for traps.", qn.getBody());
        assertEquals("ENCOUNTER", qn.getTargetType());
    }

    @Test
    void findsByTarget() {
        UUID targetId = UUID.randomUUID();
        quickNoteService.create(campaign.getId(), "MAP", targetId, "First note.");
        quickNoteService.create(campaign.getId(), "MAP", targetId, "Second note.");

        var notes = quickNoteService.findByTarget(campaign.getId(), "MAP", targetId);
        assertEquals(2, notes.size());
    }

    @Test
    void promotesQuickNoteToNote() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "STATBLOCK", UUID.randomUUID(), "This goblin has a secret lair.");
        Note note = quickNoteService.promoteToNote(qn.getId());

        assertNotNull(note.getId());
        assertEquals("This goblin has a secret lair.", note.getBody());
        assertEquals(NoteType.GENERIC, note.getType());
        assertTrue(quickNoteRepository.findById(qn.getId()).isEmpty());
    }

    @Test
    void promotesQuickNoteWithTitle() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "CAMPAIGN", campaign.getId(), "The party must find the crystal.");
        Note note = quickNoteService.promoteToNote(qn.getId(), "The Crystal Quest", NoteType.QUEST);

        assertEquals("The Crystal Quest", note.getTitle());
        assertEquals(NoteType.QUEST, note.getType());
    }

    @Test
    void deletesQuickNote() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "ENCOUNTER", UUID.randomUUID(), "Temp note.");
        quickNoteService.delete(qn.getId());
        assertTrue(quickNoteRepository.findById(qn.getId()).isEmpty());
    }

    @Test
    void promotesQuickNoteWithPrefixedLink() {
        var sb = new StatBlock();
        sb.setName("Goblin");
        sb.setSource(StatBlock.Source.SRD);
        sb.setSourceKey("goblin");
        sb.setCr("1/4");
        sb.setType("humanoid");
        sb.setHp("7 (2d6)");
        statBlockRepository.save(sb);

        QuickNote qn = quickNoteService.create(campaign.getId(), "STATBLOCK", sb.getId(), "This goblin has a secret lair.");
        Note note = quickNoteService.promoteToNote(qn.getId(), "Goblin Secrets", NoteType.LOCATION);

        assertTrue(note.getBody().startsWith("[[statblock:Goblin]]"));
        assertTrue(note.getBody().contains("This goblin has a secret lair."));
    }
}
