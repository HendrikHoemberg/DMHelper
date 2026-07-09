package dev.hendrikhoemberg.dmhelper.common.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(CommandPaletteService.class)
class CommandPaletteServiceTest {

    @Autowired private CommandPaletteService commandPaletteService;
    @Autowired private NoteRepository noteRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private SpellRepository spellRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private CampaignRepository campaignRepository;

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
        sb.setSource(StatBlock.Source.SRD);
        sb.setCr("1/4");
        sb.setType("Humanoid");
        sb.setAc(15);
        sb.setHp("7 (2d6)");
        sb.setSpeed("30 ft.");
        sb.setXp(50);
        statBlockRepository.save(sb);

        Spell spell = new Spell();
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
    void emptyQueryReturnsEmptyList() {
        var results = commandPaletteService.search("   ", campaign.getId());
        assertThat(results).isEmpty();
    }
}
