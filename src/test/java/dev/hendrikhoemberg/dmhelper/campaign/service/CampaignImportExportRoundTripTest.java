package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneTransitionService;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignCatalogResolver;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignSchemaValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignSemanticValidator;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignValidationResult;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.MapDocumentDto;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.WikiLinkParser;
import dev.hendrikhoemberg.dmhelper.session.service.SessionActivityRecorder;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatCardAssembler;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({CampaignService.class, PartyMemberService.class, StatBlockService.class, GameMapService.class,
         CustomContentSupport.class, LibraryReferenceCleaner.class,
         dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class,
         NoteService.class, WikiLinkParser.class, SceneRefCleaner.class, AdventureService.class,
         SceneTransitionService.class,
         HandoutService.class,
         CampaignImportValidator.class, CampaignSchemaValidator.class, CampaignSemanticValidator.class,
         CampaignCatalogResolver.class,
         CampaignImportExportRoundTripTest.TestObjectMapperConfig.class,
         dev.hendrikhoemberg.dmhelper.common.service.ContentDestinationRegistry.class})
class CampaignImportExportRoundTripTest {

    @TestConfiguration
    static class TestObjectMapperConfig {
        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder()
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
        }
    }

    @Autowired private CampaignService campaignService;
    @Autowired private PartyMemberService partyMemberService;
    @Autowired private StatBlockService statBlockService;
    @Autowired private GameMapService gameMapService;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private ItemAssignmentRepository assignmentRepo;
    @Autowired private LedgerEntryRepository ledgerEntryRepo;
    @Autowired private TimelineEventRepository timelineEventRepo;
    @Autowired private EncounterRepository encounterRepo;
    @Autowired private CombatantRepository combatantRepo;
    @Autowired private TokenRepository tokenRepo;
    @Autowired private NoteRepository noteRepository;
    @Autowired private QuickNoteRepository quickNoteRepository;
    @Autowired private AdventureService adventureService;
    @MockitoBean private SessionActivityRecorder sessionActivity;
    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;
    @MockitoBean private ThreatCardAssembler threatCardAssembler;
    @Autowired private AdventureRepository adventureRepo;
    @Autowired private ChapterRepository chapterRepo;
    @Autowired private SceneRepository sceneRepo;
    @Autowired private HandoutRepository handoutRepo;
    @Autowired private HandoutService handoutService;
    @Autowired private CampaignRepository campaignRepo;
    @Autowired private SpeciesRepository speciesRepo;
    @Autowired private BackgroundRepository backgroundRepo;
    @Autowired private CharacterClassRepository classRepo;
    @Autowired private FeatRepository featRepo;
    @Autowired private SpellRepository spellRepo;
    @Autowired private MagicItemRepository magicItemRepo;
    @Autowired private EquipmentItemRepository equipmentItemRepo;
    @Autowired private CampaignSchemaValidator schemaValidator;

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @BeforeEach
    void seedCatalog() {
        Species human = new Species();
        human.setSource(ContentSource.SRD);
        human.setSourceKey("srd-2024_human");
        human.setName("Human");
        speciesRepo.save(human);

        Background criminal = new Background();
        criminal.setSource(ContentSource.SRD);
        criminal.setSourceKey("srd-2024_criminal");
        criminal.setName("Criminal");
        backgroundRepo.save(criminal);

        CharacterClass rogue = new CharacterClass();
        rogue.setSource(ContentSource.SRD);
        rogue.setSourceKey("srd-2024_rogue");
        rogue.setName("Rogue");
        classRepo.save(rogue);

        Feat alert = new Feat();
        alert.setSource(ContentSource.SRD);
        alert.setSourceKey("srd-2024_alert");
        alert.setName("Alert");
        featRepo.save(alert);

        Spell cureWounds = new Spell();
        cureWounds.setSource(ContentSource.SRD);
        cureWounds.setSourceKey("cure-wounds");
        cureWounds.setName("Cure Wounds");
        cureWounds.setLevel(1);
        spellRepo.save(cureWounds);

        MagicItem bagOfHolding = new MagicItem();
        bagOfHolding.setSource(ContentSource.SRD);
        bagOfHolding.setSourceKey("srd-2024_bag-of-holding");
        bagOfHolding.setName("Bag of Holding");
        magicItemRepo.save(bagOfHolding);
    }

    @Test
    void roundTripPreservesPartyActiveAndStatblockSourceKey() {
        Campaign c = campaignService.create("Round Trip", "full graph");

        partyMemberService.create(c.getId(), "Thia", "Anna", "Rogue 5",
                16, 38, 4, 30, 17, 12, 11, "darkvision");
        PartyMember retired = partyMemberService.create(c.getId(), "Borin", "Ben", "Fighter 5",
                18, 45, 1, 30, 12, 10, 10, "retired PC");
        partyMemberService.setActive(retired.getId(), false);

        StatBlock sb = statBlockService.createCustom(c.getId(), "Amber Knight", "5", "Humanoid",
                18, "75 (10d8 + 30)", "30 ft.",
                16, 12, 16, 10, 12, 14,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "passive Perception 12", "Common",
                null, null);
        sb.setSourceKey("amber-knight");
        statBlockRepository.save(sb);

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<PartyMember> members = partyMemberService.findByCampaignId(imported.getId());
        assertThat(members).hasSize(2);
        PartyMember reBorin = members.stream()
                .filter(m -> m.getCharacterName().equals("Borin")).findFirst().orElseThrow();
        assertThat(reBorin.isActive()).isFalse();

        PartyMember reThia = members.stream()
                .filter(m -> m.getCharacterName().equals("Thia")).findFirst().orElseThrow();
        assertThat(reThia.isActive()).isTrue();

        List<StatBlock> blocks = statBlockService.findByCampaignId(imported.getId());
        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0).getSourceKey()).isEqualTo("amber-knight");
        assertThat(blocks.get(0).getName()).isEqualTo("Amber Knight");
    }

    @Test
    void roundTripPreservesMapsAndDocuments() {
        Campaign c = campaignService.create("Map Trip", "maps");
        GameMap map = gameMapService.create(c.getId(), "Tavern", 30, 20, 48);
        String doc = """
                {"schemaVersion":1,
                 "grid":{"width":30,"height":20,"cellSizePx":48,"gridType":"square"},
                 "layers":[{"id":"terrain","name":"Terrain","type":"TERRAIN","visible":true,"locked":false,
                            "cells":[{"col":1,"row":1,"terrain":"wall"}],"shapes":[]}],
                 "primitives":[{"type":"ROOM","startCol":2,"startRow":2,"endCol":8,"endRow":6}],
                 "customTerrain":[{"key":"moss","name":"Moss","fill":"#2a6e3a","walkable":true}]}""";
        gameMapService.updateDocument(map.getId(), doc, map.getVersion());

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<GameMap> maps = gameMapService.findByCampaignId(imported.getId());
        assertThat(maps).hasSize(1);
        assertThat(maps.get(0).getName()).isEqualTo("Tavern");
        MapDocumentDto reDoc = gameMapService.getDocument(maps.get(0).getId());
        assertThat(reDoc.layers().get(0).cells()).hasSize(1);
        assertThat(reDoc.layers().get(0).cells().get(0).terrain()).isEqualTo("wall");
        assertThat(reDoc.primitives()).hasSize(1);
        assertThat(reDoc.customTerrain()).hasSize(1);
    }

    @Test
    void roundTripPreservesTreasuryAndCalendar() {
        Campaign c = campaignService.create("Full Trip", "all the things");

        PartyMember pm = partyMemberService.create(c.getId(), "Thia", "Anna", "Rogue 5",
                16, 38, 4, 30, 17, 12, 11, null);

        ItemAssignment ia = new ItemAssignment();
        ia.setCampaign(c);
        ia.setPartyMember(pm);
        ia.setCustomText("Dagger +1");
        ia.setQuantity(1);
        ia.setAttuned(true);
        assignmentRepo.save(ia);

        LedgerEntry le = new LedgerEntry();
        le.setCampaign(c);
        le.setKind(LedgerEntry.Kind.GOLD);
        le.setDirection(LedgerEntry.Direction.GAIN);
        le.setAmount(new BigDecimal("500"));
        le.setCurrency("GP");
        le.setHolder("Party Stash");
        le.setNote("Dragon hoard");
        ledgerEntryRepo.save(le);

        TimelineEvent te = new TimelineEvent();
        te.setCampaign(c);
        te.setInGameYear(1492);
        te.setInGameMonth(5);
        te.setInGameDay(1);
        te.setTitle("The Eclipse");
        te.setBody("A dark omen");
        timelineEventRepo.save(te);

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<ItemAssignment> importedAssignments = assignmentRepo
                .findByCampaignIdOrderByPartyMemberAsc(imported.getId());
        assertThat(importedAssignments).hasSize(1);
        assertThat(importedAssignments.get(0).getCustomText()).isEqualTo("Dagger +1");
        assertThat(importedAssignments.get(0).isAttuned()).isTrue();

        List<LedgerEntry> importedLedger = ledgerEntryRepo
                .findByCampaignIdOrderByTimestampDesc(imported.getId());
        assertThat(importedLedger).hasSize(1);
        assertThat(importedLedger.get(0).getAmount()).isEqualByComparingTo("500");
        assertThat(importedLedger.get(0).getHolder()).isEqualTo("Party Stash");

        List<TimelineEvent> importedTimeline = timelineEventRepo
                .findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(imported.getId());
        assertThat(importedTimeline).hasSize(1);
        assertThat(importedTimeline.get(0).getTitle()).isEqualTo("The Eclipse");
    }

    @Test
    void roundTripPreservesEncountersWithCombatants() {
        Campaign c = campaignService.create("Encounter Trip", "encounters");
        GameMap map = gameMapService.create(c.getId(), "Battlefield", 30, 20, 48);

        PartyMember pm = partyMemberService.create(c.getId(), "Thia", "Anna", "Rogue 5",
                16, 38, 4, 30, 17, 12, 11, null);

        StatBlock sb = statBlockService.createCustom(c.getId(), "Goblin Boss", "1", "Humanoid",
                17, "21 (6d6)", "30 ft.",
                10, 14, 10, 10, 8, 8,
                null, null, null, null, null, null,
                null, null, null, null, null,
                "darkvision 60 ft.", "Common, Goblin",
                null, null);
        sb.setSourceKey("goblin-boss");
        statBlockRepository.save(sb);

        Encounter encounter = new Encounter();
        encounter.setCampaign(c);
        encounter.setName("Goblin Ambush");
        encounter.setStatus(Encounter.Status.ACTIVE);
        encounter.setRound(2);
        encounter.setActiveTurnIndex(0);
        encounter.setLairActionName("Falling Rocks");
        encounter.setLairActionDescription("Rocks fall, everyone dies");
        encounter = encounterRepo.save(encounter);

        Combatant pcCombatant = new Combatant();
        pcCombatant.setEncounter(encounter);
        pcCombatant.setName("Thia");
        pcCombatant.setPartyMember(pm);
        pcCombatant.setInitiative(18);
        pcCombatant.setSortOrder(0);
        pcCombatant.setMaxHp(38);
        pcCombatant.setCurrentHp(30);
        pcCombatant.setKind("player");
        pcCombatant.setConditionsJson("[blinded]");
        combatantRepo.save(pcCombatant);

        Combatant npcCombatant = new Combatant();
        npcCombatant.setEncounter(encounter);
        npcCombatant.setName("Goblin Boss");
        npcCombatant.setStatBlock(sb);
        npcCombatant.setInitiative(12);
        npcCombatant.setSortOrder(1);
        npcCombatant.setMaxHp(21);
        npcCombatant.setCurrentHp(10);
        npcCombatant.setKind("creature");
        npcCombatant.setHidden(true);
        combatantRepo.save(npcCombatant);

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<Encounter> encounters = encounterRepo.findByCampaignIdOrderByNameAsc(imported.getId());
        assertThat(encounters).hasSize(1);
        Encounter reEnc = encounters.get(0);
        assertThat(reEnc.getName()).isEqualTo("Goblin Ambush");
        assertThat(reEnc.getStatus()).isEqualTo(Encounter.Status.ACTIVE);
        assertThat(reEnc.getRound()).isEqualTo(2);
        assertThat(reEnc.getLairActionName()).isEqualTo("Falling Rocks");

        List<Combatant> combatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(reEnc.getId());
        assertThat(combatants).hasSize(2);
        Combatant rePC = combatants.get(0);
        assertThat(rePC.getName()).isEqualTo("Thia");
        assertThat(rePC.getInitiative()).isEqualTo(18);
        assertThat(rePC.getCurrentHp()).isEqualTo(30);
        assertThat(rePC.getConditionsJson()).contains("blinded");

        Combatant reNPC = combatants.get(1);
        assertThat(reNPC.getName()).isEqualTo("Goblin Boss");
        assertThat(reNPC.isHidden()).isTrue();
        assertThat(reNPC.getStatBlock().getSourceKey()).isEqualTo("goblin-boss");
    }

    @Test
    void roundTripPreservesCombatantTokenReference() {
        Campaign c = campaignService.create("Token Trip", "tokens");
        GameMap map = gameMapService.create(c.getId(), "Dungeon", 30, 20, 48);

        Token token = new Token();
        token.setMap(map);
        token.setName("Goblin Token");
        token.setKind("creature");
        token.setColor("#ff0000");
        token.setPositionX(5);
        token.setPositionY(3);
        token.setSizeCols(1);
        token.setSizeRows(1);
        token = tokenRepo.save(token);

        Encounter encounter = new Encounter();
        encounter.setCampaign(c);
        encounter.setMap(map);
        encounter.setName("Corridor Fight");
        encounter.setStatus(Encounter.Status.ACTIVE);
        encounter = encounterRepo.save(encounter);

        Combatant combatant = new Combatant();
        combatant.setEncounter(encounter);
        combatant.setName("Goblin");
        combatant.setToken(token);
        combatant.setInitiative(10);
        combatant.setSortOrder(0);
        combatant.setMaxHp(10);
        combatant.setCurrentHp(10);
        combatant.setKind("creature");
        combatantRepo.save(combatant);

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<Encounter> reEncounters = encounterRepo.findByCampaignIdOrderByNameAsc(imported.getId());
        assertThat(reEncounters).hasSize(1);
        List<Combatant> reCombatants = combatantRepo.findByEncounterIdOrderBySortOrderAsc(reEncounters.get(0).getId());
        assertThat(reCombatants).hasSize(1);
        Combatant reCombatant = reCombatants.get(0);
        assertThat(reCombatant.getToken()).isNotNull();
        assertThat(reCombatant.getToken().getName()).isEqualTo("Goblin Token");
        assertThat(reCombatant.getToken().getPositionX()).isEqualTo(5);
        assertThat(reCombatant.getToken().getPositionY()).isEqualTo(3);
        assertThat(reCombatant.getToken().getColor()).isEqualTo("#ff0000");
    }

    @Test
    void exportedJsonContainsAdventuresAndSceneFields() throws Exception {
        Campaign c = campaignService.create("JSON Check", "adventures in JSON");

        StatBlock sb = statBlockService.createCustom(c.getId(), "Test Monster", "2", "Monstrosity",
                15, "30 (5d8+5)", "30 ft.",
                12, 12, 12, 10, 10, 10,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, "Common",
                null, null);
        sb.setSourceKey("test-monster");
        statBlockRepository.save(sb);

        Handout handout = handoutService.create(c.getId(), "Handout A", "",
                new MockMultipartFile("file", "handout_a.jpg", "image/jpeg", "handout-data".getBytes()));

        GameMap map = gameMapService.create(c.getId(), "Test Map", 20, 15, 48);

        Adventure adv = adventureService.createAdventure(c.getId(), "Main Quest", "The main quest", null);
        Chapter ch = adventureService.createChapter(adv.getId(), "Ch 1", "Begin");
        Scene scene = adventureService.createScene(ch.getId(), "Start", "S-01", "You begin here.");
        adventureService.linkMap(scene.getId(), map.getId(), 100, 200);
        adventureService.addStatBlock(scene.getId(), sb.getId());
        adventureService.addHandout(scene.getId(), handout.getId());

        String json = campaignService.exportToJson(c.getId());

        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(json);

        JsonNode adventures = root.get("adventures");
        assertThat(adventures).isNotNull();
        assertThat(adventures.isArray()).isTrue();
        assertThat(adventures).hasSize(1);

        JsonNode advNode = adventures.get(0);
        assertThat(advNode.get("name").asText()).isEqualTo("Main Quest");
        assertThat(advNode.has("chapters")).isTrue();

        JsonNode chapters = advNode.get("chapters");
        assertThat(chapters).hasSize(1);
        JsonNode chNode = chapters.get(0);
        assertThat(chNode.get("title").asText()).isEqualTo("Ch 1");

        JsonNode scenes = chNode.get("scenes");
        assertThat(scenes).hasSize(1);
        JsonNode scNode = scenes.get(0);
        assertThat(scNode.get("title").asText()).isEqualTo("Start");
        assertThat(scNode.get("sceneKey").asText()).isEqualTo("S-01");
        assertThat(scNode.get("body").asText()).isEqualTo("You begin here.");
        assertThat(scNode.get("map").asText()).isEqualTo("Test Map");
        assertThat(scNode.has("pin")).isTrue();
        assertThat(scNode.get("pin").get("x").asInt()).isEqualTo(100);
        assertThat(scNode.get("pin").get("y").asInt()).isEqualTo(200);
        assertThat(scNode.get("statblocks")).isNotNull();
        assertThat(scNode.get("statblocks").get(0).asText()).isEqualTo("test-monster");
        assertThat(scNode.get("handouts")).isNotNull();
        assertThat(scNode.get("handouts").get(0).asText()).isEqualTo("Handout A");
    }

    @Test
    void roundTripPreservesDmOnlyNoteFlag() {
        Campaign c = campaignService.create("Note Trip", "dmOnly");
        noteRepository.save(createNote(c, "DM Secret", NoteType.LOCATION, "hidden body", "secret", true));
        noteRepository.save(createNote(c, "Public Note", NoteType.QUEST, "visible body", "public", false));

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<Note> notes = noteRepository.findByCampaignIdOrderByCreatedAtDesc(imported.getId());
        assertThat(notes).hasSize(2);
        Note dmNote = notes.stream().filter(n -> n.getTitle().equals("DM Secret")).findFirst().orElseThrow();
        assertThat(dmNote.isDmOnly()).isTrue();
        assertThat(dmNote.getBody()).isEqualTo("hidden body");
        Note publicNote = notes.stream().filter(n -> n.getTitle().equals("Public Note")).findFirst().orElseThrow();
        assertThat(publicNote.isDmOnly()).isFalse();
    }

    private Note createNote(Campaign campaign, String title, NoteType type, String body, String tags, boolean dmOnly) {
        Note note = new Note();
        note.setCampaign(campaign);
        note.setTitle(title);
        note.setType(type);
        note.setBody(body);
        note.setTags(tags);
        note.setDmOnly(dmOnly);
        return noteRepository.save(note);
    }

    @Test
    void roundTripPreservesQuickNoteReferences() {
        Campaign c = campaignService.create("QuickNote Trip", "quicknotes");
        GameMap map = gameMapService.create(c.getId(), "Dungeon", 30, 20, 48);
        Note note = createNote(c, "Secret Room", NoteType.LOCATION, "hidden passage", "", false);
        StatBlock sb = statBlockService.createCustom(c.getId(), "Goblin", "1/4", "Humanoid",
                15, "7 (2d6)", "30 ft.",
                8, 14, 10, 10, 8, 8,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, "Common",
                null, null);
        sb.setSourceKey("goblin-ref");
        statBlockRepository.save(sb);

        QuickNote qnMap = new QuickNote();
        qnMap.setCampaign(c);
        qnMap.setTargetType("MAP");
        qnMap.setTargetId(map.getId());
        qnMap.setBody("Map note");
        quickNoteRepository.save(qnMap);

        QuickNote qnNote = new QuickNote();
        qnNote.setCampaign(c);
        qnNote.setTargetType("NOTE");
        qnNote.setTargetId(note.getId());
        qnNote.setBody("Note note");
        quickNoteRepository.save(qnNote);

        QuickNote qnStatblock = new QuickNote();
        qnStatblock.setCampaign(c);
        qnStatblock.setTargetType("STATBLOCK");
        qnStatblock.setTargetId(sb.getId());
        qnStatblock.setBody("SB note");
        quickNoteRepository.save(qnStatblock);

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        List<QuickNote> quicknotes = quickNoteRepository.findByCampaignIdOrderByCreatedAtDesc(imported.getId());
        assertThat(quicknotes).hasSize(3);

        QuickNote reMapQn = quicknotes.stream().filter(q -> q.getBody().equals("Map note")).findFirst().orElseThrow();
        assertThat(reMapQn.getTargetType()).isEqualTo("MAP");
        assertThat(reMapQn.getTargetId()).isNotNull();

        QuickNote reNoteQn = quicknotes.stream().filter(q -> q.getBody().equals("Note note")).findFirst().orElseThrow();
        assertThat(reNoteQn.getTargetType()).isEqualTo("NOTE");
        assertThat(reNoteQn.getTargetId()).isNotNull();

        QuickNote reSbQn = quicknotes.stream().filter(q -> q.getBody().equals("SB note")).findFirst().orElseThrow();
        assertThat(reSbQn.getTargetType()).isEqualTo("STATBLOCK");
        assertThat(reSbQn.getTargetId()).isNotNull();
    }

    @Test
    void roundTripPreservesAdventuresAndEncounterMap() throws Exception {
        Campaign c = campaignService.create("Adventure Trip", "adventures + encounter map");

        StatBlock sb = statBlockService.createCustom(c.getId(), "Goblin Archer", "1/4", "Humanoid",
                15, "7 (2d6)", "30 ft.",
                8, 14, 10, 10, 8, 8,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, "Common",
                null, null);
        sb.setSourceKey("goblin-archer");
        statBlockRepository.save(sb);

        Handout handout = handoutService.create(c.getId(), "Dungeon Map Handout", "map",
                new MockMultipartFile("file", "dungeon.jpg", "image/jpeg", "map-data".getBytes()));

        GameMap map = gameMapService.create(c.getId(), "Dungeon Map", 30, 20, 48);

        Encounter encounter = new Encounter();
        encounter.setCampaign(c);
        encounter.setName("Goblin Fight");
        encounter.setEncounterKey("goblin-fight-key");
        encounter.setMap(map);
        encounter.setStatus(Encounter.Status.PLANNED);
        encounter = encounterRepo.save(encounter);

        Adventure adv = adventureService.createAdventure(c.getId(), "Test Adventure", "A test description", null);
        Chapter ch = adventureService.createChapter(adv.getId(), "Chapter 1", "Intro text");
        Scene scene = adventureService.createScene(ch.getId(), "First Scene", "scene-1", "Scene body");
        adventureService.linkMap(scene.getId(), map.getId(), 5, 10);
        adventureService.linkEncounter(scene.getId(), encounter.getId());
        adventureService.addStatBlock(scene.getId(), sb.getId());
        adventureService.addHandout(scene.getId(), handout.getId());
        adventureService.setStatus(scene.getId(), SceneStatus.VISITED);

        String json = campaignService.exportToJson(c.getId());
        Campaign imported = campaignService.importFromJson(json);

        var reAdventures = adventureRepo.findByCampaignIdOrderBySortOrderAsc(imported.getId());
        assertThat(reAdventures).hasSize(1);
        assertThat(reAdventures.get(0).getName()).isEqualTo("Test Adventure");

        var reChapters = chapterRepo.findByAdventureIdOrderBySortOrderAsc(reAdventures.get(0).getId());
        assertThat(reChapters).hasSize(1);
        assertThat(reChapters.get(0).getTitle()).isEqualTo("Chapter 1");

        var reScenes = sceneRepo.findByChapterIdOrderBySortOrderAsc(reChapters.get(0).getId());
        assertThat(reScenes).hasSize(1);
        Scene reScene = reScenes.get(0);
        assertThat(reScene.getTitle()).isEqualTo("First Scene");
        assertThat(reScene.getSceneKey()).isEqualTo("scene-1");
        assertThat(reScene.getBody()).isEqualTo("Scene body");
        assertThat(reScene.getStatus()).isEqualTo(SceneStatus.VISITED);
        assertThat(reScene.getMap()).isNotNull();
        assertThat(reScene.getMap().getName()).isEqualTo("Dungeon Map");
        assertThat(reScene.getPinX()).isEqualTo(5);
        assertThat(reScene.getPinY()).isEqualTo(10);
        assertThat(reScene.getEncounter()).isNotNull();
        assertThat(reScene.getEncounter().getName()).isEqualTo("Goblin Fight");
        assertThat(reScene.getStatBlocks()).hasSize(1);
        assertThat(reScene.getStatBlocks().get(0).getSourceKey()).isEqualTo("goblin-archer");
        assertThat(reScene.getHandouts()).hasSize(1);
        assertThat(reScene.getHandouts().get(0).getTitle()).isEqualTo("Dungeon Map Handout");

        var reEncounters = encounterRepo.findByCampaignIdOrderByNameAsc(imported.getId());
        assertThat(reEncounters).hasSize(1);
        Encounter reEncounter = reEncounters.get(0);
        assertThat(reEncounter.getMap()).isNotNull();
        assertThat(reEncounter.getMap().getName()).isEqualTo("Dungeon Map");
    }

    @Test
    void flagshipFixturePipeline() throws Exception {
        String source = resource("campaigns/v1/feature-complete.dmcampaign.json");

        CampaignValidationResult firstDryRun = campaignService.validateImport(source);
        assertThat(firstDryRun.valid()).as(firstDryRun.problems().toString()).isTrue();
        assertThat(firstDryRun.problems()).isEmpty();

        Campaign firstImport = campaignService.importFromJson(source);
        String exported = campaignService.exportToJson(firstImport.getId());
        assertThat(schemaValidator.validate(exported)).isEmpty();

        CampaignValidationResult secondDryRun = campaignService.validateImport(exported);
        assertThat(secondDryRun.valid()).isTrue();

        Campaign secondImport = campaignService.importFromJson(exported);
        assertCurrentV1SemanticsEqual(firstImport.getId(), secondImport.getId());
    }

    @Test
    void globalStatBlockReferencesValidateImportAndExportConsistently() throws Exception {
        StatBlock global = statBlockService.createCustom(null, "Global Goblin", "1/4", "Humanoid",
                15, "7 (2d6)", "30 ft.",
                8, 14, 10, 10, 8, 8,
                null, null, null, null, null, null,
                null, null, null, null, null,
                null, "Common",
                "srd-global-goblin", 50);

        JsonNode tree = objectMapper.readTree(resource("campaigns/v1/feature-complete.dmcampaign.json"));
        ((ObjectNode) tree.get("maps").get(0).get("tokens").get(0))
                .put("statBlockKey", global.getSourceKey());
        ((tools.jackson.databind.node.ArrayNode) tree.get("adventures").get(0).get("chapters").get(0)
                .get("scenes").get(0).get("statblocks")).set(0, global.getSourceKey());
        ((ObjectNode) tree.get("quicknotes").get(3)).put("targetRef", global.getSourceKey());
        String json = objectMapper.writeValueAsString(tree);

        CampaignValidationResult dryRun = campaignService.validateImport(json);
        assertThat(dryRun.valid()).as(dryRun.problems().toString()).isTrue();
        Campaign imported = campaignService.importFromJson(json);

        Scene importedScene = sceneRepo.findByChapterAdventureCampaignId(imported.getId()).get(0);
        assertThat(importedScene.getStatBlocks()).extracting(StatBlock::getId).containsExactly(global.getId());
        assertThat(quickNoteRepository.findByCampaignIdOrderByCreatedAtDesc(imported.getId()))
                .filteredOn(note -> "STATBLOCK".equals(note.getTargetType()))
                .extracting(QuickNote::getTargetId)
                .containsExactly(global.getId());

        String exported = campaignService.exportToJson(imported.getId());
        assertThat(campaignService.validateImport(exported).valid()).isTrue();
        assertThat(objectMapper.readTree(exported).get("quicknotes"))
                .anySatisfy(note -> {
                    assertThat(note.get("targetType").asText()).isEqualTo("STATBLOCK");
                    assertThat(note.get("targetRef").asText()).isEqualTo(global.getSourceKey());
                });
    }

    @Test
    void unknownPropertyFixtureIsRejectedByBothPaths() throws Exception {
        String source = resource("campaigns/v1/invalid-unknown-property.dmcampaign.json");
        long campaignCount = campaignRepo.count();

        CampaignValidationResult dryRun = campaignService.validateImport(source);
        assertThat(dryRun.valid()).isFalse();
        assertThat(dryRun.problems())
                .anyMatch(p -> p.code().equals("SCHEMA_ADDITIONAL_PROPERTIES"));

        assertThatThrownBy(() -> campaignService.importFromJson(source))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(campaignRepo.count()).isEqualTo(campaignCount);
    }

    @Test
    void unresolvedReferenceIsRejectedByBothPaths() {
        String json = """
                {"formatVersion":1,"campaign":{"name":"Unresolved Ref"},"statBlocks":[],"adventures":[{"name":"Test","sortOrder":1,"chapters":[{"title":"Ch1","sortOrder":1,"scenes":[{"title":"S1","sortOrder":1,"statblocks":["nonexistent-key"]}]}]}]}
                """;
        long campaignCount = campaignRepo.count();

        CampaignValidationResult dryRun = campaignService.validateImport(json);
        assertThat(dryRun.valid()).isFalse();
        assertThat(dryRun.problems())
                .anyMatch(p -> p.code().equals("UNRESOLVED_REFERENCE"));

        assertThatThrownBy(() -> campaignService.importFromJson(json))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(campaignRepo.count()).isEqualTo(campaignCount);
    }

    @Test
    void dryRunRejectsUnknownKeysFromEveryTypedCatalog() throws Exception {
        assertUnresolvedCatalogKey(tree ->
                        ((ObjectNode) tree.get("party").get(0).get("sheet"))
                                .put("speciesKey", "missing-species"),
                "/party/0/sheet/speciesKey");
        assertUnresolvedCatalogKey(tree ->
                        ((ObjectNode) tree.get("party").get(0).get("sheet"))
                                .put("backgroundKey", "missing-background"),
                "/party/0/sheet/backgroundKey");
        assertUnresolvedCatalogKey(tree ->
                        ((ObjectNode) tree.get("party").get(0).get("sheet")
                                .get("classLevels").get(0)).put("classSourceKey", "missing-class"),
                "/party/0/sheet/classLevels/0/classSourceKey");
        assertUnresolvedCatalogKey(tree ->
                        ((tools.jackson.databind.node.ArrayNode) tree.get("party").get(0).get("sheet")
                                .get("featRefs")).set(0, "missing-feat"),
                "/party/0/sheet/featRefs/0");
        assertUnresolvedCatalogKey(tree ->
                        ((ObjectNode) tree.get("party").get(0).get("sheet")
                                .get("spells").get(0)).put("spellKey", "missing-spell"),
                "/party/0/sheet/spells/0/spellKey");
        assertUnresolvedCatalogKey(tree ->
                        ((ObjectNode) tree.get("assignments").get(0))
                                .put("magicItemKey", "missing-magic-item"),
                "/assignments/0/magicItemKey");
        assertUnresolvedCatalogKey(tree -> {
                    ObjectNode assignment = (ObjectNode) tree.get("assignments").get(0);
                    assignment.remove("magicItemKey");
                    assignment.put("equipmentItemKey", "missing-equipment-item");
                },
                "/assignments/0/equipmentItemKey");
    }

    @Test
    void dryRunRejectsAssignmentWithMoreThanOneItemSource() throws Exception {
        JsonNode tree = objectMapper.readTree(resource("campaigns/v1/feature-complete.dmcampaign.json"));
        ((ObjectNode) tree.get("assignments").get(0)).put("customText", "A second source");

        CampaignValidationResult result = campaignService.validateImport(objectMapper.writeValueAsString(tree));

        assertThat(result.valid()).isFalse();
        assertThat(result.problems()).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("INVALID_ASSIGNMENT_SOURCE");
            assertThat(problem.path()).isEqualTo("/assignments/0");
        });
    }

    @Test
    void minimalValidFixtureIsAcceptedByBothPaths() throws Exception {
        String source = resource("campaigns/v1/minimal.dmcampaign.json");
        long campaignCount = campaignRepo.count();

        CampaignValidationResult dryRun = campaignService.validateImport(source);
        assertThat(dryRun.valid()).isTrue();

        campaignService.importFromJson(source);
        assertThat(campaignRepo.count()).isEqualTo(campaignCount + 1);
    }

    private void assertCurrentV1SemanticsEqual(UUID id1, UUID id2) throws Exception {
        String json1 = campaignService.exportToJson(id1);
        String json2 = campaignService.exportToJson(id2);

        CampaignExportDto dto1 = objectMapper.readValue(json1, CampaignExportDto.class);
        CampaignExportDto dto2 = objectMapper.readValue(json2, CampaignExportDto.class);

        assertThat(normalizeDto(dto1)).isEqualTo(normalizeDto(dto2));
    }

    private void assertUnresolvedCatalogKey(Consumer<JsonNode> mutation, String expectedPath) throws Exception {
        JsonNode tree = objectMapper.readTree(resource("campaigns/v1/feature-complete.dmcampaign.json"));
        mutation.accept(tree);

        CampaignValidationResult result = campaignService.validateImport(objectMapper.writeValueAsString(tree));

        assertThat(result.valid()).as(expectedPath).isFalse();
        assertThat(result.problems()).as(expectedPath).anySatisfy(problem -> {
            assertThat(problem.code()).isEqualTo("UNRESOLVED_REFERENCE");
            assertThat(problem.path()).isEqualTo(expectedPath);
        });
    }

    private CampaignExportDto normalizeDto(CampaignExportDto dto) {
        return new CampaignExportDto(
                dto.formatVersion(),
                dto.campaign(),
                dto.party(),
                dto.statBlocks(),
                dto.handouts() != null ? dto.handouts().stream()
                        .map(h -> new CampaignExportDto.HandoutExportDto(
                                h.title(), h.tags(), null, h.contentType(), h.imageData()))
                        .toList() : null,
                dto.maps() != null ? dto.maps().stream()
                        .map(m -> new CampaignExportDto.MapExportDto(
                                null, m.name(), m.grid(), m.movementMode(), m.showGrid(), m.document(),
                                m.tokens() != null ? m.tokens().stream()
                                        .map(t -> new CampaignExportDto.MapExportDto.TokenExportDto(
                                                null, t.name(), t.kind(), t.color(),
                                                t.positionX(), t.positionY(), t.sizeCols(), t.sizeRows(),
                                                t.hidden(), t.statBlockKey(), t.partyMemberName(),
                                                t.currentHp(), t.maxHp(), t.dead(), t.notes()))
                                        .toList() : null))
                        .toList() : null,
                dto.encounters() != null ? dto.encounters().stream()
                        .map(e -> new CampaignExportDto.EncounterExportDto(
                                e.name(),
                                e.combatants() != null ? e.combatants().stream()
                                        .map(c -> new CampaignExportDto.CombatantExportDto(
                                                c.name(), c.initiative(), c.tieBreaker(), c.sortOrder(),
                                                c.maxHp(), c.currentHp(), c.tempHp(),
                                                c.kind(), c.groupId(), c.groupLeader(),
                                                null, c.statBlockKey(), c.partyMemberName(),
                                                c.defeated(), c.hidden(),
                                                c.conditionsJson(), c.concentratingOn(), c.concentrationCheckPending(),
                                                c.legendaryActionsUsed(), c.legendaryResistancesUsed(),
                                                c.legendaryActionsMax(), c.legendaryResistancesMax(),
                                                c.rechargedAbilities(), c.notes()))
                                        .toList() : null,
                                e.status(), e.round(), e.activeTurnIndex(), e.logSequence(),
                                e.lairActionName(), e.lairActionDescription(),
                                e.encounterKey(), e.map()))
                        .toList() : null,
                dto.notes(),
                dto.quicknotes() != null ? dto.quicknotes().stream()
                        .map(q -> new CampaignExportDto.QuickNoteExportDto(
                                q.targetType(),
                                "MAP".equals(q.targetType()) ? null : q.targetRef(),
                                q.body(), q.createdAt()))
                        .toList() : null,
                dto.assignments() != null ? dto.assignments().stream()
                        .map(a -> new CampaignExportDto.AssignmentExportDto(
                                null, a.holderName(), a.magicItemKey(), a.equipmentItemKey(),
                                a.customText(), a.quantity(), a.attuned()))
                        .toList() : null,
                dto.ledger() != null ? dto.ledger().stream()
                        .map(l -> new CampaignExportDto.LedgerExportDto(
                                null, l.timestamp(),
                                l.inGameYear(), l.inGameMonth(), l.inGameDay(),
                                l.kind(), l.direction(), l.amount(), l.currency(),
                                l.holder(), l.note(), null))
                        .toList() : null,
                dto.timeline() != null ? dto.timeline().stream()
                        .map(t -> new CampaignExportDto.TimelineExportDto(
                                null, t.inGameYear(), t.inGameMonth(), t.inGameDay(),
                                t.title(), t.body(), t.noteTitle()))
                        .toList() : null,
                dto.adventures()
        );
    }

    private static String resource(String path) throws Exception {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
