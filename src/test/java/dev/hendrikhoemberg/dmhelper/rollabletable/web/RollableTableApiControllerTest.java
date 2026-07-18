package dev.hendrikhoemberg.dmhelper.rollabletable.web;

import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableRollService;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableService;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableValidationException;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableWrite;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableDuplicatePolicy;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollGroup;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollOutcome;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableRollRequest;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.TableValidationProblem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RollableTableApiController.class)
class RollableTableApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RollableTableService service;

    @MockitoBean
    private RollableTableRollService rollService;

    @MockitoBean
    private RollableTableRepository repository;

    @MockitoBean
    private CustomContentSupport customContentSupport;

    @MockitoBean
    private DiceEngine diceEngine;

    @MockitoBean
    private StatBlockRepository statBlockRepository;

    @MockitoBean
    private EquipmentItemRepository equipmentItemRepository;

    @MockitoBean
    private MagicItemRepository magicItemRepository;

    @MockitoBean
    private NoteRepository noteRepository;

    @MockitoBean
    private EncounterRepository encounterRepository;

    @MockitoBean
    private HandoutRepository handoutRepository;

    private final UUID campaignId = UUID.randomUUID();

    private RollableTable table(UUID id, String name) {
        RollableTable t = new RollableTable();
        t.setId(id);
        t.setName(name);
        t.setSource(ContentSource.CUSTOM);
        t.setAddressMode(TableAddressMode.WEIGHTED);
        t.setCategory(TableCategory.GENERIC);
        t.setRollExpression("1d100");
        return t;
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(), any(), any())).thenReturn(table(id, "New Table"));

        mockMvc.perform(post("/api/v1/rollable-tables")
                        .param("campaignId", campaignId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New Table\",\"sourceKey\":\"new-table\",\"addressMode\":\"WEIGHTED\",\"category\":\"GENERIC\",\"entries\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/library/tables/" + id));
    }

    @Test
    void createReturns400WithProblems() throws Exception {
        when(service.create(any(), any(), any()))
                .thenThrow(new RollableTableValidationException(
                        List.of(new TableValidationProblem("TABLE_WEIGHT_INVALID", "/entries/0/weight", "Weight must be > 0"))));

        mockMvc.perform(post("/api/v1/rollable-tables")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bad Table\",\"sourceKey\":\"bad\",\"addressMode\":\"WEIGHTED\",\"category\":\"GENERIC\",\"entries\":[{\"weight\":0,\"resultText\":\"test\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.problems").isArray())
                .andExpect(jsonPath("$.problems[0].code").value("TABLE_WEIGHT_INVALID"));
    }

    @Test
    void updateReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.updateCustom(any(), any(), any())).thenReturn(table(id, "Updated Table"));

        mockMvc.perform(put("/api/v1/rollable-tables/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Table\",\"sourceKey\":\"updated\",\"addressMode\":\"WEIGHTED\",\"category\":\"GENERIC\",\"entries\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Table"));
    }

    @Test
    void cloneReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.cloneAsCustom(any(), any(), any())).thenReturn(table(id, "Cloned Table"));

        mockMvc.perform(post("/api/v1/rollable-tables/{id}/clone", UUID.randomUUID()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Cloned Table"));
    }

    @Test
    void promoteReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.promoteToGlobal(id)).thenReturn(table(id, "Promoted Table"));

        mockMvc.perform(post("/api/v1/rollable-tables/{id}/promote", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Promoted Table"));
    }

    @Test
    void deleteReturns204WhenConfirmed() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/rollable-tables/{id}", id)
                        .param("confirmed", "true"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns409WhenNotConfirmedAndHasDependents() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Table has 1 dependent(s); set confirmed=true to proceed"))
                .when(service).deleteCustom(id, false);

        mockMvc.perform(delete("/api/v1/rollable-tables/{id}", id)
                        .param("confirmed", "false"))
                .andExpect(status().isConflict());
    }

    @Test
    void referenceOptionsReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/rollable-tables/reference-options")
                        .param("type", "STATBLOCK")
                        .param("q", "gob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void rollReturns200WithGroupedResult() throws Exception {
        UUID tableId = UUID.randomUUID();
        TableRollGroup group = new TableRollGroup(
                UUID.randomUUID(), campaignId, tableId,
                "test-table", "Test Table",
                List.of(new TableRollOutcome("test-table", "Test Table",
                        new DiceResult("1d100", List.of(), 0, 50, false, false),
                        "entry-0", "You found treasure!", null,
                        List.of(), List.of())),
                null, Instant.now());
        when(rollService.roll(any(), any(), any())).thenReturn(group);

        UUID campId = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/rollable-tables/{id}/roll", tableId)
                        .param("campaignId", campId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"manualValue\":null,\"rollCount\":1,\"duplicatePolicy\":\"ALLOW_DUPLICATES\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("Test Table"))
                .andExpect(jsonPath("$.outcomes[0].resultText").value("You found treasure!"));
    }
}
