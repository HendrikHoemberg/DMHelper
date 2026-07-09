package dev.hendrikhoemberg.dmhelper.dice.service;

import dev.hendrikhoemberg.dmhelper.dice.DiceEngine;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class DiceService {

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder().build();

    private final DiceEngine diceEngine;
    private final DiceRollRepository diceRollRepo;
    private final EncounterService encounterService;

    public DiceService(DiceEngine diceEngine, DiceRollRepository diceRollRepo,
                       EncounterService encounterService) {
        this.diceEngine = diceEngine;
        this.diceRollRepo = diceRollRepo;
        this.encounterService = encounterService;
    }

    public DiceResult roll(String expression, UUID encounterId) {
        DiceResult result = diceEngine.roll(expression);

        DiceRoll roll = new DiceRoll();
        roll.setExpression(result.expression());
        roll.setModifier(result.modifier());
        roll.setTotal(result.total());
        roll.setAdvantage(result.advantage());
        roll.setDisadvantage(result.disadvantage());
        roll.setEncounterId(encounterId != null ? encounterId.toString() : null);

        String rollsJson = serializeRolls(result);
        roll.setRolls(rollsJson);

        diceRollRepo.save(roll);

        if (encounterId != null) {
            encounterService.logDiceRoll(encounterId, result.expression(), result.total(), rollsJson);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<DiceRoll> getHistory() {
        return diceRollRepo.findTop20ByOrderByCreatedAtDesc();
    }

    private static String serializeRolls(DiceResult result) {
        try {
            return JSON_MAPPER.writeValueAsString(result.rolls());
        } catch (Exception e) {
            return "[]";
        }
    }
}
