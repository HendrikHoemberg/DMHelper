package dev.hendrikhoemberg.dmhelper.dice.packagev2;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.DiceRollDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.dice.DiceResult;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class DiceSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final DiceRollRepository diceRollRepository;

    public DiceSectionAdapter(DiceRollRepository diceRollRepository) {
        this.diceRollRepository = diceRollRepository;
    }

    @Override
    public String sectionName() {
        return "Dice";
    }

    @Override
    public int order() {
        return 1200;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        boolean includeDiceHistory = context.options().includeDiceHistory();

        List<DiceRollDto> dtos;
        if (includeDiceHistory) {
            var rolls = diceRollRepository.findByCampaignIdOrderByCreatedAtAscIdAsc(context.campaignId());
            dtos = rolls.stream()
                    .map(roll -> exportDiceRoll(roll, context))
                    .toList();
        } else {
            dtos = List.of();
        }
        target.diceRolls(dtos);
    }

    private DiceRollDto exportDiceRoll(DiceRoll roll, CampaignExportContext context) {
        List<DiceResult.DieRoll> dieRolls = parseRolls(roll.getRolls());

        ContentReference encounterRef = null;
        if (roll.getEncounterId() != null && !roll.getEncounterId().isBlank()) {
            String encounterKey = context.keyService()
                    .getOrCreate(context.campaignId(), CampaignContentType.ENCOUNTER,
                            UUID.fromString(roll.getEncounterId()), "");
            encounterRef = ContentReference.packageRef(CampaignContentType.ENCOUNTER, encounterKey);
        }

        return new DiceRollDto(
                roll.getId().toString(),
                roll.getExpression(),
                dieRolls,
                roll.getModifier(),
                roll.getTotal(),
                roll.isAdvantage(),
                roll.isDisadvantage(),
                encounterRef,
                roll.getCreatedAt()
        );
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<DiceRollDto> dtos = source.diceRolls();
        if (dtos == null) return;

        var campaign = context.campaign();

        for (DiceRollDto dto : dtos) {
            var roll = new DiceRoll();
            roll.setCampaign(campaign);
            roll.setExpression(dto.expression());
            roll.setRolls(serializeRolls(dto.rolls()));
            roll.setModifier(dto.modifier());
            roll.setTotal(dto.total());
            roll.setAdvantage(dto.advantage());
            roll.setDisadvantage(dto.disadvantage());
            roll.setCreatedAt(dto.createdAt());

            if (dto.encounterRef() != null) {
                ContentReference ref = dto.encounterRef();
                if (ref.type() == CampaignContentType.ENCOUNTER) {
                    context.defer("dice roll encounter " + dto.key(), () -> {
                        var encounter = context.require(ref, CampaignContentType.ENCOUNTER,
                                dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.class);
                        roll.setEncounterId(encounter.getId().toString());
                    });
                }
            }

            diceRollRepository.save(roll);
        }
    }

    private List<DiceResult.DieRoll> parseRolls(String rollsJson) {
        if (rollsJson == null || rollsJson.isBlank()) return List.of();
        try {
            return MAPPER.readValue(rollsJson, new TypeReference<List<DiceResult.DieRoll>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private String serializeRolls(List<DiceResult.DieRoll> rolls) {
        if (rolls == null || rolls.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(rolls);
        } catch (Exception e) {
            return null;
        }
    }
}
