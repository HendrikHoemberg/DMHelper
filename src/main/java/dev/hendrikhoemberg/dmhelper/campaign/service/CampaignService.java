package dev.hendrikhoemberg.dmhelper.campaign.service;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CampaignService {

    private final CampaignRepository repository;
    private final PartyMemberRepository partyMemberRepository;
    private final StatBlockRepository statBlockRepository;
    private final PartyMemberService partyMemberService;
    private final StatBlockService statBlockService;
    private final ObjectMapper objectMapper;

    public CampaignService(CampaignRepository repository,
                           PartyMemberRepository partyMemberRepository,
                           StatBlockRepository statBlockRepository,
                           PartyMemberService partyMemberService,
                           StatBlockService statBlockService) {
        this.repository = repository;
        this.partyMemberRepository = partyMemberRepository;
        this.statBlockRepository = statBlockRepository;
        this.partyMemberService = partyMemberService;
        this.statBlockService = statBlockService;
        this.objectMapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public Campaign create(String name, String description) {
        Campaign campaign = new Campaign();
        campaign.setName(name);
        campaign.setDescription(description);
        return repository.save(campaign);
    }

    @Transactional(readOnly = true)
    public List<Campaign> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Campaign findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + id));
    }

    public Campaign update(UUID id, String name, String description) {
        Campaign campaign = findById(id);
        campaign.setName(name);
        campaign.setDescription(description);
        return repository.save(campaign);
    }

    public void delete(UUID id) {
        Campaign campaign = findById(id);
        repository.delete(campaign);
    }

    @Transactional(readOnly = true)
    public String exportToJson(UUID id) {
        Campaign campaign = findById(id);
        var party = partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(id).stream()
                .map(CampaignExportDto.PartyMemberExportDto::from).toList();
        var statBlocks = statBlockRepository.findByCampaignIdOrderByNameAsc(id).stream()
                .map(CampaignExportDto.StatBlockExportDto::from).toList();
        CampaignExportDto dto = CampaignExportDto.from(campaign, party, statBlocks);
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export campaign", e);
        }
    }

    public Campaign importFromJson(String json) {
        CampaignExportDto dto;
        try {
            dto = objectMapper.readValue(json, CampaignExportDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse import JSON: " + e.getMessage(), e);
        }

        if (dto.formatVersion() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported formatVersion: " + dto.formatVersion() + ". Expected: 1");
        }

        if (dto.campaign() == null || dto.campaign().name() == null || dto.campaign().name().isBlank()) {
            throw new IllegalArgumentException("Campaign name is required");
        }

        Campaign saved = create(dto.campaign().name(), dto.campaign().description());

        if (dto.party() != null) {
            for (var pmDto : dto.party()) {
                partyMemberService.create(saved.getId(),
                        pmDto.characterName(), pmDto.playerName(),
                        pmDto.classAndLevel(), pmDto.ac(), pmDto.maxHp(),
                        pmDto.initiativeBonus(), pmDto.speed(),
                        pmDto.passivePerception(), pmDto.passiveInsight(),
                        pmDto.passiveInvestigation(), pmDto.notes());
            }
        }
        if (dto.statBlocks() != null) {
            for (var sbDto : dto.statBlocks()) {
                StatBlock sb = statBlockService.createCustom(saved.getId(),
                        sbDto.name(), sbDto.cr(), sbDto.type(),
                        sbDto.ac(), sbDto.hp(), sbDto.speed(),
                        sbDto.strScore(), sbDto.dexScore(), sbDto.conScore(),
                        sbDto.intScore(), sbDto.wisScore(), sbDto.chaScore(),
                        null, null,
                        sbDto.strSave(), sbDto.dexSave(), sbDto.conSave(),
                        sbDto.intSave(), sbDto.wisSave(), sbDto.chaSave(),
                        sbDto.skills(),
                        sbDto.damageVulnerabilities(), sbDto.damageResistances(),
                        sbDto.damageImmunities(), sbDto.conditionImmunities(),
                        sbDto.senses(), sbDto.languages());
                if (sbDto.size() != null) sb.setSize(sbDto.size());
                if (sbDto.alignment() != null) sb.setAlignment(sbDto.alignment());
                if (sbDto.traits() != null) sb.setTraits(sbDto.traits());
                if (sbDto.actions() != null) sb.setActions(sbDto.actions());
                if (sbDto.bonusActions() != null) sb.setBonusActions(sbDto.bonusActions());
                if (sbDto.reactions() != null) sb.setReactions(sbDto.reactions());
                if (sbDto.legendaryActions() != null) sb.setLegendaryActions(sbDto.legendaryActions());
                if (sbDto.legendaryDescription() != null) sb.setLegendaryDescription(sbDto.legendaryDescription());
                if (sbDto.lairActions() != null) sb.setLairActions(sbDto.lairActions());
                sb.setXp(sbDto.xp());
            }
        }

        return saved;
    }
}
