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
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                            HandoutRepository handoutRepo) {
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
        var statBlocks = statBlockRepository.findByCampaignIdOrderByNameAsc(id).stream()
                .map(CampaignExportDto.StatBlockExportDto::from).toList();
        var maps = gameMapService.findByCampaignId(id).stream()
                .map(m -> CampaignExportDto.MapExportDto.from(m, gameMapService.getDocument(m.getId())))
                .toList();

        var encounters = encounterRepo.findByCampaignIdOrderByNameAsc(id).stream()
                .map(enc -> {
                    var combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(enc.getId()).stream()
                            .map(c -> CampaignExportDto.CombatantExportDto.from(c, java.util.Map.of()))
                            .toList();
                    return CampaignExportDto.EncounterExportDto.from(enc, combatants);
                })
                .toList();

        var handouts = handoutRepo.findByCampaignIdOrderByTitleAsc(id).stream()
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

        var noteDtos = noteRepository.findByCampaignIdOrderByCreatedAtDesc(id).stream()
                .map(CampaignExportDto.NoteExportDto::from).toList();
        var quickNoteDtos = quickNoteRepository.findByCampaignIdOrderByCreatedAtDesc(id).stream()
                .map(qn -> CampaignExportDto.QuickNoteExportDto.from(qn, java.util.Map.of()))
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
                        sbDto.senses(), sbDto.languages());
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
            }
        }

        if (dto.maps() != null) {
            for (var mapDto : dto.maps()) {
                var grid = mapDto.grid();
                var map = gameMapService.create(saved.getId(), mapDto.name(),
                        grid != null ? grid.w() : 30,
                        grid != null ? grid.h() : 20,
                        grid != null ? grid.cellPx() : 48);
                if (mapDto.document() != null) {
                    gameMapService.updateDocument(map.getId(),
                            objectMapper.writeValueAsString(mapDto.document()),
                            map.getVersion());
                }
            }
        }

        if (dto.notes() != null) {
            for (var noteDto : dto.notes()) {
                noteService.create(saved.getId(),
                        NoteType.valueOf(noteDto.type()),
                        noteDto.title(),
                        noteDto.body(),
                        noteDto.tags());
            }
        }

        // TODO: import quicknotes once targetId mappings are available

        if (dto.assignments() != null) {
            for (var aDto : dto.assignments()) {
                ItemAssignment ia = new ItemAssignment();
                ia.setCampaign(saved);
                if (aDto.holderName() != null) {
                    partyMemberRepository.findByCampaignIdOrderByCharacterNameAsc(saved.getId())
                        .stream().filter(pm -> pm.getCharacterName().equals(aDto.holderName()))
                        .findFirst().ifPresent(ia::setPartyMember);
                }
                if (aDto.magicItemKey() != null) {
                    MagicItem mi = magicItemRepo.findAll().stream()
                        .filter(m -> aDto.magicItemKey().equals(m.getSourceKey())).findFirst().orElse(null);
                    if (mi != null) ia.setMagicItem(mi);
                }
                if (aDto.equipmentItemKey() != null) {
                    EquipmentItem ei = equipmentItemRepo.findAll().stream()
                        .filter(e -> aDto.equipmentItemKey().equals(e.getSourceKey())).findFirst().orElse(null);
                    if (ei != null) ia.setEquipmentItem(ei);
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
                        .toList()
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
            sheet.setSpellSlotsUsed("{}");
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
