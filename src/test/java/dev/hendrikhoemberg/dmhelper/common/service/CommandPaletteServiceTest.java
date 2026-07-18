package dev.hendrikhoemberg.dmhelper.common.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheet;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.Hazard;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardExposureMode;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatResetMode;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatSeverity;
import dev.hendrikhoemberg.dmhelper.threat.data.Trap;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({CommandPaletteService.class, ContentDestinationRegistry.class})
class CommandPaletteServiceTest {

    @Autowired private CommandPaletteService commandPaletteService;
    @Autowired private NoteRepository noteRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private SpellRepository spellRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private AdventureRepository adventureRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private HandoutRepository handoutRepository;
    @Autowired private PartyMemberRepository partyMemberRepository;
    @Autowired private CharacterSheetRepository characterSheetRepository;
    @Autowired private RollableTableRepository rollableTableRepository;
    @Autowired private TrapRepository trapRepository;
    @Autowired private HazardRepository hazardRepository;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaign = campaignRepository.save(campaign);

        Note note = new Note();
        note.setCampaign(campaign);
        note.setTitle("The Goblin Cave");
        note.setType(NoteType.LOCATION);
        note.setBody("A dark cave filled with goblins.");
        noteRepository.save(note);

        StatBlock sb = new StatBlock();
        sb.setName("Goblin");
        sb.setSourceKey("goblin");
        sb.setSource(ContentSource.SRD);
        sb.setCr("1/4");
        sb.setType("Humanoid");
        sb.setAc(15);
        sb.setHp("7 (2d6)");
        sb.setSpeed("30 ft.");
        sb.setXp(50);
        statBlockRepository.save(sb);

        Spell spell = new Spell();
        spell.setSource(ContentSource.SRD);
        spell.setName("Fireball");
        spell.setSourceKey("fireball");
        spell.setLevel(3);
        spell.setSchool("Evocation");
        spellRepository.save(spell);

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Tavern Map");
        map.setGridWidth(30);
        map.setGridHeight(20);
        map.setCellSizePx(48);
        gameMapRepository.save(map);

        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setName("Tavern Brawl");
        encounter.setStatus(Encounter.Status.PLANNED);
        encounterRepository.save(encounter);

        Adventure adv = new Adventure();
        adv.setCampaign(campaign);
        adv.setName("Test Adventure");
        adventureRepository.save(adv);

        Chapter ch = new Chapter();
        ch.setAdventure(adv);
        ch.setTitle("Test Chapter");
        chapterRepository.save(ch);

