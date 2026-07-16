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
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignValidationResult;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository adventureRepo;
    private final dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository chapterRepo;
    private final dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository sceneRepo;
    private final dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository diceRollRepo;
    private final CampaignImportValidator importValidator;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager em;

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
                             TokenRepository tokenRepo,
                             dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository adventureRepo,
                             dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository chapterRepo,
                              dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository sceneRepo,
                              dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository diceRollRepo,
                              CampaignImportValidator importValidator) {
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
        this.adventureRepo = adventureRepo;
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.diceRollRepo = diceRollRepo;
        this.importValidator = importValidator;
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

    /**
     * Deleting a campaign takes its whole world with it.
     *
     * <p>Nothing in the schema cascades: every child row holds a foreign key back to the
     * campaign, so any survivor blocks the delete outright. Order is load-bearing, and the
     * flush between each stage is what makes it real — Hibernate is free to reorder queued
     * deletes within a single flush, and a delete that reaches the database out of order
     * trips the very constraint this method exists to respect.
     */
    public void delete(UUID id) {
        Campaign campaign = findById(id);
        UUID cid = campaign.getId();

        // Adventures first: scenes point at maps, encounters, handouts and statblocks, and
        // those references pin everything else in place until the story is gone.
        for (var adventure : adventureRepo.findByCampaignIdOrderBySortOrderAsc(cid)) {
            for (var chapter : chapterRepo.findByAdventureIdOrderBySortOrderAsc(adventure.getId())) {
                sceneRepo.deleteAll(sceneRepo.findByChapterIdOrderBySortOrderAsc(chapter.getId()));
                chapterRepo.delete(chapter);
            }
            adventureRepo.delete(adventure);
        }
        em.flush();

        // Combatants reference party members, statblocks and tokens — all of which outlive them here.
        var encounters = encounterRepo.findByCampaignIdOrderByNameAsc(cid);
        for (var encounter : encounters) {
            combatantRepo.deleteByEncounterId(encounter.getId());
        }
        em.flush();
        encounterRepo.deleteAll(encounters);
        em.flush();

        for (var map : gameMapService.findByCampaignId(cid)) {
            tokenRepo.deleteByMapId(map.getId());
            em.flush();
            gameMapService.delete(map.getId());
        }
        em.flush();

        // Ledger rows reference assignments, and timeline rows may reference notes.
        ledgerEntryRepo.deleteAll(ledgerEntryRepo.findByCampaignIdOrderByTimestampDesc(cid));
        timelineEventRepo.deleteAll(
                timelineEventRepo.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(cid));
        em.flush();

        // Treasury assignments name party members, so they go before the party does.
        assignmentRepo.deleteAll(assignmentRepo.findByCampaignIdOrderByPartyMemberAsc(cid));
        em.flush();

        // Character sheets ride along on the party member (cascade + orphanRemoval).
        for (var member : partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(cid)) {
            partyMemberService.delete(member.getId());
        }
        em.flush();

        statBlockRepository.deleteAll(statBlockRepository.findByCampaignIdOrderByNameAsc(cid));
        em.flush();

        // noteService.delete also clears the wiki links leaving each note.
        for (var note : noteRepository.findByCampaignIdOrderByCreatedAtDesc(cid)) {
            noteService.delete(note.getId());
        }
        quickNoteRepository.deleteAll(quickNoteRepository.findByCampaignIdOrderByCreatedAtDesc(cid));
        em.flush();

        // handoutService.delete takes the uploaded file with the row.
        for (var handout : handoutRepo.findByCampaignIdOrderByTitleAsc(cid)) {
            handoutService.delete(handout.getId());
        }
        em.flush();

        diceRollRepo.deleteAll(diceRollRepo.findByCampaignId(cid));
        em.flush();

        repository.delete(campaign);
    }

    public void setMilestoneMode(UUID campaignId, boolean enabled) {
        Campaign c = findById(campaignId);
        c.setMilestoneLeveling(enabled);
        repository.save(c);
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
                    } catch (IOException e) {
                        throw new IllegalStateException(
                                "Cannot export campaign because handout '" + h.getTitle() + "' has no readable asset.", e);
                    }
                    return CampaignExportDto.HandoutExportDto.from(h, imageBase64);
                })
                .toList();

        var noteEntities = noteRepository.findByCampaignIdOrderByCreatedAtDesc(id);
        var noteDtos = noteEntities.stream()
                .map(CampaignExportDto.NoteExportDto::from).toList();
        var quickNoteEntities = quickNoteRepository.findByCampaignIdOrderByCreatedAtDesc(id);
        java.util.Map<java.util.UUID, String> quickNoteIdMappings = new java.util.HashMap<>();
        for (var gm : gameMaps) {
            quickNoteIdMappings.put(gm.getId(), gm.getId().toString());
        }
        for (var sb : statBlockEntities) {
            if (sb.getSourceKey() != null) quickNoteIdMappings.put(sb.getId(), sb.getSourceKey());
        }
        for (var quickNote : quickNoteEntities) {
            if ("STATBLOCK".equals(quickNote.getTargetType())) {
                statBlockRepository.findById(quickNote.getTargetId())
                        .filter(statBlock -> statBlock.getSourceKey() != null)
                        .ifPresent(statBlock -> quickNoteIdMappings.put(
                                statBlock.getId(), statBlock.getSourceKey()));
            }
        }
        for (var note : noteEntities) {
            quickNoteIdMappings.put(note.getId(), note.getTitle());
        }
        for (var handout : handoutEntities) {
            quickNoteIdMappings.put(handout.getId(), handout.getTitle());
        }
        for (var enc : encounterRepo.findByCampaignIdOrderByNameAsc(id)) {
            String ref = enc.getEncounterKey() != null && !enc.getEncounterKey().isBlank()
                    ? enc.getEncounterKey() : enc.getName();
            quickNoteIdMappings.put(enc.getId(), ref);
        }
        for (var pm : partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(id)) {
            quickNoteIdMappings.put(pm.getId(), pm.getCharacterName());
        }
        for (var adv : adventureRepo.findByCampaignIdOrderBySortOrderAsc(id)) {
            for (var ch : chapterRepo.findByAdventureIdOrderBySortOrderAsc(adv.getId())) {
                for (var scene : sceneRepo.findByChapterIdOrderBySortOrderAsc(ch.getId())) {
                    quickNoteIdMappings.put(scene.getId(),
                            adv.getName() + "/" + ch.getTitle() + "/" + scene.getSceneKey());
                }
            }
        }
        quickNoteIdMappings.put(id, "CAMPAIGN");
        var quickNoteDtos = quickNoteEntities.stream()
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
                        le.getHolder(), le.getNote(),
                        le.getItemAssignmentRef() != null ?
                                le.getItemAssignmentRef().getId().toString() : null))
                .toList();

        var timelineEvents = timelineEventRepo.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(id).stream()
                .map(te -> new CampaignExportDto.TimelineExportDto(
                        te.getId(), te.getInGameYear(), te.getInGameMonth(), te.getInGameDay(),
                        te.getTitle(), te.getBody(),
                        te.getNoteRef() != null ? te.getNoteRef().getTitle() : null))
                .toList();

        var adventures = adventureRepo.findByCampaignIdOrderBySortOrderAsc(id).stream()
                .map(adv -> {
                    var chapters = chapterRepo.findByAdventureIdOrderBySortOrderAsc(adv.getId()).stream()
                            .map(ch -> {
                                var scenes = sceneRepo.findByChapterIdOrderBySortOrderAsc(ch.getId()).stream()
                                        .map(scene -> {
                                            java.util.Map<String, Integer> pin = null;
                                            if (scene.getPinX() != null) {
                                                pin = java.util.Map.of("x", scene.getPinX(), "y", scene.getPinY());
                                            }
                                            String encounterRef = null;
                                            if (scene.getEncounter() != null) {
                                                encounterRef = scene.getEncounter().getEncounterKey();
                                                if (encounterRef == null || encounterRef.isBlank()) {
                                                    encounterRef = scene.getEncounter().getName();
                                                }
                                            }
                                            return new CampaignExportDto.SceneExportDto(
                                                    scene.getTitle(), scene.getSceneKey(), scene.getBody(),
                                                    scene.getStatus().name(), scene.getSortOrder(),
                                                    scene.getMap() != null ? scene.getMap().getName() : null,
                                                    pin,
                                                    encounterRef,
                                                    scene.getStatBlocks().stream()
                                                            .map(sb -> sb.getSourceKey() != null ? sb.getSourceKey() : sb.getName())
                                                            .toList(),
                                                    scene.getHandouts().stream()
                                                            .map(h -> h.getTitle())
                                                            .toList()
                                            );
                                        }).toList();
                                return new CampaignExportDto.ChapterExportDto(
                                        ch.getTitle(), ch.getIntro(), ch.getSortOrder(), scenes);
                            }).toList();
                    return new CampaignExportDto.AdventureExportDto(
                            adv.getName(), adv.getDescription(), adv.getSourceAttribution(),
                            adv.getSortOrder(), chapters);
                }).toList();

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
                timelineEvents,
                adventures
        );
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export campaign", e);
        }
    }

    /** Returns the local entity IDs in the same stable pointer order used by {@link #exportToJson(UUID)}. */
    @Transactional(readOnly = true)
    public Map<String, UUID> exportEntityIds(UUID campaignId) {
        Map<String, UUID> ids = new java.util.LinkedHashMap<>();
        ids.put("/campaign", findById(campaignId).getId());

        var party = partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(campaignId);
        for (int i = 0; i < party.size(); i++) {
            var member = party.get(i);
            String pointer = "/party/" + i;
            ids.put(pointer, member.getId());
            sheetRepo.findByPartyMemberId(member.getId()).ifPresent(sheet -> {
                ids.put(pointer + "/sheet", sheet.getId());
                var resources = resourceRepo.findBySheetId(sheet.getId());
                for (int j = 0; j < resources.size(); j++) {
                    ids.put(pointer + "/sheet/resources/" + j, resources.get(j).getId());
                }
            });
        }
        putIds(ids, "/statBlocks/", statBlockRepository.findByCampaignIdOrderByNameAsc(campaignId));

        var maps = gameMapService.findByCampaignId(campaignId);
        for (int i = 0; i < maps.size(); i++) {
            ids.put("/maps/" + i, maps.get(i).getId());
            putIds(ids, "/maps/" + i + "/tokens/", tokenRepo.findByMapIdOrderByNameAsc(maps.get(i).getId()));
        }
        var encounters = encounterRepo.findByCampaignIdOrderByNameAsc(campaignId);
        for (int i = 0; i < encounters.size(); i++) {
            ids.put("/encounters/" + i, encounters.get(i).getId());
            putIds(ids, "/encounters/" + i + "/combatants/",
                    combatantRepo.findByEncounterIdOrderBySortOrderAsc(encounters.get(i).getId()));
        }
        putIds(ids, "/handouts/", handoutRepo.findByCampaignIdOrderByTitleAsc(campaignId));
        putIds(ids, "/notes/", noteRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId));
        putIds(ids, "/quicknotes/", quickNoteRepository.findByCampaignIdOrderByCreatedAtDesc(campaignId));
        putIds(ids, "/assignments/", assignmentRepo.findByCampaignIdOrderByPartyMemberAsc(campaignId));
        putIds(ids, "/ledger/", ledgerEntryRepo.findByCampaignIdOrderByTimestampDesc(campaignId));
        putIds(ids, "/timeline/",
                timelineEventRepo.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(campaignId));

        var adventures = adventureRepo.findByCampaignIdOrderBySortOrderAsc(campaignId);
        for (int ai = 0; ai < adventures.size(); ai++) {
            var adventure = adventures.get(ai);
            ids.put("/adventures/" + ai, adventure.getId());
            var chapters = chapterRepo.findByAdventureIdOrderBySortOrderAsc(adventure.getId());
            for (int ci = 0; ci < chapters.size(); ci++) {
                var chapter = chapters.get(ci);
                String chapterPointer = "/adventures/" + ai + "/chapters/" + ci;
                ids.put(chapterPointer, chapter.getId());
                putIds(ids, chapterPointer + "/scenes/",
                        sceneRepo.findByChapterIdOrderBySortOrderAsc(chapter.getId()));
            }
        }
        return Map.copyOf(ids);
    }

    private void putIds(Map<String, UUID> target, String pointerPrefix, List<?> entities) {
        for (int i = 0; i < entities.size(); i++) {
            UUID id = (UUID) em.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entities.get(i));
            target.put(pointerPrefix + i, id);
        }
    }

    @Transactional(readOnly = true)
    public CampaignValidationResult validateImport(String json) {
        return importValidator.validate(json);
    }

    public Campaign importFromJson(String json) {
        CampaignExportDto dto = importValidator.validate(json).requireImportable();

        return importValidated(dto);
    }

    /**
     * Persists an already validated v1 compatibility document and returns the local IDs for every
     * addressable entity.
     */
    public Campaign importValidated(CampaignExportDto dto) {
        Map<String, UUID> persistedIds = new java.util.LinkedHashMap<>();

        Campaign saved = create(dto.campaign().name(), dto.campaign().description());
        putPersistedId(persistedIds, "/campaign", saved.getId());

        java.util.Map<String, UUID> mapKeyToId = new java.util.HashMap<>();
        java.util.Map<String, UUID> mapNameToId = new java.util.HashMap<>();
        java.util.Map<String, UUID> tokenOldToNewId = new java.util.HashMap<>();
        java.util.Map<String, UUID> encounterKeyToId = new java.util.HashMap<>();
        java.util.Map<String, UUID> encounterNameToId = new java.util.HashMap<>();
        java.util.Map<String, UUID> partyMemberNameToId = new java.util.HashMap<>();
        java.util.Map<String, UUID> handoutTitleToId = new java.util.HashMap<>();
        java.util.Map<String, UUID> scenePathToId = new java.util.HashMap<>();

        if (dto.party() != null) {
            for (int partyIndex = 0; partyIndex < dto.party().size(); partyIndex++) {
                var pmDto = dto.party().get(partyIndex);
                var member = partyMemberService.create(saved.getId(),
                        pmDto.characterName(), pmDto.playerName(),
                        pmDto.classAndLevel(), pmDto.ac(), pmDto.maxHp(),
                        pmDto.initiativeBonus(), pmDto.speed(),
                        pmDto.passivePerception(), pmDto.passiveInsight(),
                        pmDto.passiveInvestigation(), pmDto.notes());
                String partyPointer = "/party/" + partyIndex;
                putPersistedId(persistedIds, partyPointer, member.getId());
                if (!pmDto.active()) {
                    partyMemberService.setActive(member.getId(), false);
                }
                if (pmDto.sheet() != null) {
                    importSheet(member, pmDto.sheet(), partyPointer + "/sheet", persistedIds);
                }
                partyMemberNameToId.put(pmDto.characterName(), member.getId());
            }
        }
        if (dto.statBlocks() != null) {
            for (int statIndex = 0; statIndex < dto.statBlocks().size(); statIndex++) {
                var sbDto = dto.statBlocks().get(statIndex);
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
                putPersistedId(persistedIds, "/statBlocks/" + statIndex, sb.getId());
            }
        }

        if (dto.maps() != null) {
            for (int mapIndex = 0; mapIndex < dto.maps().size(); mapIndex++) {
                var mapDto = dto.maps().get(mapIndex);
                var grid = mapDto.grid();
                var map = gameMapService.create(saved.getId(), mapDto.name(),
                        grid != null ? grid.w() : 30,
                        grid != null ? grid.h() : 20,
                        grid != null ? grid.cellPx() : 48);
                mapKeyToId.put(mapDto.key(), map.getId());
                mapNameToId.put(mapDto.name(), map.getId());
                putPersistedId(persistedIds, "/maps/" + mapIndex, map.getId());
                if (mapDto.movementMode() != null && !mapDto.movementMode().isBlank()) {
                    gameMapService.updateMode(map.getId(), mapDto.movementMode(), mapDto.showGrid());
                }
                if (mapDto.document() != null) {
                    gameMapService.updateDocument(map.getId(),
                            objectMapper.writeValueAsString(mapDto.document()),
                            map.getVersion());
                }
                if (mapDto.tokens() != null) {
                    for (int tokenIndex = 0; tokenIndex < mapDto.tokens().size(); tokenIndex++) {
                        var tDto = mapDto.tokens().get(tokenIndex);
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
                        if (tDto.statBlockKey() != null) {
                            var resolved = statBlockRepository.findByCampaignIdAndSourceKey(saved.getId(), tDto.statBlockKey())
                                    .or(() -> statBlockRepository.findBySourceKey(tDto.statBlockKey()));
                            token.setStatBlock(resolved.orElseThrow(() -> new IllegalStateException(
                                    "Validated reference disappeared: statblock key '" + tDto.statBlockKey() + "' for token '" + tDto.name() + "'")));
                        }
                        if (tDto.partyMemberName() != null) {
                            var pm = partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(saved.getId()).stream()
                                    .filter(p -> tDto.partyMemberName().equals(p.getCharacterName()))
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalStateException(
                                            "Validated reference disappeared: party member '" + tDto.partyMemberName() + "' for token '" + tDto.name() + "'"));
                            token.setPartyMember(pm);
                        }
                        token = tokenRepo.save(token);
                        putPersistedId(persistedIds, "/maps/" + mapIndex + "/tokens/" + tokenIndex, token.getId());
                        if (tDto.id() != null) {
                            tokenOldToNewId.put(tDto.id(), token.getId());
                        }
                    }
                }
            }
        }

        if (dto.handouts() != null) {
            for (int handoutIndex = 0; handoutIndex < dto.handouts().size(); handoutIndex++) {
                var hDto = dto.handouts().get(handoutIndex);
                Handout handout;
                try {
                    String tagStr = hDto.tags() != null ? String.join(",", hDto.tags()) : null;
                    String base64Data = hDto.imageData();
                    if (base64Data.startsWith("data:")) {
                        int commaIdx = base64Data.indexOf(',');
                        if (commaIdx > 0) {
                            String prefix = base64Data.substring(0, commaIdx);
                            String expectedPrefix = "data:" + hDto.contentType() + ";base64";
                            if (!prefix.equals(expectedPrefix)) {
                                throw new IllegalStateException(
                                        "Handout '" + hDto.title() + "' content type mismatch: " + prefix);
                            }
                            base64Data = base64Data.substring(commaIdx + 1);
                        }
                    }
                    byte[] imageBytes;
                    try {
                        imageBytes = java.util.Base64.getDecoder().decode(base64Data);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalStateException(
                                "Handout '" + hDto.title() + "' has invalid base64 image data", e);
                    }
                    handout = handoutService.createImported(saved.getId(), hDto.title(), tagStr,
                            hDto.fileName(), hDto.contentType(), imageBytes);
                } catch (IOException e) {
                    throw new IllegalStateException(
                            "Failed to import handout '" + hDto.title() + "'", e);
                }
                handoutTitleToId.put(hDto.title(), handout.getId());
                putPersistedId(persistedIds, "/handouts/" + handoutIndex, handout.getId());
            }
        }

        List<PartyMember> partyMembers = partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(saved.getId());
        if (dto.encounters() != null) {
            for (int encounterIndex = 0; encounterIndex < dto.encounters().size(); encounterIndex++) {
                var encDto = dto.encounters().get(encounterIndex);
                var encounter = new dev.hendrikhoemberg.dmhelper.encounter.data.Encounter();
                encounter.setCampaign(saved);
                encounter.setName(encDto.name());
                encounter.setStatus(dev.hendrikhoemberg.dmhelper.encounter.data.Encounter.Status.valueOf(encDto.status()));
                encounter.setRound(encDto.round());
                encounter.setActiveTurnIndex(encDto.activeTurnIndex());
                encounter.setLogSequence(encDto.logSequence());
                encounter.setLairActionName(encDto.lairActionName());
                encounter.setLairActionDescription(encDto.lairActionDescription());
                if (encDto.encounterKey() != null) {
                    encounter.setEncounterKey(encDto.encounterKey());
                }
                if (encDto.map() != null) {
                    UUID mapId = mapKeyToId.get(encDto.map());
                    if (mapId == null) mapId = mapNameToId.get(encDto.map());
                    if (mapId != null) {
                        encounter.setMap(gameMapService.findById(mapId));
                    } else {
                        throw new IllegalStateException(
                                "Validated reference disappeared: map '" + encDto.map() + "' for encounter '" + encDto.name() + "'");
                    }
                }
                encounter = encounterRepo.save(encounter);
                putPersistedId(persistedIds, "/encounters/" + encounterIndex, encounter.getId());
                encounterNameToId.put(encDto.name(), encounter.getId());
                if (encounter.getEncounterKey() != null) {
                    encounterKeyToId.put(encounter.getEncounterKey(), encounter.getId());
                }

                if (encDto.combatants() != null) {
                    for (int combatantIndex = 0; combatantIndex < encDto.combatants().size(); combatantIndex++) {
                        var cDto = encDto.combatants().get(combatantIndex);
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
                                combatant.setToken(tokenRepo.findById(newTokenId)
                                        .orElseThrow(() -> new IllegalStateException(
                                                "Validated reference disappeared: token '" + cDto.tokenId() + "' in combatant '" + cDto.name() + "'")));
                            } else {
                                combatant.setToken(tokenRepo.findById(java.util.UUID.fromString(cDto.tokenId()))
                                        .orElseThrow(() -> new IllegalStateException(
                                                "Validated reference disappeared: token '" + cDto.tokenId() + "' in combatant '" + cDto.name() + "'")));
                            }
                        }
                        if (cDto.statBlockKey() != null) {
                            var resolved = statBlockRepository.findByCampaignIdAndSourceKey(saved.getId(), cDto.statBlockKey())
                                    .or(() -> statBlockRepository.findBySourceKey(cDto.statBlockKey()));
                            combatant.setStatBlock(resolved.orElseThrow(() -> new IllegalStateException(
                                    "Validated reference disappeared: statblock key '" + cDto.statBlockKey() + "' for combatant '" + cDto.name() + "'")));
                        }
                        if (cDto.partyMemberName() != null) {
                            var pm = partyMembers.stream()
                                    .filter(p -> p.getCharacterName().equals(cDto.partyMemberName()))
                                    .findFirst()
                                    .orElseThrow(() -> new IllegalStateException(
                                            "Validated reference disappeared: party member '" + cDto.partyMemberName() + "' in combatant '" + cDto.name() + "'"));
                            combatant.setPartyMember(pm);
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
                        var persistedCombatant = combatantRepo.save(combatant);
                        if (persistedCombatant != null && persistedCombatant.getId() != null) {
                            putPersistedId(persistedIds,
                                    "/encounters/" + encounterIndex + "/combatants/" + combatantIndex,
                                    persistedCombatant.getId());
                        }
                    }
                }
            }
        }

        if (dto.adventures() != null) {
            for (int adventureIndex = 0; adventureIndex < dto.adventures().size(); adventureIndex++) {
                var advDto = dto.adventures().get(adventureIndex);
                var adv = new dev.hendrikhoemberg.dmhelper.adventure.data.Adventure();
                adv.setCampaign(saved);
                adv.setName(advDto.name());
                adv.setDescription(advDto.description());
                adv.setSourceAttribution(advDto.sourceAttribution());
                adv.setSortOrder(advDto.sortOrder());
                adv = adventureRepo.save(adv);
                putPersistedId(persistedIds, "/adventures/" + adventureIndex, adv.getId());
                if (advDto.chapters() != null) {
                    for (int chapterIndex = 0; chapterIndex < advDto.chapters().size(); chapterIndex++) {
                        var chDto = advDto.chapters().get(chapterIndex);
                        var ch = new dev.hendrikhoemberg.dmhelper.adventure.data.Chapter();
                        ch.setAdventure(adv);
                        ch.setTitle(chDto.title());
                        ch.setIntro(chDto.intro());
                        ch.setSortOrder(chDto.sortOrder());
                        ch = chapterRepo.save(ch);
                        putPersistedId(persistedIds,
                                "/adventures/" + adventureIndex + "/chapters/" + chapterIndex, ch.getId());
                        if (chDto.scenes() != null) {
                            for (int sceneIndex = 0; sceneIndex < chDto.scenes().size(); sceneIndex++) {
                                var scDto = chDto.scenes().get(sceneIndex);
                                var sc = new dev.hendrikhoemberg.dmhelper.adventure.data.Scene();
                                sc.setChapter(ch);
                                sc.setTitle(scDto.title());
                                sc.setSceneKey(scDto.sceneKey());
                                sc.setBody(scDto.body());
                                sc.setStatus(scDto.status() != null ?
                                        dev.hendrikhoemberg.dmhelper.adventure.data.SceneStatus.valueOf(scDto.status()) :
                                        dev.hendrikhoemberg.dmhelper.adventure.data.SceneStatus.UNVISITED);
                                sc.setSortOrder(scDto.sortOrder());
                                if (scDto.map() != null) {
                                    UUID mapId = mapKeyToId.get(scDto.map());
                                    if (mapId == null) mapId = mapNameToId.get(scDto.map());
                                    if (mapId != null) {
                                        sc.setMap(gameMapService.findById(mapId));
                                    } else {
                                        throw new IllegalStateException(
                                                "Validated reference disappeared: map '" + scDto.map() + "' for scene '" + scDto.title() + "'");
                                    }
                                }
                                if (scDto.pin() != null) {
                                    sc.setPinX(scDto.pin().get("x"));
                                    sc.setPinY(scDto.pin().get("y"));
                                }
                                if (scDto.encounter() != null) {
                                    UUID encId = encounterKeyToId.get(scDto.encounter());
                                    if (encId == null) {
                                        encId = encounterNameToId.get(scDto.encounter());
                                    }
                                    if (encId != null) {
                                        sc.setEncounter(encounterRepo.findById(encId)
                                                .orElseThrow(() -> new IllegalStateException(
                                                        "Validated reference disappeared: encounter '" + scDto.encounter() + "' for scene '" + scDto.title() + "'")));
                                    } else {
                                        throw new IllegalStateException(
                                                "Validated reference disappeared: encounter '" + scDto.encounter() + "' for scene '" + scDto.title() + "'");
                                    }
                                }
                                sc = sceneRepo.save(sc);
                                putPersistedId(persistedIds, "/adventures/" + adventureIndex + "/chapters/"
                                        + chapterIndex + "/scenes/" + sceneIndex, sc.getId());
                                scenePathToId.put(advDto.name() + "/" + chDto.title() + "/" + sc.getSceneKey(), sc.getId());
                                if (scDto.statblocks() != null) {
                                    for (var sbKey : scDto.statblocks()) {
                                        StatBlock resolved = statBlockRepository
                                                .findByCampaignIdAndSourceKey(saved.getId(), sbKey)
                                                .or(() -> statBlockRepository.findBySourceKey(sbKey))
                                                .orElseThrow(() -> new IllegalStateException(
                                                        "Validated reference disappeared: statblock key '" + sbKey
                                                                + "' for scene '" + scDto.title() + "'"));
                                        sc.getStatBlocks().add(resolved);
                                    }
                                    if (!scDto.statblocks().isEmpty()) {
                                        sceneRepo.save(sc);
                                    }
                                }
                                if (scDto.handouts() != null) {
                                    for (var hTitle : scDto.handouts()) {
                                        UUID handoutId = handoutTitleToId.get(hTitle);
                                        if (handoutId != null) {
                                            sc.getHandouts().add(handoutRepo.findById(handoutId)
                                                    .orElseThrow(() -> new IllegalStateException(
                                                            "Validated reference disappeared: handout '" + hTitle + "' for scene '" + scDto.title() + "'")));
                                        } else {
                                            throw new IllegalStateException(
                                                    "Validated reference disappeared: handout '" + hTitle + "' for scene '" + scDto.title() + "'");
                                        }
                                    }
                                    if (!scDto.handouts().isEmpty()) {
                                        sceneRepo.save(sc);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        List<Note> importedNotes = new ArrayList<>();
        if (dto.notes() != null) {
            for (int noteIndex = 0; noteIndex < dto.notes().size(); noteIndex++) {
                var noteDto = dto.notes().get(noteIndex);
                Note note = new Note();
                note.setCampaign(saved);
                note.setType(NoteType.valueOf(noteDto.type()));
                note.setTitle(noteDto.title());
                note.setBody(noteDto.body());
                note.setTags(noteDto.tags());
                note.setDmOnly(noteDto.dmOnly());
                note = noteRepository.save(note);
                importedNotes.add(note);
                putPersistedId(persistedIds, "/notes/" + noteIndex, note.getId());
            }
        }
        for (var note : importedNotes) {
            noteService.rebuildLinks(note);
        }

        if (dto.quicknotes() != null) {
            for (int quickNoteIndex = 0; quickNoteIndex < dto.quicknotes().size(); quickNoteIndex++) {
                var qnDto = dto.quicknotes().get(quickNoteIndex);
                dev.hendrikhoemberg.dmhelper.notes.data.QuickNote qn =
                        new dev.hendrikhoemberg.dmhelper.notes.data.QuickNote();
                qn.setCampaign(saved);
                qn.setTargetType(qnDto.targetType());
                qn.setBody(qnDto.body());
                UUID resolvedId = null;
                if (qnDto.targetRef() != null) {
                    resolvedId = switch (qnDto.targetType()) {
                        case "MAP" -> mapKeyToId.get(qnDto.targetRef());
                        case "STATBLOCK" -> statBlockRepository
                                .findByCampaignIdAndSourceKey(saved.getId(), qnDto.targetRef())
                                .or(() -> statBlockRepository.findBySourceKey(qnDto.targetRef()))
                                .map(StatBlock::getId).orElse(null);
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
                        case "ENCOUNTER" -> {
                            UUID encId = encounterKeyToId.get(qnDto.targetRef());
                            if (encId == null) encId = encounterNameToId.get(qnDto.targetRef());
                            yield encId;
                        }
                        case "PARTY_MEMBER" -> partyMemberNameToId.get(qnDto.targetRef());
                        case "CAMPAIGN" -> saved.getId();
                        case "SCENE" -> scenePathToId.get(qnDto.targetRef());
                        default -> throw new IllegalStateException(
                                "Validated quicknote target type disappeared from the v1 contract: "
                                        + qnDto.targetType());
                    };
                }
                if (resolvedId == null) {
                    throw new IllegalStateException(
                            "Validated reference disappeared: target reference '" + qnDto.targetRef()
                            + "' for quicknote of type " + qnDto.targetType());
                }
                qn.setTargetId(resolvedId);
                if (qnDto.createdAt() != null) {
                    qn.setCreatedAt(java.time.Instant.parse(qnDto.createdAt()));
                }
                var persistedQuickNote = quickNoteRepository.save(qn);
                if (persistedQuickNote != null && persistedQuickNote.getId() != null) {
                    putPersistedId(persistedIds, "/quicknotes/" + quickNoteIndex, persistedQuickNote.getId());
                }
            }
        }

        java.util.Map<String, UUID> assignmentIdMap = new java.util.HashMap<>();
        if (dto.assignments() != null) {
            for (int assignmentIndex = 0; assignmentIndex < dto.assignments().size(); assignmentIndex++) {
                var aDto = dto.assignments().get(assignmentIndex);
                ItemAssignment ia = new ItemAssignment();
                ia.setCampaign(saved);
                if (aDto.holderName() != null) {
                    var pm = partyMembers.stream()
                        .filter(p -> p.getCharacterName().equals(aDto.holderName()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Validated reference disappeared: party member '" + aDto.holderName() + "' in assignment"));
                    ia.setPartyMember(pm);
                }
                if (aDto.magicItemKey() != null) {
                    ia.setMagicItem(magicItemRepo.findBySourceKey(aDto.magicItemKey())
                        .orElseThrow(() -> new IllegalStateException(
                                "Validated reference disappeared: magic item key '" + aDto.magicItemKey() + "' in assignment")));
                }
                if (aDto.equipmentItemKey() != null) {
                    ia.setEquipmentItem(equipmentItemRepo.findBySourceKey(aDto.equipmentItemKey())
                        .orElseThrow(() -> new IllegalStateException(
                                "Validated reference disappeared: equipment item key '" + aDto.equipmentItemKey() + "' in assignment")));
                }
                ia.setCustomText(aDto.customText());
                ia.setQuantity(aDto.quantity());
                ia.setAttuned(aDto.attuned());
                ia = assignmentRepo.save(ia);
                assignmentIdMap.put(aDto.id().toString(), ia.getId());
                putPersistedId(persistedIds, "/assignments/" + assignmentIndex, ia.getId());
            }
        }

        if (dto.ledger() != null) {
            for (int ledgerIndex = 0; ledgerIndex < dto.ledger().size(); ledgerIndex++) {
                var leDto = dto.ledger().get(ledgerIndex);
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
                if (leDto.itemAssignmentRef() != null) {
                    UUID newAssignmentId = assignmentIdMap.get(leDto.itemAssignmentRef());
                    if (newAssignmentId != null) {
                        le.setItemAssignmentRef(assignmentRepo.findById(newAssignmentId)
                                .orElseThrow(() -> new IllegalStateException(
                                        "Validated reference disappeared: assignment '" + leDto.itemAssignmentRef() + "' in ledger entry")));
                    } else {
                        throw new IllegalStateException(
                                "Validated reference disappeared: assignment '" + leDto.itemAssignmentRef() + "' in ledger entry");
                    }
                }
                var persistedLedgerEntry = ledgerEntryRepo.save(le);
                if (persistedLedgerEntry != null && persistedLedgerEntry.getId() != null) {
                    putPersistedId(persistedIds, "/ledger/" + ledgerIndex, persistedLedgerEntry.getId());
                }
            }
        }

        if (dto.timeline() != null) {
            for (int timelineIndex = 0; timelineIndex < dto.timeline().size(); timelineIndex++) {
                var teDto = dto.timeline().get(timelineIndex);
                TimelineEvent te = new TimelineEvent();
                te.setCampaign(saved);
                te.setInGameYear(teDto.inGameYear());
                te.setInGameMonth(teDto.inGameMonth());
                te.setInGameDay(teDto.inGameDay());
                te.setTitle(teDto.title());
                te.setBody(teDto.body());
                if (teDto.noteTitle() != null) {
                    te.setNoteRef(noteRepository.findByCampaignIdAndTitle(saved.getId(), teDto.noteTitle())
                        .stream().findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "Validated reference disappeared: note '" + teDto.noteTitle() + "' in timeline event")));
                }
                var persistedTimelineEvent = timelineEventRepo.save(te);
                if (persistedTimelineEvent != null && persistedTimelineEvent.getId() != null) {
                    putPersistedId(persistedIds, "/timeline/" + timelineIndex, persistedTimelineEvent.getId());
                }
            }
        }

        return saved;
    }

    private static void putPersistedId(Map<String, UUID> persistedIds, String pointer, UUID id) {
        if (id != null) persistedIds.put(pointer, id);
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

    private void importSheet(PartyMember member, CampaignExportDto.SheetExportDto sheetDto,
                             String pointer, Map<String, UUID> persistedIds) {
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
                throw new IllegalStateException(
                        "Validated reference disappeared: species key '" + sheetDto.speciesKey() + "' in sheet");
            }
        }
        if (sheetDto.backgroundKey() != null) {
            var background = backgroundRepo.findBySourceKey(sheetDto.backgroundKey());
            if (background != null) {
                sheet.setBackground(background);
            } else {
                throw new IllegalStateException(
                        "Validated reference disappeared: background key '" + sheetDto.backgroundKey() + "' in sheet");
            }
        }

        sheet = sheetRepo.save(sheet);
        putPersistedId(persistedIds, pointer, sheet.getId());

        if (sheetDto.resources() != null) {
            for (int resourceIndex = 0; resourceIndex < sheetDto.resources().size(); resourceIndex++) {
                var resDto = sheetDto.resources().get(resourceIndex);
                SheetResource sr = new SheetResource();
                sr.setSheet(sheet);
                sr.setName(resDto.name());
                sr.setMaxUses(resDto.maxUses());
                sr.setCurrentUses(resDto.currentUses());
                sr.setResetRule(SheetResource.ResetRule.valueOf(resDto.resetRule()));
                var persistedResource = resourceRepo.save(sr);
                if (persistedResource != null && persistedResource.getId() != null) {
                    putPersistedId(persistedIds, pointer + "/resources/" + resourceIndex, persistedResource.getId());
                }
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
                        throw new IllegalStateException(
                                "Validated reference disappeared: spell key '" + spellDto.spellKey() + "' in sheet");
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
