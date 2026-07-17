package dev.hendrikhoemberg.dmhelper.notes.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.library.service.CustomContentSupport;
import dev.hendrikhoemberg.dmhelper.library.service.LibraryReferenceCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({QuickNoteService.class, NoteService.class, WikiLinkParser.class, StatBlockService.class, SceneRefCleaner.class,
        CustomContentSupport.class, LibraryReferenceCleaner.class,
        dev.hendrikhoemberg.dmhelper.session.service.SessionReferenceCleaner.class,
        dev.hendrikhoemberg.dmhelper.common.service.ContentDestinationRegistry.class})
class QuickNoteServiceTest {

    @MockitoBean private dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService packageKeyService;

    @Autowired private QuickNoteRepository quickNoteRepository;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private NoteRepository noteRepository;
    @Autowired private StatBlockRepository statBlockRepository;
    @Autowired private QuickNoteService quickNoteService;
    @Autowired private AdventureRepository adventureRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private SceneRepository sceneRepository;
    @Autowired private EncounterRepository encounterRepository;
    @Autowired private GameMapRepository gameMapRepository;

    private Campaign campaign;

    @BeforeEach
    void setUp() {
        campaign = new Campaign();
        campaign.setName("Test Campaign");
        campaignRepository.save(campaign);
    }

    @Test
    void createsQuickNote() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "ENCOUNTER", UUID.randomUUID(), "Watch for traps.");
        assertNotNull(qn.getId());
        assertEquals("Watch for traps.", qn.getBody());
        assertEquals("ENCOUNTER", qn.getTargetType());
    }

    @Test
    void findsByTarget() {
        UUID targetId = UUID.randomUUID();
        quickNoteService.create(campaign.getId(), "MAP", targetId, "First note.");
        quickNoteService.create(campaign.getId(), "MAP", targetId, "Second note.");

        var notes = quickNoteService.findByTarget(campaign.getId(), "MAP", targetId);
        assertEquals(2, notes.size());
    }

    @Test
    void resolvesHumanReadableTargetLabel() {
        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Lower Crypt");
        gameMapRepository.save(map);
        QuickNote note = quickNoteService.create(campaign.getId(), "MAP", map.getId(), "Check the door.");

        assertEquals("Lower Crypt", quickNoteService.targetLabel(note));
    }

    @Test
    void promotesQuickNoteToNote() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "STATBLOCK", UUID.randomUUID(), "This goblin has a secret lair.");
        Note note = quickNoteService.promoteToNote(campaign.getId(), qn.getId());

        assertNotNull(note.getId());
        assertEquals("This goblin has a secret lair.", note.getBody());
        assertEquals(NoteType.GENERIC, note.getType());
        assertTrue(quickNoteRepository.findById(qn.getId()).isEmpty());
    }

    @Test
    void promotesQuickNoteWithTitle() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "CAMPAIGN", campaign.getId(), "The party must find the crystal.");
        Note note = quickNoteService.promoteToNote(campaign.getId(), qn.getId(), "The Crystal Quest", NoteType.QUEST);

        assertEquals("The Crystal Quest", note.getTitle());
        assertEquals(NoteType.QUEST, note.getType());
    }

    @Test
    void deletesQuickNote() {
        QuickNote qn = quickNoteService.create(campaign.getId(), "ENCOUNTER", UUID.randomUUID(), "Temp note.");
        quickNoteService.delete(campaign.getId(), qn.getId());
        assertTrue(quickNoteRepository.findById(qn.getId()).isEmpty());
    }

    @Test
    void promotesQuickNoteWithSceneLink() {
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
        sceneRepository.save(scene);

        QuickNote qn = quickNoteService.create(campaign.getId(), "SCENE", scene.getId(), "The king sits here.");
        Note note = quickNoteService.promoteToNote(campaign.getId(), qn.getId(), "Throne Room Notes", NoteType.LOCATION);

        assertTrue(note.getBody().startsWith("[[scene:Throne Room]]"));
        assertTrue(note.getBody().contains("The king sits here."));
    }

    @Test
    void promotesQuickNoteWithPrefixedLink() {
        var sb = new StatBlock();
        sb.setName("Goblin");
        sb.setSource(ContentSource.SRD);
        sb.setSourceKey("goblin");
        sb.setCr("1/4");
        sb.setType("humanoid");
        sb.setHp("7 (2d6)");
        statBlockRepository.save(sb);

        QuickNote qn = quickNoteService.create(campaign.getId(), "STATBLOCK", sb.getId(), "This goblin has a secret lair.");
        Note note = quickNoteService.promoteToNote(campaign.getId(), qn.getId(), "Goblin Secrets", NoteType.LOCATION);

        assertTrue(note.getBody().startsWith("[[statblock:Goblin]]"));
        assertTrue(note.getBody().contains("This goblin has a secret lair."));
    }

    @Test
    void rejectsUnknownTargetType() {
        assertThrows(IllegalArgumentException.class,
                () -> quickNoteService.create(campaign.getId(), "UNKNOWN", UUID.randomUUID(), "No target"));
    }

    @Test
    void refusesMutationThroughAnotherCampaign() {
        Campaign other = new Campaign();
        other.setName("Other Campaign");
        campaignRepository.save(other);
        QuickNote note = quickNoteService.create(campaign.getId(), "CAMPAIGN", campaign.getId(), "Private note");

        assertThrows(NotFoundException.class,
                () -> quickNoteService.delete(other.getId(), note.getId()));
        assertTrue(quickNoteRepository.findById(note.getId()).isPresent());
    }

    @Test
    void promotesQuickNoteWithEncounterLink() {
        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setName("Crypt Ambush");
        encounter.setStatus(Encounter.Status.PLANNED);
        encounterRepository.save(encounter);

        QuickNote qn = quickNoteService.create(
                campaign.getId(), "ENCOUNTER", encounter.getId(), "The ghouls arrive in round two.");
        Note promoted = quickNoteService.promoteToNote(campaign.getId(), qn.getId());

        assertTrue(promoted.getBody().startsWith("[[encounter:Crypt Ambush]]"));
    }
}