        Scene scene = new Scene();
        scene.setChapter(ch);
        scene.setTitle("Throne Room");
        scene.setSceneKey("TR");
        sceneRepository.save(scene);
    }

    @Test
    void searchFindsNotesByTitle() {
        var results = commandPaletteService.search("Cave", campaign.getId());
        assertThat(results).anyMatch(r -> r.title().equals("The Goblin Cave") && r.type().equals("note"));
    }

    @Test
    void searchFindsStatblocksByName() {
        var results = commandPaletteService.search("Goblin", null);
        assertThat(results).anyMatch(r -> r.title().equals("Goblin") && r.type().equals("statblock"));
    }

    @Test
    void searchFindsSpellsByName() {
        var results = commandPaletteService.search("Fireball", null);
        assertThat(results).anyMatch(r -> r.title().equals("Fireball") && r.type().equals("spell"));
    }

    @Test
    void searchFindsMapsByName() {
        var results = commandPaletteService.search("Tavern", campaign.getId());
        assertThat(results).anyMatch(r -> r.title().equals("Tavern Map") && r.type().equals("map"));
    }

    @Test
    void searchFindsEncountersByName() {
        var results = commandPaletteService.search("Brawl", campaign.getId());
        assertThat(results).anyMatch(r -> r.title().equals("Tavern Brawl") && r.type().equals("encounter"));
    }

    @Test
    void searchWithNoCampaignOnlyReturnsGlobalContent() {
        var results = commandPaletteService.search("Goblin", null);
        assertThat(results).anyMatch(r -> r.type().equals("statblock"));
        assertThat(results).noneMatch(r -> r.type().equals("note"));
    }

    @Test
    void searchFindsScenesByTitle() {
        var results = commandPaletteService.search("Throne", campaign.getId());
        assertThat(results).anyMatch(r -> r.title().equals("Throne Room") && r.type().equals("scene"));
    }

    @Test
    void emptyQueryReturnsEmptyList() {
        var results = commandPaletteService.search("   ", campaign.getId());
        assertThat(results).isEmpty();
    }

    @Test
    void mapResultUsesPlayRoute() {
        var result = commandPaletteService.search("Tavern Map", campaign.getId()).stream()
                .filter(item -> item.type().equals("map"))
                .findFirst().orElseThrow();
        assertThat(result.url()).endsWith("/play");
        assertThat(result.url()).doesNotContain("/battle");
    }

    @Test
    void spellResultUsesDetailRoute() {
        var result = commandPaletteService.search("Fireball", null).stream()
                .filter(item -> item.type().equals("spell"))
                .findFirst().orElseThrow();
        assertThat(result.url()).startsWith("/library/spells/");
    }

    @Test
    void handoutResultUsesTheGalleryCardAnchor() {
        Handout handout = new Handout();
        handout.setCampaign(campaign);
        handout.setTitle("Royal Invitation");
        handout.setFileName("invitation.png");
        handout = handoutRepository.save(handout);

        var result = commandPaletteService.search("Royal Invitation", campaign.getId()).stream()
                .filter(item -> item.type().equals("handout"))
                .findFirst().orElseThrow();
        assertThat(result.url()).isEqualTo(
                "/campaigns/" + campaign.getId() + "/handouts#handout-" + handout.getId());
    }

    @Test
    void partyMemberUsesRosterUntilASheetExists() {
        PartyMember member = new PartyMember();
        member.setCampaign(campaign);
        member.setCharacterName("Arannis");
        member = partyMemberRepository.save(member);

        var rosterResult = commandPaletteService.search("Arannis", campaign.getId()).stream()
                .filter(item -> item.type().equals("party-member"))
                .findFirst().orElseThrow();
        assertThat(rosterResult.url()).isEqualTo(
                "/campaigns/" + campaign.getId() + "/party#pm-card-" + member.getId());

        CharacterSheet sheet = new CharacterSheet();
        sheet.setPartyMember(member);
        characterSheetRepository.save(sheet);

        var sheetResult = commandPaletteService.search("Arannis", campaign.getId()).stream()
                .filter(item -> item.type().equals("party-member"))
                .findFirst().orElseThrow();
        assertThat(sheetResult.url()).isEqualTo(
                "/campaigns/" + campaign.getId() + "/party/" + member.getId() + "/sheet");
    }

    @Test
    void resultsAreGloballyCapped() {
        for (int index = 0; index < 30; index++) {
            Note note = new Note();
            note.setCampaign(campaign);
            note.setTitle("Shared Result " + index);
            note.setType(NoteType.GENERIC);
            note.setBody("shared result body");
            noteRepository.save(note);
        }
        assertThat(commandPaletteService.search("shared result", campaign.getId())).hasSize(20);
    }

    @Test
    void exactCampaignTitleRanksAheadOfEqualGlobalTitle() {
        Note note = new Note();
        note.setCampaign(campaign);
        note.setTitle("Goblin");
        note.setType(NoteType.NPC);
        note.setBody("");
        noteRepository.save(note);

        var results = commandPaletteService.search("Goblin", campaign.getId());
        assertThat(results.getFirst().type()).isEqualTo("note");
    }

    @Test
    void noteBodyMatchesUseBodyRelevance() {
        Note note = new Note();
        note.setCampaign(campaign);
        note.setTitle("Zeta Chronicle");
        note.setType(NoteType.GENERIC);
        note.setBody("The hidden needle is behind the altar.");
        noteRepository.save(note);

        PartyMember member = new PartyMember();
        member.setCampaign(campaign);
        member.setCharacterName("Alpha Hero");
        member.setPlayerName("Hidden Needle");
        partyMemberRepository.save(member);

        var results = commandPaletteService.search("hidden needle", campaign.getId());

        assertThat(results).extracting(CommandPaletteService.SearchResultItem::type)
                .startsWith("note", "party-member");
    }

    @Test
    void searchFindsCampaignRollableTablesByTitle() {
        RollableTable table = new RollableTable();
        table.setCampaign(campaign);
        table.setName("Wild Magic Surge");
        table.setSource(ContentSource.CUSTOM);
        table.setAddressMode(TableAddressMode.WEIGHTED);
        table.setCategory(TableCategory.GENERIC);
        table.setSourceKey("wild-magic-surge");
        table.setDescription("A chaotic table of wild magic effects");
        rollableTableRepository.save(table);

        var results = commandPaletteService.search("Wild Magic", campaign.getId());
        assertThat(results).anyMatch(r -> r.title().equals("Wild Magic Surge") && r.type().equals("rollable-table"));
    }

    @Test
    void searchFindsGlobalRollableTablesByName() {
        RollableTable table = new RollableTable();
        table.setName("Global Loot Table");
        table.setSource(ContentSource.SRD);
        table.setAddressMode(TableAddressMode.WEIGHTED);
        table.setCategory(TableCategory.TREASURE);
        table.setSourceKey("global-loot");
        rollableTableRepository.save(table);

        var results = commandPaletteService.search("Global Loot", null);
        assertThat(results).anyMatch(r -> r.title().equals("Global Loot Table") && r.type().equals("rollable-table"));
    }

    @Test
    void campaignTableTitleRanksAheadOfGlobalTableTitle() {
        RollableTable campaignTable = new RollableTable();
        campaignTable.setCampaign(campaign);
        campaignTable.setName("Goblin");
        campaignTable.setSource(ContentSource.CUSTOM);
        campaignTable.setAddressMode(TableAddressMode.WEIGHTED);
        campaignTable.setCategory(TableCategory.RUMOR);
        campaignTable.setSourceKey("goblin-rumor");
        rollableTableRepository.save(campaignTable);

        var results = commandPaletteService.search("Goblin", campaign.getId());
        assertThat(results.getFirst().type()).isEqualTo("rollable-table");
    }

    @Test
    void rollableTableSearchIsCampaignIsolatedRankedAndDeduplicated() {
        Campaign otherCampaign = new Campaign();
        otherCampaign.setName("Other Campaign");
        otherCampaign = campaignRepository.save(otherCampaign);

        RollableTable campaignTable = table("Shared Oracle", "campaign-oracle", ContentSource.CUSTOM);
        campaignTable.setCampaign(campaign);
        campaignTable = rollableTableRepository.save(campaignTable);

        RollableTable globalTable = rollableTableRepository.save(
                table("Shared Oracle", "global-oracle", ContentSource.CUSTOM));

        RollableTable srdTable = rollableTableRepository.save(
                table("Shared Oracle", "srd-oracle", ContentSource.SRD));

        RollableTable foreignTable = table("Shared Oracle", "foreign-oracle", ContentSource.CUSTOM);
        foreignTable.setCampaign(otherCampaign);
        foreignTable = rollableTableRepository.save(foreignTable);

        var tableResults = commandPaletteService.search("Shared Oracle", campaign.getId()).stream()
                .filter(result -> result.type().equals("rollable-table"))
                .toList();

        assertThat(tableResults.getFirst().id()).isEqualTo(campaignTable.getId().toString());
        assertThat(tableResults).extracting(CommandPaletteService.SearchResultItem::id)
                .containsExactlyInAnyOrder(
                        campaignTable.getId().toString(),
                        globalTable.getId().toString(),
                        srdTable.getId().toString())
                .doesNotContain(foreignTable.getId().toString());
    }

    private RollableTable table(String name, String sourceKey, ContentSource source) {
        RollableTable table = new RollableTable();
        table.setName(name);
        table.setSource(source);
        table.setAddressMode(TableAddressMode.WEIGHTED);
        table.setCategory(TableCategory.GENERIC);
        table.setSourceKey(sourceKey);
        return table;
    }

    @Test
    void searchFindsCampaignTrapsByTitle() {
        Trap trap = newTrap("Spike Pit", "spike-pit", ContentSource.CUSTOM);
        trap.setCampaign(campaign);
        trapRepository.save(trap);

        var results = commandPaletteService.search("Spike", campaign.getId());
        assertThat(results).anyMatch(r -> r.title().equals("Spike Pit") && r.type().equals("trap"));
    }

    @Test
    void searchFindsGlobalHazardsByName() {
        Hazard hazard = newHazard("Lava Field", "lava-field", ContentSource.SRD);
        hazardRepository.save(hazard);

        var results = commandPaletteService.search("Lava", null);
        assertThat(results).anyMatch(r -> r.title().equals("Lava Field") && r.type().equals("hazard"));
    }

    @Test
    void campaignTrapTitleRanksAheadOfGlobalTrapTitle() {
        Trap campaignTrap = newTrap("Goblin", "goblin-trap", ContentSource.CUSTOM);
        campaignTrap.setCampaign(campaign);
        trapRepository.save(campaignTrap);

        var results = commandPaletteService.search("Goblin", campaign.getId()).stream()
                .filter(r -> r.type().equals("trap") || r.type().equals("statblock"))
                .toList();
        assertThat(results.getFirst().type()).isEqualTo("trap");
    }

    @Test
    void trapAndHazardSearchIsCampaignIsolatedRankedAndDeduplicated() {
        Campaign otherCampaign = new Campaign();
        otherCampaign.setName("Other Threat Campaign");
        otherCampaign = campaignRepository.save(otherCampaign);

        Trap campaignTrap = newTrap("Shared Threat", "campaign-trap", ContentSource.CUSTOM);
        campaignTrap.setCampaign(campaign);
        campaignTrap = trapRepository.save(campaignTrap);

        Trap globalTrap = trapRepository.save(newTrap("Shared Threat", "global-trap", ContentSource.CUSTOM));
        Trap srdTrap = trapRepository.save(newTrap("Shared Threat", "srd-trap", ContentSource.SRD));

        Trap foreignTrap = newTrap("Shared Threat", "foreign-trap", ContentSource.CUSTOM);
        foreignTrap.setCampaign(otherCampaign);
        foreignTrap = trapRepository.save(foreignTrap);

        Hazard campaignHazard = newHazard("Shared Threat", "campaign-hazard", ContentSource.CUSTOM);
        campaignHazard.setCampaign(campaign);
        campaignHazard = hazardRepository.save(campaignHazard);

        Hazard foreignHazard = newHazard("Shared Threat", "foreign-hazard", ContentSource.CUSTOM);
        foreignHazard.setCampaign(otherCampaign);
        foreignHazard = hazardRepository.save(foreignHazard);

        var trapResults = commandPaletteService.search("Shared Threat", campaign.getId()).stream()
                .filter(result -> result.type().equals("trap"))
                .toList();
        var hazardResults = commandPaletteService.search("Shared Threat", campaign.getId()).stream()
                .filter(result -> result.type().equals("hazard"))
                .toList();

        assertThat(trapResults.getFirst().id()).isEqualTo(campaignTrap.getId().toString());
        assertThat(trapResults).extracting(CommandPaletteService.SearchResultItem::id)
                .containsExactlyInAnyOrder(
                        campaignTrap.getId().toString(),
                        globalTrap.getId().toString(),
                        srdTrap.getId().toString())
                .doesNotContain(foreignTrap.getId().toString());
        assertThat(hazardResults).extracting(CommandPaletteService.SearchResultItem::id)
                .containsExactly(campaignHazard.getId().toString())
                .doesNotContain(foreignHazard.getId().toString());
    }

    private Trap newTrap(String name, String sourceKey, ContentSource source) {
        Trap trap = new Trap();
        trap.setName(name);
        trap.setSourceKey(sourceKey);
        trap.setSource(source);
        trap.setSeverity(ThreatSeverity.DANGEROUS);
        trap.setResetMode(ThreatResetMode.NONE);
        return trap;
    }

    private Hazard newHazard(String name, String sourceKey, ContentSource source) {
        Hazard hazard = new Hazard();
        hazard.setName(name);
        hazard.setSourceKey(sourceKey);
        hazard.setSource(source);
        hazard.setSeverity(ThreatSeverity.SETBACK);
        hazard.setExposureMode(HazardExposureMode.ON_ENTER);
        return hazard;
    }
}
