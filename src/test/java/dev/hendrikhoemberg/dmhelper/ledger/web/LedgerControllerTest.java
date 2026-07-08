package dev.hendrikhoemberg.dmhelper.ledger.web;

import dev.hendrikhoemberg.dmhelper.calendar.service.CalendarService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.ledger.service.LedgerService;
import dev.hendrikhoemberg.dmhelper.ledger.service.LedgerService.HolderBalance;
import dev.hendrikhoemberg.dmhelper.ledger.service.LedgerService.LedgerEntryDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LedgerController.class)
class LedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    @MockitoBean
    private CalendarService calendarService;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private final UUID campaignId = UUID.randomUUID();

    @Test
    void shouldShowList() throws Exception {
        Campaign campaign = new Campaign();
        campaign.setId(campaignId);
        campaign.setName("Test Campaign");

        LedgerEntryDto entry = new LedgerEntryDto(
                UUID.randomUUID(), campaignId, Instant.now(),
                1492, 0, 1,
                "GOLD", "GAIN", BigDecimal.valueOf(100), "GP",
                "Party", null, null);

        HolderBalance balance = new HolderBalance("Party", BigDecimal.valueOf(100), "GP");

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(ledgerService.findByCampaignId(campaignId)).thenReturn(List.of(entry));
        when(ledgerService.computeAllGoldBalances(campaignId)).thenReturn(List.of(balance));

        mockMvc.perform(get("/campaigns/{campaignId}/ledger", campaignId))
                .andExpect(status().isOk())
                .andExpect(view().name("ledger/list"))
                .andExpect(model().attributeExists("entries"))
                .andExpect(model().attributeExists("balances"));
    }
}
