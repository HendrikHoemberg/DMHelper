package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEvent;
import dev.hendrikhoemberg.dmhelper.calendar.data.TimelineEventRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRoll;
import dev.hendrikhoemberg.dmhelper.dice.data.DiceRollRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.Token;
import dev.hendrikhoemberg.dmhelper.gamemap.data.TokenRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntry;
import dev.hendrikhoemberg.dmhelper.ledger.data.LedgerEntryRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.notes.data.*;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.notes.service.WikiLinkParser;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignment;
import dev.hendrikhoemberg.dmhelper.treasury.data.ItemAssignmentRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Deleting a campaign has to take its whole world with it. Every one of these children
 * carries a foreign key back to the campaign row, so leaving any of them behind means the
 * delete fails outright with a referential integrity violation.
 */
@DataJpaTest
@Import({CampaignService.class, PartyMemberService.class, StatBlockService.class, GameMapService.class,
         NoteService.class, WikiLinkParser.class, SceneRefCleaner.class, AdventureService.class,
         HandoutService.class,
         CampaignCascadeDeleteTest.TestObjectMapperConfig.class,
         dev.hendrikhoemberg.dmhelper.common.service.ContentDestinationRegistry.class,
         dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportValidator.class,
         dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignSchemaValidator.class,
         dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignCatalogResolver.class,
         dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignSemanticValidator.class})
class CampaignCascadeDeleteTest {

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
    @Autowired private GameMapService gameMapService;
    @Autowired private AdventureService adventureService;
    @Autowired private NoteService noteService;

    @Autowired private CampaignRepository campaignRepo;
    @Autowired private PartyMemberRepository partyMemberRepo;
    @Autowired private NoteRepository noteRepo;
    @Autowired private QuickNoteRepository quickNoteRepo;
    @Autowired private StatBlockRepository statBlockRepo;
    @Autowired private EncounterRepository encounterRepo;
    @Autowired private CombatantRepository combatantRepo;
    @Autowired private TokenRepository tokenRepo;
    @Autowired private HandoutRepository handoutRepo;
    @Autowired private LedgerEntryRepository ledgerRepo;
    @Autowired private TimelineEventRepository timelineRepo;
    @Autowired private ItemAssignmentRepository assignmentRepo;
    @Autowired private DiceRollRepository diceRollRepo;
    @Autowired private AdventureRepository adventureRepo;
    @Autowired private EntityManager em;

    /** A campaign with one of everything hanging off it. */
    private Campaign seedFullCampaign() {
        Campaign c = campaignService.create("Doomed", "Everything hangs off this.");
        UUID cid = c.getId();

        PartyMember pm = partyMemberService.create(cid, "Thalia", "Anna", "Ranger 5",
                16, 44, 3, 30, 14, 12, 11, null);

        GameMap map = gameMapService.create(cid, "The Ford", 20, 20, 50);

        Token token = new Token();
        token.setMap(map);
        token.setName("Bugbear");
        token.setKind("MONSTER");
        tokenRepo.save(token);

        StatBlock sb = new StatBlock();
        sb.setCampaign(c);
        sb.setSource(StatBlock.Source.CUSTOM);
        sb.setName("Homebrew Horror");
        sb.setCr("3");
        sb.setType("Aberration");
        sb.setAc(14);
        sb.setHp("40");
        sb.setSpeed("30 ft.");
        statBlockRepo.save(sb);

        Encounter enc = new Encounter();
        enc.setCampaign(c);
        enc.setName("Ambush");
        encounterRepo.save(enc);

        Combatant cb = new Combatant();
        cb.setEncounter(enc);
        cb.setName("Bugbear");
        cb.setMaxHp(27);
        cb.setCurrentHp(27);
        combatantRepo.save(cb);

        noteService.create(cid, NoteType.SESSION_LOG, "Session One", "The party arrives.", null, false);

        QuickNote qn = new QuickNote();
        qn.setCampaign(c);
        qn.setTargetType("ENCOUNTER");
        qn.setTargetId(enc.getId());
        qn.setBody("Remember the ambush.");
        quickNoteRepo.save(qn);

        Handout h = new Handout();
        h.setCampaign(c);
        h.setTitle("The Map");
        h.setFileName("cascade-test-" + UUID.randomUUID() + ".png");
        handoutRepo.save(h);

        LedgerEntry le = new LedgerEntry();
        le.setCampaign(c);
        le.setHolder("PARTY");
        le.setAmount(new BigDecimal("100"));
        le.setCurrency("GP");
        ledgerRepo.save(le);

        TimelineEvent te = new TimelineEvent();
        te.setCampaign(c);
        te.setTitle("The Fall");
        timelineRepo.save(te);

        ItemAssignment ia = new ItemAssignment();
        ia.setCampaign(c);
        ia.setPartyMember(pm);
        ia.setCustomText("Rope");
        ia.setQuantity(1);
        assignmentRepo.save(ia);

        DiceRoll dr = new DiceRoll();
        dr.setCampaign(c);
        dr.setExpression("2d6+4");
        dr.setTotal(11);
        diceRollRepo.save(dr);

        Adventure adv = adventureService.createAdventure(cid, "The Sunken Crown", null, null);
        Chapter ch = adventureService.createChapter(adv.getId(), "Chapter One", null);
        adventureService.createScene(ch.getId(), "The Ford", null, null);

        em.flush();
        return c;
    }

    @Test
    void deletingACampaignTakesItsWholeWorldWithIt() {
        Campaign c = seedFullCampaign();
        UUID cid = c.getId();

        assertThatCode(() -> {
            campaignService.delete(cid);
            em.flush();
        }).doesNotThrowAnyException();

        assertThat(campaignRepo.findById(cid)).isEmpty();

        assertThat(partyMemberRepo.findByCampaignIdOrderByCharacterNameAsc(cid)).isEmpty();
        assertThat(noteRepo.findByCampaignIdOrderByCreatedAtDesc(cid)).isEmpty();
        assertThat(quickNoteRepo.findByCampaignIdOrderByCreatedAtDesc(cid)).isEmpty();
        assertThat(statBlockRepo.findByCampaignIdOrderByNameAsc(cid)).isEmpty();
        assertThat(encounterRepo.findByCampaignIdOrderByNameAsc(cid)).isEmpty();
        assertThat(handoutRepo.findByCampaignIdOrderByTitleAsc(cid)).isEmpty();
        assertThat(ledgerRepo.findByCampaignIdOrderByTimestampDesc(cid)).isEmpty();
        assertThat(timelineRepo.findByCampaignIdOrderByInGameYearAscInGameMonthAscInGameDayAsc(cid)).isEmpty();
        assertThat(assignmentRepo.findByCampaignIdOrderByPartyMemberAsc(cid)).isEmpty();
        assertThat(diceRollRepo.findByCampaignId(cid)).isEmpty();
        assertThat(adventureRepo.findByCampaignIdOrderBySortOrderAsc(cid)).isEmpty();
        assertThat(gameMapService.findByCampaignId(cid)).isEmpty();

        // grandchildren must go too, not just the rows that name the campaign directly
        assertThat(combatantRepo.count()).isZero();
        assertThat(tokenRepo.count()).isZero();
    }

    @Test
    void deletingAnEmptyCampaignStillWorks() {
        Campaign c = campaignService.create("Empty", null);

        campaignService.delete(c.getId());
        em.flush();

        assertThat(campaignRepo.findById(c.getId())).isEmpty();
    }
}
