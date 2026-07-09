package dev.hendrikhoemberg.dmhelper.dice.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
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
    private final CampaignRepository campaignRepo;

    public DiceService(DiceEngine diceEngine, DiceRollRepository diceRollRepo,
                       EncounterService encounterService, CampaignRepository campaignRepo) {
        this.diceEngine = diceEngine;
        this.diceRollRepo = diceRollRepo;
        this.encounterService = encounterService;
        this.campaignRepo = campaignRepo;
    }

    public DiceResult roll(String expression, UUID encounterId, UUID campaignId) {
        DiceResult result = diceEngine.roll(expression);

        DiceRoll roll = new DiceRoll();
        roll.setExpression(result.expression());
        roll.setModifier(result.modifier());
        roll.setTotal(result.total());
        roll.setAdvantage(result.advantage());
        roll.setDisadvantage(result.disadvantage());
        roll.setCampaign(campaignRepo.getReferenceById(campaignId));

        boolean encounterActive = false;
        if (encounterId != null) {
            encounterActive = encounterService.findActiveByCampaignId(campaignId)
                    .map(e -> e.id().equals(encounterId))
                    .orElse(false);
        }
        roll.setEncounterId(encounterActive ? encounterId.toString() : null);

        String rollsJson = serializeRolls(result);
        roll.setRolls(rollsJson);

        diceRollRepo.save(roll);

        if (encounterActive) {
            encounterService.logDiceRoll(encounterId, result.expression(), result.total(), rollsJson);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public List<DiceRoll> getHistory() {
        return diceRollRepo.findTop20ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<DiceRoll> getHistory(UUID campaignId) {
        return diceRollRepo.findTop20ByCampaignIdOrderByCreatedAtDesc(campaignId);
    }

    private static String serializeRolls(DiceResult result) {
        try {
            return JSON_MAPPER.writeValueAsString(result.rolls());
        } catch (Exception e) {
            return "[]";
        }
    }
}
