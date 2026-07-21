package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.Quest;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveCompletionMode;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestObjectiveStatus;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestStatus;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestService;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestService.*;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableAddressMode;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.TableCategory;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableEntryWrite;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableService;
import dev.hendrikhoemberg.dmhelper.rollabletable.service.RollableTableWrite;
import dev.hendrikhoemberg.dmhelper.threat.data.*;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardService;
import dev.hendrikhoemberg.dmhelper.threat.service.HazardWrite;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapService;
import dev.hendrikhoemberg.dmhelper.threat.service.TrapWrite;
import dev.hendrikhoemberg.dmhelper.world.data.*;
import dev.hendrikhoemberg.dmhelper.world.service.WorldService;
import dev.hendrikhoemberg.dmhelper.world.service.WorldService.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class PopulatedCampaignFixture {

    public record Seeded(
            UUID campaignId,
            UUID adventureId,
            UUID chapterOneId,
            UUID chapterTwoId,
            UUID richSceneId,
            UUID secondSceneId,
            UUID factionId,
            UUID locationId,
            UUID childLocationId,
            UUID npcId,
            UUID questId,
            UUID trapId,
            UUID hazardId,
            UUID tableId) {}

    public static final int CHAPTER_TWO_SCENE_COUNT = 12;

    public static final String READ_ALOUD_BODY =
            "Auf dem Schreibtisch, zwischen Bestellungen f\u00fcr die Werkstatt, liegt ein "
            + "zusammengefalteter Brief. Das Wachssiegel ist gebrochen. Die Handschrift ist "
            + "sauber und eng, und der Text ist in einer Sprache verfasst, die keiner von euch "
            + "auf Anhieb erkennt \u2014 bis euch auff\u00e4llt, dass die Unterschrift ein einzelnes "
            + "Wort ist, das ihr sehr wohl kennt.";

    /** DM prose describing the room before the players discover it. */
    public static final String SCENE_SUMMARY =
            "Der Bote der Schwarzen Spinne hat den Brief hier liegen lassen.";

    public static final String SECRET_BODY =
            "Der Brief stammt von der Schwarzen Spinne. Wer ihn liest und Zwergisch beherrscht, "
            + "erf\u00e4hrt, dass die Mine bereits besetzt ist.";

    /** Stats of the participant-linked statblock, asserted on by the rendering tests. */
    public static final String PARTICIPANT_STATBLOCK_NAME = "Rotbrenner-Schläger";
    public static final int PARTICIPANT_STATBLOCK_AC = 14;
    public static final String PARTICIPANT_STATBLOCK_HP = "16 (3W8+3)";

    public static final String TREASURE_BODY =
            "In der verschlossenen Truhe unter dem Schreibtisch liegen 120 gp und ein Paar "
            + "Stiefel der Elfenhaftigkeit.";

    private final CampaignRepository campaigns;
    private final AdventureService adventures;
    private final SceneStructuredContentService structured;
    private final WorldService world;
    private final QuestService quests;
    private final TrapService traps;
    private final HazardService hazards;
    private final RollableTableService tables;
    private final StatBlockRepository statBlocks;

    public PopulatedCampaignFixture(CampaignRepository campaigns,
                                    AdventureService adventures,
                                    SceneStructuredContentService structured,
                                    WorldService world,
                                    QuestService quests,
                                    TrapService traps,
                                    HazardService hazards,
                                    RollableTableService tables,
                                    StatBlockRepository statBlocks) {
        this.campaigns = campaigns;
        this.adventures = adventures;
        this.structured = structured;
        this.world = world;
        this.quests = quests;
        this.traps = traps;
        this.hazards = hazards;
        this.tables = tables;
        this.statBlocks = statBlocks;
    }

    @Transactional
    public Seeded seed() {
        Campaign campaign = new Campaign();
        campaign.setName("Populated Fixture Campaign");
        campaign.setDescription("Published-adventure-shaped fixture");
        UUID campaignId = campaigns.save(campaign).getId();

        Adventure adventure = adventures.createAdventure(
                campaignId, "Die Verlorene Probe", "A two-part fixture adventure", "Fixture, S. 1");
        UUID adventureId = adventure.getId();

        Chapter one = adventures.createChapter(adventureId, "Teil 1: Auf der Stra\u00dfe", "Opening chapter.");
        Chapter two = adventures.createChapter(adventureId, "Teil 2: Die Spinne", "Second chapter.");

        Scene rich = adventures.createScene(one.getId(), "Der Schreibtisch", "S1",
                "Ein enger Raum mit einem Schreibtisch aus dunklem Holz.");
        Scene second = adventures.createScene(one.getId(), "Der Gang", "S2",
                "Ein Gang, der nach Norden f\u00fchrt.");

        for (int i = 1; i <= CHAPTER_TWO_SCENE_COUNT; i++) {
            adventures.createScene(two.getId(), "Raum " + i, "R" + i, "Beschreibung f\u00fcr Raum " + i + ".");
        }

        structured.updateMetadata(campaignId, rich.getId(), new SceneMetadataCommand(
                SCENE_SUMMARY, "Fixture, S. 22", "brief,spinne", null));

        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.READ_ALOUD, "Der Brief", READ_ALOUD_BODY, "Fixture, S. 22", 0));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.SECRET, "Absender", SECRET_BODY, "Fixture, S. 22", 1));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.TREASURE, "Truhe", TREASURE_BODY, "Fixture, S. 23", 2));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.DM_ADVICE, "Hinweis", "Lass die Gruppe suchen.", null, 3));

        structured.addCheck(campaignId, rich.getId(), new SceneCheckCommand(
                "Brief entziffern", "int", "investigation", 13, SceneCheckVisibility.PLAYER_FACING,
                "Der Absender wird klar.", "Nichts.", null, null, null, null, "Fixture, S. 22", 0));

        // The real LMoP package resolves 65 of its 67 participants to a statblock, so the
        // fixture must too -- a participant with statBlockId=null hid the fact that the UI
        // never rendered the link at all.
        StatBlock spaeher = new StatBlock();
        spaeher.setSource(ContentSource.CUSTOM);
        spaeher.setName(PARTICIPANT_STATBLOCK_NAME);
        spaeher.setCr("1/2");
        spaeher.setType("Humanoider (Mensch)");
        spaeher.setAc(PARTICIPANT_STATBLOCK_AC);
        spaeher.setHp(PARTICIPANT_STATBLOCK_HP);
        spaeher = statBlocks.save(spaeher);

        structured.addParticipant(campaignId, rich.getId(), new SceneParticipantCommand(
                "Sp\u00e4her der Redbrands", 2, SceneParticipantDisposition.HOSTILE,
                "Hinter der T\u00fcr", spaeher.getId(), null, "Fixture, S. 22", 0));

        // One participant deliberately left unlinked, mirroring the 2 of 67 in the real package.
        structured.addParticipant(campaignId, rich.getId(), new SceneParticipantCommand(
                "Namenloser Bote", 1, SceneParticipantDisposition.NEUTRAL,
                "Am Eingang", null, null, "Fixture, S. 22", 1));

        structured.addTransition(campaignId, rich.getId(), new SceneTransitionCommand(
                SceneTransitionKind.CHOICE, "Weiter in den Gang", second.getId(),
                null, null, "Nur wenn die Truhe offen ist.", "Fixture, S. 23", 0));

        structured.addLink(campaignId, rich.getId(), new SceneLinkCommand(
                SceneLinkRole.RELATED_SCENE, SceneLinkTargetScope.PACKAGE, "SCENE", second.getId(),
                null, null, "Der Gang", null, 0));

        Faction faction = world.createFaction(campaignId, new FactionCommand(
                "Orden des Panzerhandschuhs", "Ordnung herstellen", "Kontakte", null,
                null, "order", "Fixture, S. 30"));

        WorldLocation parent = world.createLocation(campaignId, new LocationCommand(
                "Phandalin", LocationKind.SETTLEMENT, null, null, null, null,
                "Ein Grenzdorf.", "Schmied, Gasthaus", "Die Redbrands halten den Ort.",
                null, null, "town", "Fixture, S. 28"));

        WorldLocation child = world.createLocation(campaignId, new LocationCommand(
                "Stonehill Inn", LocationKind.SITE, parent.getId(), null, null, null,
                "Das Gasthaus am Platz.", "Zimmer, Bier", null,
                null, null, "inn", "Fixture, S. 29"));

        WorldNpc npc = world.createNpc(campaignId, new NpcCommand(
                "Daran Edermath", "Obstbauer und Ex-Ritter", WorldDisposition.FRIENDLY,
                faction.getId(), child.getId(), null, null,
                "Ein hochgewachsener Halbelf mit wei\u00dfem Haar.", "Ruhig, bedacht",
                "Will den Orden wiederbeleben.", "War fr\u00fcher Ritter.", "Langschwert",
                WorldNpcStatus.ALIVE, "ally", "Fixture, S. 30"));

        world.createClock(campaignId, new ClockCommand(
                faction.getId(), "Der Orden formiert sich", 4, 1, null, null,
                "F\u00fcllt sich, wenn die Gruppe hilft.", "Fixture, S. 30", 0));

        world.createRelationship(campaignId, new RelationshipCommand(
                RelationshipKind.MEMBER_OF, "NPC", npc.getId(), "FACTION", faction.getId(),
                true, RelationshipKnowledge.PUBLIC, RelationshipStatus.ACTIVE,
                "Gr\u00fcndungsmitglied.", "Fixture, S. 30", 0));

        Quest quest = quests.createQuest(campaignId, new QuestCommand(
                "Die Mine finden", QuestStatus.NOT_STARTED,
                "Findet den Eingang zur Wave Echo Cave.", "Fixture, S. 40",
                "main", "500 gp", "Teil 1 abgeschlossen", null));
        quests.addObjective(campaignId, quest.getId(), new QuestObjectiveCommand(
                "Karte beschaffen", "Die Karte liegt bei Daran.", QuestObjectiveStatus.NOT_STARTED,
                QuestObjectiveCompletionMode.ALL, 0, "Fixture, S. 40"));

        Trap trap = traps.create(campaignId, new TrapWrite(
                "pit-trap", "Fallgrube", "Eine zehn Fu\u00df tiefe Grube unter loser Erde.",
                ThreatSeverity.SETBACK, null, null, null, null, null, null,
                List.of(), null, null, null, List.of(), null,
                ThreatResetMode.MANUAL, null, null, null, List.of()), null);

        Hazard hazard = hazards.create(campaignId, new HazardWrite(
                "green-slime", "Gr\u00fcner Schleim", "\u00c4tzender Schleim an der Decke.",
                ThreatSeverity.SETBACK, 1, 4, HazardExposureMode.ON_ENTER,
                "Beim Betreten des Feldes", "10-Fu\u00df-Feld", null,
                "1d6", List.of(DamageType.ACID), null, "Wird mit Feuer zerst\u00f6rt.",
                List.of()), null);

        RollableTable table = tables.create(campaignId, new RollableTableWrite(
                "wilderness", "Zufallsbegegnungen Wildnis", "F\u00fcr Reisen zwischen Orten.",
                TableAddressMode.RANGE, "1d6", TableCategory.ENCOUNTER, List.of("travel"),
                List.of(
                        new RollableTableEntryWrite("goblins", 1, 3, null, "2 Goblins", null, List.of()),
                        new RollableTableEntryWrite("nothing", 4, 6, null, "Nichts passiert", null, List.of())
                )), null);

        return new Seeded(campaignId, adventureId, one.getId(), two.getId(),
                rich.getId(), second.getId(), faction.getId(), parent.getId(), child.getId(),
                npc.getId(), quest.getId(), trap.getId(), hazard.getId(), table.getId());
    }
}
