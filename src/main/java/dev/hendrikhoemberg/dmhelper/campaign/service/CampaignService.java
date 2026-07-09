package dev.hendrikhoemberg.dmhelper.campaign.service;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResource;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetResourceRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReference;
import dev.hendrikhoemberg.dmhelper.sheet.data.SheetSpellReferenceRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CampaignService {

    private final CampaignRepository repository;
    private final PartyMemberRepository partyMemberRepository;
    private final StatBlockRepository statBlockRepository;
    private final PartyMemberService partyMemberService;
    private final StatBlockService statBlockService;
    private final GameMapService gameMapService;
    private final NoteRepository noteRepository;
    private final QuickNoteRepository quickNoteRepository;
    private final NoteService noteService;
    private final ObjectMapper objectMapper;
    private final CharacterSheetRepository sheetRepo;
    private final SheetResourceRepository resourceRepo;
    private final SheetSpellReferenceRepository spellRefRepo;
    private final SpeciesRepository speciesRepo;
    private final BackgroundRepository backgroundRepo;
    private final SpellRepository spellRepo;
    private final ItemAssignmentRepository assignmentRepo;
    private final LedgerEntryRepository ledgerEntryRepo;
    private final TimelineEventRepository timelineEventRepo;
    private final MagicItemRepository magicItemRepo;
    private final EquipmentItemRepository equipmentItemRepo;
    private final EncounterRepository encounterRepo;
    private final CombatantRepository combatantRepo;
    private final HandoutService handoutService;
    private final HandoutRepository handoutRepo;
    private final TokenRepository tokenRepo;

    public CampaignService(CampaignRepository repository,
                           PartyMemberRepository partyMemberRepository,
                           StatBlockRepository statBlockRepository,
                           PartyMemberService partyMemberService,
                           StatBlockService statBlockService,
                           GameMapService gameMapService,
                           NoteRepository noteRepository,
                           QuickNoteRepository quickNoteRepository,
                           NoteService noteService,
                           CharacterSheetRepository sheetRepo,
                           SheetResourceRepository resourceRepo,
                           SheetSpellReferenceRepository spellRefRepo,
                           SpeciesRepository speciesRepo,
                           BackgroundRepository backgroundRepo,
                           SpellRepository spellRepo,
                           ItemAssignmentRepository assignmentRepo,
                           LedgerEntryRepository ledgerEntryRepo,
                           TimelineEventRepository timelineEventRepo,
                            MagicItemRepository magicItemRepo,
                            EquipmentItemRepository equipmentItemRepo,
                             EncounterRepository encounterRepo,
                             CombatantRepository combatantRepo,
                             HandoutService handoutService,
                             HandoutRepository handoutRepo,
                             TokenRepository tokenRepo) {
        this.repository = repository;
        this.partyMemberRepository = partyMemberRepository;
        this.statBlockRepository = statBlockRepository;
        this.partyMemberService = partyMemberService;
        this.statBlockService = statBlockService;
        this.gameMapService = gameMapService;
        this.noteRepository = noteRepository;
        this.quickNoteRepository = quickNoteRepository;
        this.noteService = noteService;
        this.sheetRepo = sheetRepo;
        this.resourceRepo = resourceRepo;
        this.spellRefRepo = spellRefRepo;
        this.speciesRepo = speciesRepo;
        this.backgroundRepo = backgroundRepo;
        this.spellRepo = spellRepo;
        this.assignmentRepo = assignmentRepo;
        this.ledgerEntryRepo = ledgerEntryRepo;
        this.timelineEventRepo = timelineEventRepo;
        this.magicItemRepo = magicItemRepo;
        this.equipmentItemRepo = equipmentItemRepo;
        this.encounterRepo = encounterRepo;
        this.combatantRepo = combatantRepo;
        this.handoutService = handoutService;
        this.handoutRepo = handoutRepo;
        this.tokenRepo = tokenRepo;
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
                .map(this::toPartyMemberExport).toList();
        var statBlockEntities = statBlockRepository.findByCampaignIdOrderByNameAsc(id);
        var statBlocks = statBlockEntities.stream()
                .map(CampaignExportDto.StatBlockExportDto::from).toList();
        var gameMaps = gameMapService.findByCampaignId(id);
        java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, String>> tokenIdMapsByMap = new java.util.HashMap<>();
        List<CampaignExportDto.MapExportDto> maps = new java.util.ArrayList<>();
        for (var gameMap : gameMaps) {
            var document = gameMapService.getDocument(gameMap.getId());
            var tokens = tokenRepo.findByMapIdOrderByNameAsc(gameMap.getId());
            List<CampaignExportDto.MapExportDto.TokenExportDto> tokenDtos = new java.util.ArrayList<>();
            java.util.Map<java.util.UUID, String> tokenIdMap = new java.util.HashMap<>();
            for (var t : tokens) {
                tokenDtos.add(new CampaignExportDto.MapExportDto.TokenExportDto(
                        t.getId().toString(), t.getName(), t.getKind(), t.getColor(),
                        t.getPositionX(), t.getPositionY(), t.getSizeCols(), t.getSizeRows(),
                        t.isHidden(),
                        t.getStatBlock() != null ? t.getStatBlock().getSourceKey() : null,
                        t.getPartyMember() != null ? t.getPartyMember().getCharacterName() : null,
                        t.getCurrentHp(), t.getMaxHp(), t.isDead(), t.getNotes()));
                tokenIdMap.put(t.getId(), t.getId().toString());
            }
            tokenIdMapsByMap.put(gameMap.getId(), tokenIdMap);
            maps.add(CampaignExportDto.MapExportDto.from(gameMap, document, tokenDtos));
        }

        List<CampaignExportDto.EncounterExportDto> encounters = new java.util.ArrayList<>();
        for (var enc : encounterRepo.findByCampaignIdOrderByNameAsc(id)) {
            var encMap = enc.getMap();
            java.util.Map<java.util.UUID, String> tokenIdMap = encMap != null
                    ? tokenIdMapsByMap.getOrDefault(encMap.getId(), java.util.Map.of())
                    : java.util.Map.of();
            List<CampaignExportDto.CombatantExportDto> combatants = combatantRepo
                    .findByEncounterIdOrderBySortOrderAsc(enc.getId()).stream()
                    .map(c -> CampaignExportDto.CombatantExportDto.from(c, tokenIdMap))
                    .toList();
            encounters.add(CampaignExportDto.EncounterExportDto.from(enc, combatants));
        }

        var handoutEntities = handoutRepo.findByCampaignIdOrderByTitleAsc(id);
        var handouts = handoutEntities.stream()
                .map(h -> {
                    String imageBase64 = null;
                    try {
                        java.nio.file.Path filePath = java.nio.file.Path.of(
                                System.getProperty("user.home"), ".dmhelper", "files")
                                .resolve(h.getFileName());
                        byte[] bytes = java.nio.file.Files.readAllBytes(filePath);
                        imageBase64 = "data:" + h.getContentType() + ";base64," +
                                java.util.Base64.getEncoder().encodeToString(bytes);
                    } catch (Exception e) {
                        System.err.println("WARNING: Could not read handout image: " + h.getFileName());
                    }
                    return CampaignExportDto.HandoutExportDto.from(h, imageBase64);
                })
                .toList();

        var noteEntities = noteRepository.findByCampaignIdOrderByCreatedAtDesc(id);
        var noteDtos = noteEntities.stream()
                .map(CampaignExportDto.NoteExportDto::from).toList();
        java.util.Map<java.util.UUID, String> quickNoteIdMappings = new java.util.HashMap<>();
        for (var gm : gameMaps) {
            quickNoteIdMappings.put(gm.getId(), gm.getId().toString());
        }
        for (var sb : statBlockEntities) {
            if (sb.getSourceKey() != null) quickNoteIdMappings.put(sb.getId(), sb.getSourceKey());
        }
        for (var note : noteEntities) {
            quickNoteIdMappings.put(note.getId(), note.getTitle());
        }
        for (var handout : handoutEntities) {
            quickNoteIdMappings.put(handout.getId(), handout.getTitle());
        }
        var quickNoteDtos = quickNoteRepository.findByCampaignIdOrderByCreatedAtDesc(id).stream()
                .map(qn -> CampaignExportDto.QuickNoteExportDto.from(qn, quickNoteIdMappings))
                .toList();

        var assignments = assignmentRepo.findByCampaignIdOrderByPartyMemberAsc(id).stream()
                .map(a -> new CampaignExportDto.AssignmentExportDto(
                        a.getId(),
                        a.getPartyMember() != null ? a.getPartyMember().getCharacterName() : null,
                        a.getMagicItem() != null ? a.getMagicItem().getSourceKey() : null,
                        a.getEquipmentItem() != null ? a.getEquipmentItem().getSourceKey() : null,
                        a.getCustomText(), a.getQuantity(), a.isAttuned()))
                .toList();

        var ledgerEntries = ledgerEntryRepo.findByCampaignIdOrderByTimestampDesc(id).stream()
                .map(le -> new CampaignExportDto.LedgerExportDto(
                        le.getId(), le.getTimestamp(),
                        le.getInGameYear(), le.getInGameMonth(), le.getInGameDay(),
                        le.getKind().name(), le.getDirection().name(),
                        le.getAmount(), le.getCurrency(),
                        le.getHolder(), le.getNote()))
                .toList();

        var timelineEvents = timelineEventRepo.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(id).stream()
                .map(te -> new CampaignExportDto.TimelineExportDto(
                        te.getId(), te.getInGameYear(), te.getInGameMonth(), te.getInGameDay(),
                        te.getTitle(), te.getBody(),
                        te.getNoteRef() != null ? te.getNoteRef().getTitle() : null))
                .toList();

        CampaignExportDto dto = new CampaignExportDto(
                CampaignExportDto.CURRENT_FORMAT_VERSION,
                new CampaignExportDto.CampaignDto(campaign.getName(), campaign.getDescription()),
                party,
                statBlocks,
                handouts,
                maps,
                encounters,
                noteDtos,
                quickNoteDtos,
                assignments,
                ledgerEntries,
                timelineEvents
        );
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export campaign", e);
        }
    }

    @Transactional(readOnly = true)
    public List<String> validateImport(String json) {
        List<String> warnings = new ArrayList<>();
        CampaignExportDto dto;
        try {
            dto = objectMapper.readValue(json, CampaignExportDto.class);
        } catch (Exception e) {
            return List.of("Failed to parse JSON: " + e.getMessage());
        }

        if (dto.formatVersion() != CampaignExportDto.CURRENT_FORMAT_VERSION) {
            warnings.add("Unsupported formatVersion: " + dto.formatVersion() +
                    ". Expected: " + CampaignExportDto.CURRENT_FORMAT_VERSION);
        }
        if (dto.campaign() == null || dto.campaign().name() == null || dto.campaign().name().isBlank()) {
            warnings.add("Campaign name is required");
        }

        if (dto.maps() != null) {
            for (var mapDto : dto.maps()) {
                var grid = mapDto.grid();
                if (grid == null) {
                    warnings.add("Map '" + mapDto.name() + "' has no grid config");
                } else {
                    if (grid.w() < 1) warnings.add("Map '" + mapDto.name() + "' grid width must be >= 1");
                    if (grid.h() < 1) warnings.add("Map '" + mapDto.name() + "' grid height must be >= 1");
                }
            }
        }

        if (dto.statBlocks() != null) {
            for (var sbDto : dto.statBlocks()) {
                if (sbDto.name() == null || sbDto.name().isBlank()) {
                    warnings.add("StatBlock has no name");
                }
            }
        }

        if (dto.encounters() != null) {
            for (var encDto : dto.encounters()) {
                if (encDto.name() == null || encDto.name().isBlank()) {
                    warnings.add("Encounter has no name");
                }
                if (encDto.combatants() != null) {
                    for (int i = 0; i < encDto.combatants().size(); i++) {
                        var c = encDto.combatants().get(i);
                        if (c.name() == null || c.name().isBlank()) {
                            warnings.add("Combatant #" + (i + 1) + " in encounter '" +
                                    encDto.name() + "' has no name");
                        }
                        if (c.statBlockKey() != null && c.statBlockKey().startsWith("srd-")) {
                            var resolved = statBlockRepository.findBySourceKey(c.statBlockKey());
                            if (resolved.isEmpty()) {
                                warnings.add("SRD statblock key '" + c.statBlockKey() +
                                        "' not found in library (will use plain text)");
                            }
                        }
                    }
                }
            }
        }

        if (warnings.isEmpty()) {
            warnings.add("Validation passed — campaign is ready for import.");
        }

        return warnings;
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

        java.util.Map<String, UUID> mapKeyToId = new java.util.HashMap<>();
        java.util.Map<String, UUID> tokenOldToNewId = new java.util.HashMap<>();
        java.util.Map<String, UUID> statblockKeyToId = new java.util.HashMap<>();

        if (dto.party() != null) {
            for (var pmDto : dto.party()) {
                var member = partyMemberService.create(saved.getId(),
                        pmDto.characterName(), pmDto.playerName(),
                        pmDto.classAndLevel(), pmDto.ac(), pmDto.maxHp(),
                        pmDto.initiativeBonus(), pmDto.speed(),
                        pmDto.passivePerception(), pmDto.passiveInsight(),
                        pmDto.passiveInvestigation(), pmDto.notes());
                if (!pmDto.active()) {
                    partyMemberService.setActive(member.getId(), false);
                }
                if (pmDto.sheet() != null) {
                    importSheet(member, pmDto.sheet());
                }
            }
        }
        if (dto.statBlocks() != null) {
            for (var sbDto : dto.statBlocks()) {
                StatBlock sb = statBlockService.createCustom(saved.getId(),
                        sbDto.name(), sbDto.cr(), sbDto.type(),
                        sbDto.ac(), sbDto.hp(), sbDto.speed(),
                        sbDto.strScore(), sbDto.dexScore(), sbDto.conScore(),
                        sbDto.intScore(), sbDto.wisScore(), sbDto.chaScore(),
                        sbDto.strSave(), sbDto.dexSave(), sbDto.conSave(),
                        sbDto.intSave(), sbDto.wisSave(), sbDto.chaSave(),
                        sbDto.skills(),
                        sbDto.damageVulnerabilities(), sbDto.damageResistances(),
                        sbDto.damageImmunities(), sbDto.conditionImmunities(),
                        sbDto.senses(), sbDto.languages(),
                        null, null);
                if (sbDto.size() != null) sb.setSize(sbDto.size());
                if (sbDto.sourceKey() != null) sb.setSourceKey(sbDto.sourceKey());
                if (sbDto.alignment() != null) sb.setAlignment(sbDto.alignment());
                if (sbDto.traits() != null) sb.setTraits(sbDto.traits());
                if (sbDto.actions() != null) sb.setActions(sbDto.actions());
                if (sbDto.bonusActions() != null) sb.setBonusActions(sbDto.bonusActions());
                if (sbDto.reactions() != null) sb.setReactions(sbDto.reactions());
                if (sbDto.legendaryActions() != null) sb.setLegendaryActions(sbDto.legendaryActions());
                if (sbDto.legendaryDescription() != null) sb.setLegendaryDescription(sbDto.legendaryDescription());
                if (sbDto.lairActions() != null) sb.setLairActions(sbDto.lairActions());
                sb.setXp(sbDto.xp());
                if (sbDto.sourceKey() != null) {
                    statblockKeyToId.put(sbDto.sourceKey(), sb.getId());
                }
            }
        }

        if (dto.maps() != null) {
            for (var mapDto : dto.maps()) {
                var grid = mapDto.grid();
                var map = gameMapService.create(saved.getId(), mapDto.name(),
                        grid != null ? grid.w() : 30,
                        grid != null ? grid.h() : 20,
                        grid != null ? grid.cellPx() : 48);
                mapKeyToId.put(mapDto.key(), map.getId());
                if (mapDto.movementMode() != null && !mapDto.movementMode().isBlank()) {
                    gameMapService.updateMode(map.getId(), mapDto.movementMode(), mapDto.showGrid());
                }
                if (mapDto.document() != null) {
                    gameMapService.updateDocument(map.getId(),
                            objectMapper.writeValueAsString(mapDto.document()),
                            map.getVersion());
                }
                if (mapDto.tokens() != null) {
                    for (var tDto : mapDto.tokens()) {
                        var token = new dev.hendrikhoemberg.dmhelper.gamemap.data.Token();
                        token.setMap(map);
                        token.setName(tDto.name());
                        token.setKind(tDto.kind());
                        token.setColor(tDto.color());
                        token.setPositionX(tDto.positionX());
                        token.setPositionY(tDto.positionY());
                        token.setSizeCols(tDto.sizeCols());
                        token.setSizeRows(tDto.sizeRows());
                        token.setHidden(tDto.hidden());
                        token.setCurrentHp(tDto.currentHp());
                        token.setMaxHp(tDto.maxHp());
                        token.setDead(tDto.dead());
                        token.setNotes(tDto.notes());
                        token = tokenRepo.save(token);
                        if (tDto.id() != null) {
                            tokenOldToNewId.put(tDto.id(), token.getId());
                        }
                    }
                }
            }
        }

        if (dto.handouts() != null) {
            for (var hDto : dto.handouts()) {
                if (hDto.imageData() != null) {
                    try {
                        String base64Data = hDto.imageData();
                        if (base64Data.startsWith("data:")) {
                            int commaIdx = base64Data.indexOf(',');
                            if (commaIdx > 0) {
                                base64Data = base64Data.substring(commaIdx + 1);
                            }
                        }
                        byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Data);
                        java.nio.file.Path filesDir = java.nio.file.Path.of(
                                System.getProperty("user.home"), ".dmhelper", "files");
                        java.nio.file.Path targetPath = filesDir.resolve(hDto.fileName());
                        java.nio.file.Files.createDirectories(targetPath.getParent());
                        java.nio.file.Files.write(targetPath, imageBytes);
                    } catch (Exception e) {
                        System.err.println("WARNING: Failed to import handout image '" + hDto.title() + "': " + e.getMessage());
                    }
                } else {
                    System.err.println("WARNING: Handout '" + hDto.title() + "' has no image data");
                }
                dev.hendrikhoemberg.dmhelper.handout.data.Handout handout =
                        new dev.hendrikhoemberg.dmhelper.handout.data.Handout();
                handout.setCampaign(saved);
                handout.setTitle(hDto.title());
                handout.setFileName(hDto.fileName());
                handout.setContentType(hDto.contentType());
                handout.setTags(hDto.tags() != null ? String.join(",", hDto.tags()) : null);
                handout.setDmOnly(true);
                handout.setPresented(false);
                handoutRepo.save(handout);
            }
        }

        List<PartyMember> partyMembers = partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(saved.getId());
        if (dto.encounters() != null) {
            for (var encDto : dto.encounters()) {
                var encounter = new dev.hendrikhoemberg.dmhelper.encounter.data.Encounter();
                encounter.setCampaign(saved);
                encounter.setName(encDto.name());
                encounter.setStatus(dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.valueOf(encDto.status()));
                encounter.setRound(encDto.round());
                encounter.setActiveTurnIndex(encDto.activeTurnIndex());
                encounter.setLogSequence(encDto.logSequence());
                encounter.setLairActionName(encDto.lairActionName());
                encounter.setLairActionDescription(encDto.lairActionDescription());
                encounter = encounterRepo.save(encounter);

                if (encDto.combatants() != null) {
                    for (var cDto : encDto.combatants()) {
                        var combatant = new dev.hendrikhoemberg.dmhelper.encounter.data.Combatant();
                        combatant.setEncounter(encounter);
                        combatant.setName(cDto.name());
                        combatant.setInitiative(cDto.initiative());
                        combatant.setTieBreaker(cDto.tieBreaker());
                        combatant.setSortOrder(cDto.sortOrder());
                        combatant.setMaxHp(cDto.maxHp());
                        combatant.setCurrentHp(cDto.currentHp());
                        combatant.setTempHp(cDto.tempHp());
                        combatant.setKind(cDto.kind());
                        combatant.setGroupId(cDto.groupId());
                        combatant.setGroupLeader(cDto.groupLeader());
                        if (cDto.tokenId() != null) {
                            UUID newTokenId = tokenOldToNewId.get(cDto.tokenId());
                            if (newTokenId != null) {
                                tokenRepo.findById(newTokenId).ifPresent(combatant::setToken);
                            } else {
                                try {
                                    tokenRepo.findById(java.util.UUID.fromString(cDto.tokenId()))
                                            .ifPresent(combatant::setToken);
                                } catch (Exception e) {
                                    System.err.println("WARNING: Invalid token ID: " + cDto.tokenId());
                                }
                            }
                        }
                        if (cDto.statBlockKey() != null) {
                            statBlockRepository.findByCampaignIdAndSourceKey(saved.getId(), cDto.statBlockKey())
                                    .ifPresentOrElse(combatant::setStatBlock,
                                            () -> System.err.println("WARNING: Unknown statblock key: " + cDto.statBlockKey()));
                        }
                        if (cDto.partyMemberName() != null) {
                            partyMembers.stream()
                                    .filter(pm -> pm.getCharacterName().equals(cDto.partyMemberName()))
                                    .findFirst()
                                    .ifPresentOrElse(combatant::setPartyMember,
                                            () -> System.err.println("WARNING: Unknown party member '" + cDto.partyMemberName() + "' in combatant '" + cDto.name() + "'"));
                        }
                        combatant.setDefeated(cDto.defeated());
                        combatant.setHidden(cDto.hidden());
                        combatant.setConditionsJson(cDto.conditionsJson());
                        combatant.setConcentratingOn(cDto.concentratingOn());
                        combatant.setConcentrationCheckPending(cDto.concentrationCheckPending());
                        combatant.setLegendaryActionsUsed(cDto.legendaryActionsUsed());
                        combatant.setLegendaryResistancesUsed(cDto.legendaryResistancesUsed());
                        combatant.setLegendaryActionsMax(cDto.legendaryActionsMax());
                        combatant.setLegendaryResistancesMax(cDto.legendaryResistancesMax());
                        combatant.setRechargedAbilities(cDto.rechargedAbilities());
                        combatant.setNotes(cDto.notes());
                        combatantRepo.save(combatant);
                    }
                }
            }
        }

        List<Note> importedNotes = new ArrayList<>();
        if (dto.notes() != null) {
            for (var noteDto : dto.notes()) {
                Note note = new Note();
                note.setCampaign(saved);
                note.setType(NoteType.valueOf(noteDto.type()));
                note.setTitle(noteDto.title());
                note.setBody(noteDto.body());
                note.setTags(noteDto.tags());
                note.setDmOnly(noteDto.dmOnly());
                note = noteRepository.save(note);
                importedNotes.add(note);
            }
        }
        for (var note : importedNotes) {
            noteService.rebuildLinks(note);
        }

        if (dto.quicknotes() != null) {
            for (var qnDto : dto.quicknotes()) {
                dev.hendrikhoemberg.dmhelper.notes.data.QuickNote qn =
                        new dev.hendrikhoemberg.dmhelper.notes.data.QuickNote();
                qn.setCampaign(saved);
                qn.setTargetType(qnDto.targetType());
                qn.setBody(qnDto.body());
                UUID resolvedId = null;
                if (qnDto.targetRef() != null) {
                    resolvedId = switch (qnDto.targetType()) {
                        case "MAP" -> mapKeyToId.get(qnDto.targetRef());
                        case "STATBLOCK" -> statblockKeyToId.get(qnDto.targetRef());
                        case "NOTE" -> {
                            var notes = noteRepository.findByCampaignIdAndTitle(saved.getId(), qnDto.targetRef());
                            yield notes.isEmpty() ? null : notes.get(0).getId();
                        }
                        case "HANDOUT" -> {
                            var handouts = handoutRepo.findByCampaignIdOrderByTitleAsc(saved.getId());
                            yield handouts.stream()
                                    .filter(h -> h.getTitle().equals(qnDto.targetRef()))
                                    .findFirst().map(h -> h.getId()).orElse(null);
                        }
                        default -> {
                            try {
                                yield java.util.UUID.fromString(qnDto.targetRef());
                            } catch (Exception e) {
                                yield null;
                            }
                        }
                    };
                    if (resolvedId == null) {
                        try {
                            resolvedId = java.util.UUID.fromString(qnDto.targetRef());
                        } catch (Exception e) { /* leave null */ }
                    }
                }
                if (resolvedId != null) {
                    qn.setTargetId(resolvedId);
                } else {
                    System.err.println("WARNING: Could not resolve target reference '" + qnDto.targetRef() + "' for quicknote of type " + qnDto.targetType());
                }
                quickNoteRepository.save(qn);
            }
        }

        if (dto.assignments() != null) {
            for (var aDto : dto.assignments()) {
                ItemAssignment ia = new ItemAssignment();
                ia.setCampaign(saved);
                if (aDto.holderName() != null) {
                    partyMembers.stream()
                        .filter(pm -> pm.getCharacterName().equals(aDto.holderName()))
                        .findFirst()
                        .ifPresentOrElse(ia::setPartyMember,
                                () -> System.err.println("WARNING: Unknown party member '" + aDto.holderName() + "' in assignment"));
                }
                if (aDto.magicItemKey() != null) {
                    magicItemRepo.findBySourceKey(aDto.magicItemKey())
                        .ifPresentOrElse(ia::setMagicItem,
                                () -> System.err.println("WARNING: Unknown magic item key '" + aDto.magicItemKey() + "' in assignment"));
                }
                if (aDto.equipmentItemKey() != null) {
                    equipmentItemRepo.findBySourceKey(aDto.equipmentItemKey())
                        .ifPresentOrElse(ia::setEquipmentItem,
                                () -> System.err.println("WARNING: Unknown equipment item key '" + aDto.equipmentItemKey() + "' in assignment"));
                }
                ia.setCustomText(aDto.customText());
                ia.setQuantity(aDto.quantity());
                ia.setAttuned(aDto.attuned());
                assignmentRepo.save(ia);
            }
        }

        if (dto.ledger() != null) {
            for (var leDto : dto.ledger()) {
                LedgerEntry le = new LedgerEntry();
                le.setCampaign(saved);
                le.setTimestamp(leDto.timestamp());
                le.setInGameYear(leDto.inGameYear());
                le.setInGameMonth(leDto.inGameMonth());
                le.setInGameDay(leDto.inGameDay());
                le.setKind(LedgerEntry.Kind.valueOf(leDto.kind()));
                le.setDirection(LedgerEntry.Direction.valueOf(leDto.direction()));
                le.setAmount(leDto.amount());
                le.setCurrency(leDto.currency());
                le.setHolder(leDto.holder());
                le.setNote(leDto.note());
                ledgerEntryRepo.save(le);
            }
        }

        if (dto.timeline() != null) {
            for (var teDto : dto.timeline()) {
                TimelineEvent te = new TimelineEvent();
                te.setCampaign(saved);
                te.setInGameYear(teDto.inGameYear());
                te.setInGameMonth(teDto.inGameMonth());
                te.setInGameDay(teDto.inGameDay());
                te.setTitle(teDto.title());
                te.setBody(teDto.body());
                if (teDto.noteTitle() != null) {
                    Optional<Note> note = noteRepository.findByCampaignIdAndTitle(saved.getId(), teDto.noteTitle())
                        .stream().findFirst();
                    note.ifPresent(te::setNoteRef);
                }
                timelineEventRepo.save(te);
            }
        }

        return saved;
    }

    private CampaignExportDto.PartyMemberExportDto toPartyMemberExport(PartyMember pm) {
        var dto = CampaignExportDto.PartyMemberExportDto.from(pm);
        var sheetOpt = sheetRepo.findByPartyMemberId(pm.getId());
        if (sheetOpt.isEmpty()) return dto;

        var sheet = sheetOpt.get();
        CampaignExportDto.SheetExportDto sheetDto = new CampaignExportDto.SheetExportDto(
                parseJsonMap(sheet.getAbilityScores()),
                parseSheetClassLevels(sheet.getClassLevels()),
                parseJsonMap(sheet.getProficiencies()),
                sheet.getSpecies() != null ? sheet.getSpecies().getSourceKey() : null,
                sheet.getBackground() != null ? sheet.getBackground().getSourceKey() : null,
                parseJsonList(sheet.getFeatRefs()),
                sheet.getXp(),
                parseJsonMap(sheet.getOverrides()),
                sheet.getHitDiceUsed(),
                resourceRepo.findBySheetId(sheet.getId()).stream()
                        .map(r -> new CampaignExportDto.ResourceExportDto(
                                r.getName(), r.getMaxUses(), r.getCurrentUses(), r.getResetRule().name()))
                        .toList(),
                spellRefRepo.findBySheetId(sheet.getId()).stream()
                        .map(s -> new CampaignExportDto.SpellRefExportDto(
                                s.getSpell() != null ? s.getSpell().getSourceKey() : null,
                                s.isPrepared(), s.getSourceClass()))
                        .toList(),
                parseJsonMap(sheet.getSpellSlotsUsed())
        );

        return new CampaignExportDto.PartyMemberExportDto(
                pm.getCharacterName(), pm.getPlayerName(), pm.getClassAndLevel(),
                pm.getAc(), pm.getMaxHp(), pm.getInitiativeBonus(), pm.getSpeed(),
                pm.getPassivePerception(), pm.getPassiveInsight(),
                pm.getPassiveInvestigation(), pm.getNotes(), pm.isActive(),
                sheetDto
        );
    }

    private void importSheet(PartyMember member, CampaignExportDto.SheetExportDto sheetDto) {
        CharacterSheet sheet = new CharacterSheet();
        sheet.setPartyMember(member);

        try {
            sheet.setAbilityScores(sheetDto.abilityScores() != null ?
                    objectMapper.writeValueAsString(sheetDto.abilityScores()) : null);
            sheet.setClassLevels(sheetDto.classLevels() != null ?
                    objectMapper.writeValueAsString(sheetDto.classLevels()) : null);
            sheet.setProficiencies(sheetDto.proficiencies() != null ?
                    objectMapper.writeValueAsString(sheetDto.proficiencies()) : null);
            sheet.setFeatRefs(sheetDto.featRefs() != null ?
                    objectMapper.writeValueAsString(sheetDto.featRefs()) : "[]");
            sheet.setOverrides(sheetDto.overrides() != null ?
                    objectMapper.writeValueAsString(sheetDto.overrides()) : "{}");
            sheet.setSpellSlotsUsed(sheetDto.spellSlotsUsed() != null ?
                    objectMapper.writeValueAsString(sheetDto.spellSlotsUsed()) : "{}");
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize sheet data", e);
        }

        sheet.setXp(sheetDto.xp());
        sheet.setHitDiceUsed(sheetDto.hitDiceUsed());

        if (sheetDto.speciesKey() != null) {
            var species = speciesRepo.findBySourceKey(sheetDto.speciesKey());
            if (species != null) {
                sheet.setSpecies(species);
            } else {
                System.err.println("WARNING: Unknown species key: " + sheetDto.speciesKey());
            }
        }
        if (sheetDto.backgroundKey() != null) {
            var background = backgroundRepo.findBySourceKey(sheetDto.backgroundKey());
            if (background != null) {
                sheet.setBackground(background);
            } else {
                System.err.println("WARNING: Unknown background key: " + sheetDto.backgroundKey());
            }
        }

        sheet = sheetRepo.save(sheet);

        if (sheetDto.resources() != null) {
            for (var resDto : sheetDto.resources()) {
                SheetResource sr = new SheetResource();
                sr.setSheet(sheet);
                sr.setName(resDto.name());
                sr.setMaxUses(resDto.maxUses());
                sr.setCurrentUses(resDto.currentUses());
                sr.setResetRule(SheetResource.ResetRule.valueOf(resDto.resetRule()));
                resourceRepo.save(sr);
            }
        }

        if (sheetDto.spells() != null) {
            for (var spellDto : sheetDto.spells()) {
                if (spellDto.spellKey() != null) {
                    var spell = spellRepo.findBySourceKey(spellDto.spellKey());
                    if (spell != null) {
                        SheetSpellReference ref = new SheetSpellReference();
                        ref.setSheet(sheet);
                        ref.setSpell(spell);
                        ref.setPrepared(spellDto.prepared());
                        ref.setSourceClass(spellDto.sourceClass());
                        spellRefRepo.save(ref);
                    } else {
                        System.err.println("WARNING: Unknown spell key: " + spellDto.spellKey());
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<CampaignExportDto.ClassLevelExportDto> parseSheetClassLevels(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, List.class);
            return raw.stream().map(m -> new CampaignExportDto.ClassLevelExportDto(
                    (String) m.get("classSourceKey"),
                    ((Number) m.get("level")).intValue(),
                    m.get("hitDieRolls") instanceof List<?> l ?
                            l.stream().map(o -> ((Number) o).intValue()).toList() : List.of()
            )).toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
