package dev.hendrikhoemberg.dmhelper.rollabletable.web;

import dev.hendrikhoemberg.dmhelper.config.MarkdownUtil;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableEntry;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RollableTableController.class)
class RollableTableControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RollableTableRepository repository;

    @MockitoBean
    private RollableTableService service;

    @MockitoBean
    private MarkdownUtil markdownUtil;

    private final UUID campaignId = UUID.randomUUID();

    private RollableTable table(UUID id, String name, ContentSource source) {
        RollableTable t = new RollableTable();
        t.setId(id);
        t.setName(name);
        t.setSource(source);
        t.setAddressMode(TableAddressMode.WEIGHTED);
        t.setCategory(TableCategory.GENERIC);
        t.setRollExpression("1d100");
        if (source == ContentSource.CUSTOM) {
            t.setDescription("A custom table");
        }
        return t;
    }

    private RollableTableEntry entry(String key, String resultText, int sortOrder) {
        RollableTableEntry e = new RollableTableEntry();
        e.setEntryKey(key);
        e.setResultText(resultText);
        e.setSortOrder(sortOrder);
        e.setWeight(1);
        return e;
    }

    @Test
    void listReturns200WithTables() throws Exception {
        when(repository.findVisibleByCampaignId(any())).thenReturn(List.of(
                table(UUID.randomUUID(), "Potion Effects", ContentSource.CUSTOM),
                table(UUID.randomUUID(), "Tavern Names", ContentSource.CUSTOM)
        ));

        mockMvc.perform(get("/library/tables")
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Potion Effects")))
                .andExpect(content().string(containsString("Tavern Names")));
    }

    @Test
    void listFiltersByCategory() throws Exception {
        when(repository.findVisibleByCampaignId(any())).thenReturn(List.of(
                table(UUID.randomUUID(), "Encounter Table", ContentSource.CUSTOM)
        ));

        mockMvc.perform(get("/library/tables")
                        .param("campaignId", campaignId.toString())
                        .param("category", "ENCOUNTER"))
                .andExpect(status().isOk());
    }

    @Test
    void newFormReturns200() throws Exception {
        mockMvc.perform(get("/library/tables/new")
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk());
    }

    @Test
    void listContainsCreateControlWithCampaignContext() throws Exception {
        when(repository.findVisibleByCampaignId(campaignId)).thenReturn(List.of());

        mockMvc.perform(get("/library/tables")
                        .param("campaignId", campaignId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("New Table")))
                .andExpect(content().string(containsString("/library/tables/new?campaignId=" + campaignId)));
    }

    @Test
    void detailReturns200WithEntries() throws Exception {
        UUID id = UUID.randomUUID();
        RollableTable t = table(id, "Wild Magic Surge", ContentSource.CUSTOM);
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");
        t.setCampaign(campaign);
        t.getEntries().add(entry("e1", "Fireball centered on self", 0));
        t.getEntries().add(entry("e2", "Turn into a potted plant", 1));
        when(repository.findWithEntriesById(id)).thenReturn(Optional.of(t));
        when(markdownUtil.toHtml(any())).thenReturn("<p>Rendered description</p>");

        mockMvc.perform(get("/library/tables/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wild Magic Surge")))
                .andExpect(content().string(containsString("Fireball centered on self")))
                .andExpect(content().string(containsString("Turn into a potted plant")))
                .andExpect(content().string(containsString("Roll")))
                .andExpect(content().string(containsString("Clone")))
                .andExpect(content().string(containsString("Promote")))
                .andExpect(content().string(containsString("Delete")));
    }

    @Test
    void editReturns200ForCustomTable() throws Exception {
        UUID id = UUID.randomUUID();
        RollableTable t = table(id, "My Homebrew Table", ContentSource.CUSTOM);
        t.getEntries().add(entry("e1", "Result A", 0));
        when(repository.findWithEntriesById(id)).thenReturn(Optional.of(t));

        mockMvc.perform(get("/library/tables/{id}/edit", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My Homebrew Table")));
    }

    @Test
    void editReturns403ForSrdTable() throws Exception {
        UUID id = UUID.randomUUID();
        RollableTable t = table(id, "SRD Table", ContentSource.SRD);
        when(repository.findWithEntriesById(id)).thenReturn(Optional.of(t));

        mockMvc.perform(get("/library/tables/{id}/edit", id))
                .andExpect(status().isForbidden());
    }
}
