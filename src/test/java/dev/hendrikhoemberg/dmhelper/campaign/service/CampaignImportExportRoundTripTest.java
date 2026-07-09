package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
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
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNote;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.WikiLinkParser;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CampaignService.class, PartyMemberService.class, StatBlockService.class, GameMapService.class,
         NoteService.class, WikiLinkParser.class})
class CampaignImportExportRoundTripTest {

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

    @MockitoBean
    private HandoutService handoutService;

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
        pcCombatant.setKind("PC");
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
        npcCombatant.setKind("NPC");
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
        token.setKind("NPC");
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
        combatant.setKind("NPC");
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
}
