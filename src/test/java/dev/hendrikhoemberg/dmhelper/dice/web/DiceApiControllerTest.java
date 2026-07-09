package dev.hendrikhoemberg.dmhelper.dice.web;

import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.service.DiceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DiceApiController.class)
class DiceApiControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private DiceService diceService;

    @Test
    void shouldRollValidExpression() throws Exception {
        DiceResult result = new DiceResult("2d6+4",
                List.of(new DiceResult.DieRoll("d6", List.of(3, 5))), 4, 12, false, false);
        when(diceService.roll(eq("2d6+4"), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"2d6+4\"}"))
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
        when(diceService.roll(eq("d20 adv"), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"d20 adv\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.advantage").value(true))
                .andExpect(jsonPath("$.total").value(14));
    }

    @Test
    void shouldRollDisadvantage() throws Exception {
        DiceResult result = new DiceResult("d20 dis",
                List.of(new DiceResult.DieRoll("d20", List.of(14, 3))), 0, 3, false, true);
        when(diceService.roll(eq("d20 dis"), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"d20 dis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disadvantage").value(true))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void shouldRollTypedInput() throws Exception {
        DiceResult result = new DiceResult("typed: 15", List.of(), 0, 15, false, false);
        when(diceService.roll(eq("15"), isNull())).thenReturn(result);

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expression").value("typed: 15"))
                .andExpect(jsonPath("$.total").value(15))
                .andExpect(jsonPath("$.rolls").isArray())
                .andExpect(jsonPath("$.rolls").isEmpty());
    }

    @Test
    void shouldReturnBadRequestForInvalidExpression() throws Exception {
        when(diceService.roll(eq("notdice"), isNull()))
                .thenThrow(new IllegalArgumentException("Invalid dice expression: notdice"));

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"notdice\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid dice expression: notdice"));
    }

    @Test
    void shouldReturnBadRequestForEmptyExpression() throws Exception {
        when(diceService.roll(eq(""), isNull()))
                .thenThrow(new IllegalArgumentException("Expression is required"));

        mockMvc.perform(post("/api/v1/roll")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expression\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldGetHistory() throws Exception {
        DiceRoll roll = new DiceRoll();
        roll.setExpression("2d6+4");
        roll.setTotal(12);
        when(diceService.getHistory()).thenReturn(List.of(roll));

        mockMvc.perform(get("/api/v1/roll/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].expression").value("2d6+4"))
                .andExpect(jsonPath("$[0].total").value(12));
    }
}
