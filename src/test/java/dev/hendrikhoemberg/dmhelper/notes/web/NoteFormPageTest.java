package dev.hendrikhoemberg.dmhelper.notes.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.QuickNoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Both note forms are reached by ordinary links, so each has to answer with a whole
 * document — a bare fragment renders without any stylesheet.
 */
@WebMvcTest(NoteController.class)
class NoteFormPageTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoteService noteService;

    @MockitoBean
    private QuickNoteService quickNoteService;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @MockitoBean
    private MarkdownUtil markdownUtil;

    private final UUID campaignId = UUID.randomUUID();

    private Campaign sampleCampaign() {
        Campaign c = new Campaign();
        c.setId(campaignId);
        c.setName("Test Campaign");
        return c;
    }

    @Test
    void newNoteFormIsAWholeStyledPage() throws Exception {
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(sampleCampaign()));

        mockMvc.perform(get("/campaigns/{cid}/notes/new", campaignId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stylesheet")))
                .andExpect(content().string(containsString("New Note")));
    }

    @Test
    void editNoteFormIsAWholeStyledPage() throws Exception {
        UUID noteId = UUID.randomUUID();
        Note note = new Note();
        note.setId(noteId);
        note.setTitle("Der Bote");
        note.setType(NoteType.NPC);
        note.setCampaign(sampleCampaign());
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(sampleCampaign()));
        when(noteService.findById(noteId)).thenReturn(note);

        mockMvc.perform(get("/campaigns/{cid}/notes/{nid}/edit", campaignId, noteId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("stylesheet")))
                .andExpect(content().string(containsString("Der Bote")));
    }
}
