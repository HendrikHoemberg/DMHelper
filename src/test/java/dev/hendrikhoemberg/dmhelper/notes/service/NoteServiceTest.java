package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({NoteService.class, WikiLinkParser.class, StatBlockService.class, SceneRefCleaner.class, dev.hendrikhoemberg.dmhelper.common.service.ContentDestinationRegistry.class})
class NoteServiceTest {

    @Autowired private NoteRepository noteRepository;
    @Autowired private NoteLinkRepository noteLinkRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private GameMapRepository gameMapRepository;
    @Autowired private HandoutRepository handoutRepository;
    @Autowired private NoteService noteService;
    @Autowired private AdventureRepository adventureRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;

    private Campaign campaign;
    private StatBlock goblin;
    private GameMap dungeon;
    private Scene throneRoom;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaignRepository.save(campaign);

        goblin = new StatBlock();
        goblin.setName("Goblin");
        goblin.setSource(StatBlock.Source.SRD);
        goblin.setSourceKey("goblin");
        goblin.setCr("1/4");
        goblin.setType("humanoid");
        goblin.setHp("7 (2d6)");
        statBlockRepository.save(goblin);

        dungeon = new GameMap();
        dungeon.setCampaign(campaign);
        dungeon.setName("Dungeon Level 1");
        dungeon.setGridWidth(20);
        dungeon.setGridHeight(20);
        dungeon.setCellSizePx(48);
        gameMapRepository.save(dungeon);

        Adventure adv = new Adventure();
        adv.setCampaign(campaign);
        adv.setName("Test Adventure");
        adventureRepository.save(adv);

        Chapter ch = new Chapter();
        ch.setAdventure(adv);
        ch.setTitle("Test Chapter");
        chapterRepository.save(ch);

        throneRoom = new Scene();
        throneRoom.setChapter(ch);
        throneRoom.setTitle("Throne Room");
        throneRoom.setSceneKey("TR");
        sceneRepository.save(throneRoom);
    }

    @Test
    void createsNoteAndResolvesWikiLinks() {
        Note note = noteService.create(
            campaign.getId(),
            NoteType.LOCATION,
            "The Dungeon",
            "Guarded by [[statblock:Goblin]] and connects to [[Dungeon Level 1]].",
            "dungeon, level-1"
        );

        assertNotNull(note.getId());
        assertEquals("The Dungeon", note.getTitle());
        assertEquals(NoteType.LOCATION, note.getType());

        List<NoteLink> links = noteLinkRepository.findBySourceNoteId(note.getId());
        assertEquals(2, links.size());

        var statblockLink = links.stream()
            .filter(l -> "STATBLOCK".equals(l.getTargetType())).findFirst().orElseThrow();
        assertTrue(statblockLink.isResolved());
        assertEquals(goblin.getId(), statblockLink.getTargetId());

        var noteLink = links.stream()
            .filter(l -> "NOTE".equals(l.getTargetType()) && "Dungeon Level 1".equals(l.getDisplayText()))
            .findFirst().orElseThrow();
        assertFalse(noteLink.isResolved());
    }

    @Test
    void findsBacklinks() {
        Note target = noteService.create(campaign.getId(), NoteType.GENERIC, "Target", "I am the target.", "");
        Note source = noteService.create(campaign.getId(), NoteType.GENERIC, "Source", "Links to [[Target]].", "");

        List<Note> backlinks = noteService.findBacklinks(target.getId());
        assertEquals(1, backlinks.size());
        assertEquals("Source", backlinks.get(0).getTitle());
    }

    @Test
    void searchFindsByTitleAndBody() {
        noteService.create(campaign.getId(), NoteType.NPC, "Aldric", "A wise wizard.", "");
        noteService.create(campaign.getId(), NoteType.LOCATION, "Forest", "Dense woods.", "");

        var results = noteService.search(campaign.getId(), "wizard");
        assertEquals(1, results.size());
        assertEquals("Aldric", results.get(0).getTitle());
    }

    @Test
    void updatesNoteAndReResolvesLinks() {
        Note note = noteService.create(campaign.getId(), NoteType.GENERIC, "Test", "Links to [[Old]].", "");
        assertEquals(1, noteLinkRepository.findBySourceNoteId(note.getId()).size());

        Note updated = noteService.update(note.getId(), note.getType(), "Test Updated",
            "Links to [[New]] instead.", "updated");
        var links = noteLinkRepository.findBySourceNoteId(updated.getId());
        assertEquals(1, links.size());
        assertEquals("New", links.get(0).getDisplayText());
    }

    @Test
    void deletesNoteAndRemovesLinks() {
        Note note = noteService.create(campaign.getId(), NoteType.GENERIC, "Test", "Links to [[Target]].", "");
        assertFalse(noteLinkRepository.findBySourceNoteId(note.getId()).isEmpty());

        noteService.delete(note.getId());
        assertTrue(noteLinkRepository.findBySourceNoteId(note.getId()).isEmpty());
        assertTrue(noteRepository.findById(note.getId()).isEmpty());
    }

    @Test
    void resolvesSceneLink() {
        Note note = noteService.create(campaign.getId(), NoteType.LOCATION, "The Throne Room",
            "The king sits here. [[scene:Throne Room]]", "");

        var links = noteLinkRepository.findBySourceNoteId(note.getId());
        var sceneLink = links.stream().filter(l -> "SCENE".equals(l.getTargetType())).findFirst().orElseThrow();
        assertTrue(sceneLink.isResolved());
        assertEquals(throneRoom.getId(), sceneLink.getTargetId());

        String rendered = noteService.renderBody(note);
        assertTrue(rendered.contains("/campaigns/" + campaign.getId() + "/adventures/"));
        assertTrue(rendered.contains("/scenes/" + throneRoom.getId()));
    }

    @Test
    void resolvesHandoutAndMapPrefixes() {
        Handout letter = new Handout();
        letter.setCampaign(campaign);
        letter.setTitle("The Letter");
        letter.setFileName("letter.txt");
        handoutRepository.save(letter);

        Note note = noteService.create(campaign.getId(), NoteType.QUEST, "Quest",
            "Read [[handout:The Letter]] and explore [[map:Dungeon Level 1]].", "");

        var links = noteLinkRepository.findBySourceNoteId(note.getId());
        var handoutLink = links.stream().filter(l -> "HANDOUT".equals(l.getTargetType())).findFirst().orElseThrow();
        assertTrue(handoutLink.isResolved());
        assertEquals(letter.getId(), handoutLink.getTargetId());

        var mapLink = links.stream().filter(l -> "MAP".equals(l.getTargetType())).findFirst().orElseThrow();
        assertTrue(mapLink.isResolved());
        assertEquals(dungeon.getId(), mapLink.getTargetId());

        String rendered = noteService.renderBody(note);
        assertTrue(rendered.contains("/campaigns/" + campaign.getId() + "/maps/" + dungeon.getId() + "/play"));
        assertTrue(rendered.contains("/campaigns/" + campaign.getId() + "/handouts#handout-" + letter.getId()));
        assertFalse(rendered.contains("/battle"));
    }
}
