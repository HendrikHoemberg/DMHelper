package dev.hendrikhoemberg.dmhelper.treasury.web;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.ledger.service.LedgerService;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService;
import dev.hendrikhoemberg.dmhelper.treasury.service.TreasuryService.AssignmentDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TreasuryController.class)
class TreasuryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TreasuryService treasuryService;

    @MockitoBean
    private LedgerService ledgerService;

    @MockitoBean
    private CampaignRepository campaignRepository;

    @MockitoBean
    private PartyMemberRepository partyMemberRepository;

    @MockitoBean
    private MagicItemRepository magicItemRepository;

    @MockitoBean
    private EquipmentItemRepository equipmentItemRepository;

    private final UUID campaignId = UUID.randomUUID();

    private Campaign sampleCampaign() {
        Campaign c = new Campaign();
        c.setId(campaignId);
        c.setName("Test Campaign");
        return c;
    }

    @Test
    void shouldShowOverview() throws Exception {
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(sampleCampaign()));
        when(partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId)).thenReturn(List.of());
        when(treasuryService.findByCampaignId(campaignId)).thenReturn(List.of());
        when(ledgerService.computeAllGoldBalances(campaignId)).thenReturn(List.of());
        when(magicItemRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(equipmentItemRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{campaignId}/treasury", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("treasury/list"))
                .andExpect(model().attributeExists("assignments"))
                .andExpect(model().attributeExists("balances"));
    }

    @Test
    void shouldShowNewForm() throws Exception {
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(sampleCampaign()));
        when(partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId)).thenReturn(List.of());
        when(magicItemRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(equipmentItemRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(get("/campaigns/{campaignId}/treasury/new", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("treasury/_form :: form"));
    }

    @Test
    void shouldCreateAssignment() throws Exception {
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(sampleCampaign()));
        when(partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId)).thenReturn(List.of());
        when(treasuryService.findByCampaignId(campaignId)).thenReturn(List.of());
        when(ledgerService.computeAllGoldBalances(campaignId)).thenReturn(List.of());
        when(magicItemRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(equipmentItemRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        mockMvc.perform(post("/campaigns/{campaignId}/treasury", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("treasury/list"));
    }

    @Test
    void shouldToggleAttunement() throws Exception {
        UUID id = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(sampleCampaign()));
        when(partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId)).thenReturn(List.of());
        when(treasuryService.toggleAttunement(id)).thenReturn(
                new AssignmentDto(id, campaignId, null, "Party Stash",
                        null, null, null, null, null, 1, false, null));

        mockMvc.perform(put("/campaigns/{campaignId}/treasury/{id}/attune", campaignId, id))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDeleteAssignment() throws Exception {
        UUID id = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(sampleCampaign()));
        when(partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId)).thenReturn(List.of());

        mockMvc.perform(delete("/campaigns/{campaignId}/treasury/{id}", campaignId, id))
                .andExpect(status().isOk());
    }
}
