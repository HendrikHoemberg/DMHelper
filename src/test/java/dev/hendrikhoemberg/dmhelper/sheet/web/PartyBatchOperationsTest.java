package dev.hendrikhoemberg.dmhelper.sheet.web;

import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService;
import dev.hendrikhoemberg.dmhelper.sheet.service.SheetService.SheetDto;
import dev.hendrikhoemberg.dmhelper.treasury.data.InventoryState;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SheetApiController.class)
class PartyBatchOperationsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SheetService sheetService;

    @MockitoBean
    private PartyMemberRepository partyMemberRepo;

    @MockitoBean
    private TreasuryService treasuryService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID campaignId = UUID.randomUUID();

    @Test
    void batchShortRestAppliesToMultipleMembers() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        SheetDto sheet1 = new SheetDto(UUID.randomUUID(), id1, Map.of(), List.of(), null, null, null, null, List.of(), 0, Map.of(), Map.of(), Map.of(), 0, Map.of(), null, List.of(), List.of(), List.of(), List.of());
        SheetDto sheet2 = new SheetDto(UUID.randomUUID(), id2, Map.of(), List.of(), null, null, null, null, List.of(), 0, Map.of(), Map.of(), Map.of(), 0, Map.of(), null, List.of(), List.of(), List.of(), List.of());

        when(sheetService.getSheetDtoByPartyMemberId(id1)).thenReturn(sheet1);
        when(sheetService.getSheetDtoByPartyMemberId(id2)).thenReturn(sheet2);
        when(sheetService.shortRest(sheet1.id(), 2)).thenReturn(sheet1);
        when(sheetService.shortRest(sheet2.id(), 2)).thenReturn(sheet2);

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/party/rest", campaignId)
                        .param("type", "SHORT")
                        .param("members", id1.toString(), id2.toString())
                        .param("hitDiceSpent", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].status").value("ok"))
                .andExpect(jsonPath("$.results[1].status").value("ok"));
    }

    @Test
    void batchXpEqualSplitDistributesEvenly() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        SheetDto sheet1 = new SheetDto(UUID.randomUUID(), id1, Map.of(), List.of(), null, null, null, null, List.of(), 100, Map.of(), Map.of(), Map.of(), 0, Map.of(), null, List.of(), List.of(), List.of(), List.of());
        SheetDto sheet2 = new SheetDto(UUID.randomUUID(), id2, Map.of(), List.of(), null, null, null, null, List.of(), 200, Map.of(), Map.of(), Map.of(), 0, Map.of(), null, List.of(), List.of(), List.of(), List.of());
        SheetDto result1 = new SheetDto(sheet1.id(), id1, Map.of(), List.of(), null, null, null, null, List.of(), 600, Map.of(), Map.of(), Map.of(), 0, Map.of(), null, List.of(), List.of(), List.of(), List.of());
        SheetDto result2 = new SheetDto(sheet2.id(), id2, Map.of(), List.of(), null, null, null, null, List.of(), 700, Map.of(), Map.of(), Map.of(), 0, Map.of(), null, List.of(), List.of(), List.of(), List.of());

        when(sheetService.getSheetDtoByPartyMemberId(id1)).thenReturn(sheet1);
        when(sheetService.getSheetDtoByPartyMemberId(id2)).thenReturn(sheet2);
        when(sheetService.awardXp(sheet1.id(), 500)).thenReturn(result1);
        when(sheetService.awardXp(sheet2.id(), 500)).thenReturn(result2);

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/party/xp", campaignId)
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of(
                                "memberIds", List.of(id1.toString(), id2.toString()),
                                "amount", 1000,
                                "split", "EQUAL"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members.length()").value(2))
                .andExpect(jsonPath("$.members[0].xp").value(600))
                .andExpect(jsonPath("$.members[1].xp").value(700));
    }

    @Test
    void batchAddConditionAppliesToAllMembers() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        PartyMember pm1 = new PartyMember();
        pm1.setId(id1);
        pm1.setConditionsJson("[]");
        PartyMember pm2 = new PartyMember();
        pm2.setId(id2);
        pm2.setConditionsJson(null);

        when(partyMemberRepo.findById(id1)).thenReturn(Optional.of(pm1));
        when(partyMemberRepo.findById(id2)).thenReturn(Optional.of(pm2));
        when(partyMemberRepo.save(pm1)).thenReturn(pm1);
        when(partyMemberRepo.save(pm2)).thenReturn(pm2);

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/party/condition/batch", campaignId)
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of(
                                "memberIds", List.of(id1.toString(), id2.toString()),
                                "condition", Map.of("sourceKey", "poisoned", "name", "Poisoned", "notes", "taken damage")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].status").value("ok"))
                .andExpect(jsonPath("$.results[1].status").value("ok"));
    }

    @Test
    void batchClearDeathSavesResetsBothSuccessesAndFailures() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        PartyMember pm1 = new PartyMember();
        pm1.setId(id1);
        pm1.setDeathSaveSuccesses(2);
        pm1.setDeathSaveFailures(1);
        PartyMember pm2 = new PartyMember();
        pm2.setId(id2);
        pm2.setDeathSaveSuccesses(3);
        pm2.setDeathSaveFailures(2);

        when(partyMemberRepo.findById(id1)).thenReturn(Optional.of(pm1));
        when(partyMemberRepo.findById(id2)).thenReturn(Optional.of(pm2));
        when(partyMemberRepo.save(pm1)).thenReturn(pm1);
        when(partyMemberRepo.save(pm2)).thenReturn(pm2);

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/party/death-saves/clear-batch", campaignId)
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of(
                                "memberIds", List.of(id1.toString(), id2.toString())
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].status").value("ok"))
                .andExpect(jsonPath("$.results[1].status").value("ok"));
    }

    @Test
    void batchLootAssignsCustomItemToEachMember() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        var dto = new TreasuryService.AssignmentDto(
                assignmentId, campaignId, id1, "A", null, null, null, null,
                "Potion of Healing", 1, false, "Potion of Healing", InventoryState.CARRIED);
        when(treasuryService.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/party/loot/batch", campaignId)
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of(
                                "memberIds", List.of(id1.toString(), id2.toString()),
                                "customText", "Potion of Healing",
                                "quantity", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].status").value("ok"))
                .andExpect(jsonPath("$.results[1].status").value("ok"));
    }
}
