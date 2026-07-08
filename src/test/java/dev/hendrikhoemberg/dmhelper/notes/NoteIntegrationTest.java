package dev.hendrikhoemberg.dmhelper.notes;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class NoteIntegrationTest {

    @Autowired private CampaignRepository campaignRepository;
    @Autowired private NoteService noteService;
    @Autowired private QuickNoteService quickNoteService;
    @Autowired private NoteRepository noteRepository;
    @Autowired private NoteLinkRepository noteLinkRepository;
    @Autowired private QuickNoteRepository quickNoteRepository;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Integration Test Campaign");
        campaignRepository.save(campaign);
    }

    @Test
    void fullNoteLifecycle() {
        Note note = noteService.create(campaign.getId(), NoteType.LOCATION, "The Crypt",
                "A dark crypt with [[statblock:Skeleton]] guards. See also [[The Altar]].", "dungeon");

        assertNotNull(note.getId());

        String rendered = noteService.renderBody(note);
        assertTrue(rendered.contains("wiki-link"));

        var found = noteService.search(campaign.getId(), "crypt");
        assertEquals(1, found.size());
        assertEquals("The Crypt", found.get(0).getTitle());

        noteService.update(note.getId(), NoteType.LOCATION, "The Catacombs", "Updated body.", "catacombs");
        Note updated = noteService.findById(note.getId());
        assertEquals("The Catacombs", updated.getTitle());
    }

    @Test
    void quicknotePromoteFlow() {
        UUID targetId = UUID.randomUUID();
        QuickNote qn = quickNoteService.create(campaign.getId(), "ENCOUNTER", targetId, "Secret door in the north wall.");

        Note promoted = quickNoteService.promoteToNote(qn.getId(), "Secret Door Discovery", NoteType.LOCATION);
        assertNotNull(promoted.getId());
        assertEquals("Secret Door Discovery", promoted.getTitle());
        assertEquals("Secret door in the north wall.", promoted.getBody());
        assertTrue(quickNoteRepository.findById(qn.getId()).isEmpty());
    }

    @Test
    void wikiLinksCreateBacklinks() {
        Note target = noteService.create(campaign.getId(), NoteType.NPC, "Gundren", "A dwarf.", "");
        Note source = noteService.create(campaign.getId(), NoteType.QUEST, "Find Gundren",
                "The party must find [[Gundren]].", "quest");

        var backlinks = noteService.findBacklinks(target.getId());
        assertEquals(1, backlinks.size());
        assertEquals("Find Gundren", backlinks.get(0).getTitle());
    }

    @Test
    void searchAcrossNotesAndQuicknotes() {
        noteService.create(campaign.getId(), NoteType.NPC, "Thalia", "Elven ranger.", "");
        noteService.create(campaign.getId(), NoteType.LOCATION, "Forest of Whispers", "Ancient woods.", "");
        quickNoteService.create(campaign.getId(), "CAMPAIGN", campaign.getId(), "Thalia suspects something.");

        var noteResults = noteService.search(campaign.getId(), "Thalia");
        assertEquals(1, noteResults.size());
        assertEquals("Thalia", noteResults.get(0).getTitle());

        var qnResults = quickNoteRepository.searchByCampaignId(campaign.getId(), "Thalia");
        assertEquals(1, qnResults.size());
    }
}
