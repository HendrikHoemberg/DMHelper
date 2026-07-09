package dev.hendrikhoemberg.dmhelper.dice.service;

import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiceServiceTest {

    @Mock private DiceRollRepository diceRollRepo;
    @Mock private EncounterService encounterService;
    @Mock private DiceEngine diceEngine;

    private DiceService diceService;

    @BeforeEach
    void setUp() {
        diceService = new DiceService(diceEngine, diceRollRepo, encounterService);
    }

    @Test
    void shouldRollAndSaveHistory() {
        UUID encounterId = UUID.randomUUID();
        DiceResult expectedResult = new DiceResult("2d6+4",
                List.of(new DiceResult.DieRoll("d6", List.of(3, 5))), 4, 12, false, false);
        when(diceEngine.roll("2d6+4")).thenReturn(expectedResult);

        DiceResult result = diceService.roll("2d6+4", encounterId);

        assertThat(result).isEqualTo(expectedResult);

        ArgumentCaptor<DiceRoll> captor = ArgumentCaptor.forClass(DiceRoll.class);
        verify(diceRollRepo).save(captor.capture());
        DiceRoll saved = captor.getValue();
        assertThat(saved.getExpression()).isEqualTo("2d6+4");
        assertThat(saved.getTotal()).isEqualTo(12);
        assertThat(saved.getEncounterId()).isEqualTo(encounterId.toString());

        verify(encounterService).logDiceRoll(eq(encounterId), eq("2d6+4"), eq(12), anyString());
    }

    @Test
    void shouldRollWithoutEncounterAndNotLog() {
        DiceResult expectedResult = new DiceResult("d20",
                List.of(new DiceResult.DieRoll("d20", List.of(17))), 0, 17, false, false);
        when(diceEngine.roll("d20")).thenReturn(expectedResult);

        DiceResult result = diceService.roll("d20", null);

        assertThat(result).isEqualTo(expectedResult);
        verify(diceRollRepo).save(any());
        verify(encounterService, never()).logDiceRoll(any(), anyString(), anyInt(), anyString());
    }

    @Test
    void shouldHandleTypedInput() {
        DiceResult expectedResult = new DiceResult("typed: 15", List.of(), 0, 15, false, false);
        when(diceEngine.roll("15")).thenReturn(expectedResult);

        DiceResult result = diceService.roll("15", null);

        assertThat(result.expression()).isEqualTo("typed: 15");
        assertThat(result.total()).isEqualTo(15);
        assertThat(result.rolls()).isEmpty();

        ArgumentCaptor<DiceRoll> captor = ArgumentCaptor.forClass(DiceRoll.class);
        verify(diceRollRepo).save(captor.capture());
        assertThat(captor.getValue().getExpression()).isEqualTo("typed: 15");
    }

    @Test
    void shouldGetHistory() {
        DiceRoll roll1 = new DiceRoll();
        roll1.setExpression("d20");
        roll1.setTotal(17);
        DiceRoll roll2 = new DiceRoll();
        roll2.setExpression("2d6+4");
        roll2.setTotal(12);
        when(diceRollRepo.findTop20ByOrderByCreatedAtDesc()).thenReturn(List.of(roll2, roll1));

        List<DiceRoll> history = diceService.getHistory();

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getExpression()).isEqualTo("2d6+4");
        assertThat(history.get(1).getExpression()).isEqualTo("d20");
    }
}
