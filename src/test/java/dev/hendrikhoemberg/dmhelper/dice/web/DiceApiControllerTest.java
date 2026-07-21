package dev.hendrikhoemberg.dmhelper.dice.web;

import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.service.DiceService;
import dev.hendrikhoemberg.dmhelper.dice.service.RollHistoryItem;
import dev.hendrikhoemberg.dmhelper.dice.service.RollHistoryKind;
import dev.hendrikhoemberg.dmhelper.dice.service.RollHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;

@WebMvcTest(DiceApiController.class)
class DiceApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DiceService diceService;
    @MockitoBean private RollHistoryService rollHistoryService;

    @MockitoBean
    private CampaignRepository campaignRepository;

    private static final UUID CAMPAIGN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void shouldRollValidExpression() throws Exception {
        DiceResult result = new DiceResult("2d6+4",
                List.of(new DiceResult.DieRoll("d6", List.of(3, 5))), 4, 12, false, false);
        when(diceService.roll(eq("2d6+4"), isNull(), eq(CAMPAIGN_ID))).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"2d6+4\",\"campaignId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expression").value("2d6+4"))
                .andExpect(jsonPath("$.total").value(12))
                .andExpect(jsonPath("$.modifier").value(4))
                .andExpect(jsonPath("$.rolls[0].die").value("d6"))
                .andExpect(jsonPath("$.rolls[0].values[0]").value(3))
                .andExpect(jsonPath("$.rolls[0].values[1]").value(5));
    }

    @Test
    void shouldRollAdvantage() throws Exception {
        DiceResult result = new DiceResult("d20 adv",
                List.of(new DiceResult.DieRoll("d20", List.of(7, 14))), 0, 14, true, false);
        when(diceService.roll(eq("d20 adv"), isNull(), eq(CAMPAIGN_ID))).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"d20 adv\",\"campaignId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advantage").value(true))
                .andExpect(jsonPath("$.total").value(14));
    }

    @Test
    void shouldRollDisadvantage() throws Exception {
        DiceResult result = new DiceResult("d20 dis",
                List.of(new DiceResult.DieRoll("d20", List.of(14, 3))), 0, 3, false, true);
        when(diceService.roll(eq("d20 dis"), isNull(), eq(CAMPAIGN_ID))).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"d20 dis\",\"campaignId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disadvantage").value(true))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void shouldRollTypedInput() throws Exception {
        DiceResult result = new DiceResult("typed: 15", List.of(), 0, 15, false, false);
        when(diceService.roll(eq("15"), isNull(), eq(CAMPAIGN_ID))).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"15\",\"campaignId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expression").value("typed: 15"))
                .andExpect(jsonPath("$.total").value(15))
                .andExpect(jsonPath("$.rolls").isArray())
                .andExpect(jsonPath("$.rolls").isEmpty());
    }

    @Test
    void shouldReturnBadRequestForInvalidExpression() throws Exception {
        when(diceService.roll(eq("notdice"), isNull(), eq(CAMPAIGN_ID)))
                .thenThrow(new IllegalArgumentException("Invalid dice expression: notdice"));

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"notdice\",\"campaignId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid dice expression: notdice"));
    }

    @Test
    void shouldReturnBadRequestForEmptyExpression() throws Exception {
        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"\",\"campaignId\":\"00000000-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetMergedHistory() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<RollHistoryItem> history = List.of(
                new RollHistoryItem(id1, RollHistoryKind.TABLE, Instant.parse("2026-07-18T12:02:00Z"),
                        null, 0, "Forest Encounters",
                        List.of(new RollHistoryItem.RollHistoryOutcome("wolves", "2 wolves")), true),
                new RollHistoryItem(id2, RollHistoryKind.DICE, Instant.parse("2026-07-18T12:01:00Z"),
                        "1d8", 5, null, null, true)
        );
        when(rollHistoryService.recent(CAMPAIGN_ID, 20)).thenReturn(history);

        mockMvc.perform(get("/api/v1/roll/history")
                        .param("campaignId", "00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("TABLE"))
                .andExpect(jsonPath("$[0].tableName").value("Forest Encounters"))
                .andExpect(jsonPath("$[0].outcomes[0].entryKey").value("wolves"))
                .andExpect(jsonPath("$[0].outcomes[0].resultText").value("2 wolves"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].kind").value("DICE"))
                .andExpect(jsonPath("$[1].expression").value("1d8"))
                .andExpect(jsonPath("$[1].total").value(5));
    }

    @Test
    void shouldReturnBadRequestWhenCampaignIdMissing() throws Exception {
        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"d20\"}"))
                .andExpect(status().isBadRequest());
    }
}
