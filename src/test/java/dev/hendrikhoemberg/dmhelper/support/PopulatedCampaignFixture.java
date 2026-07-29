package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
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
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteType;
import dev.hendrikhoemberg.dmhelper.notes.service.NoteService;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableLinkRole;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLink;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.WorldLocationTableLinkRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.ThreatCheckMode;
import dev.hendrikhoemberg.dmhelper.threat.service.ThreatCheckWrite;
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
            UUID tableId,
            UUID dmOnlyHandoutId,
            UUID playerHandoutId) {}

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

    /**
     * DM-facing detail fields on the quest / location / faction detail pages. The real package
     * populates all of these (quest.rewards 12/13, quest.outcomeNotes 3/13, quest.prerequisites
     * 1/13, worldLocation.secrets 6/11, faction.reputationNotes 9/9), and every one of them
     * rendered under the PLAYER-SAFE badge until this fixture could prove otherwise.
     */
    public static final String QUEST_PREREQUISITES = "Teil 1 abgeschlossen";
    public static final String QUEST_REWARDS = "500 gp";
    public static final String QUEST_OUTCOME_NOTES =
            "Wenn die Gruppe die Karte verliert, f\u00fchrt Sildar sie stattdessen zur H\u00f6hle.";
    public static final String LOCATION_SECRETS = "Die Redbrands halten den Ort.";
    public static final String DM_ONLY_HANDOUT_TITLE = "Karte: Cragmaw-Versteck";
    public static final String PLAYER_HANDOUT_TITLE = "Regionalkarte: Schwertküste";
    public static final String FACTION_REPUTATION_NOTES =
            "Der Orden traut der Gruppe erst nach der Befreiung von Phandalin.";

    private final CampaignRepository campaigns;
    private final AdventureService adventures;
    private final SceneStructuredContentService structured;
    private final WorldService world;
    private final QuestService quests;
    private final TrapService traps;
    private final HazardService hazards;
    private final RollableTableService tables;
    private final HandoutRepository handouts;
    private final StatBlockRepository statBlocks;
    private final NoteService notes;
    private final WorldLocationTableLinkRepository locationTableLinks;

    public PopulatedCampaignFixture(CampaignRepository campaigns,
                                    AdventureService adventures,
                                    SceneStructuredContentService structured,
                                    WorldService world,
                                    QuestService quests,
                                    TrapService traps,
                                    HazardService hazards,
                                     RollableTableService tables,
                                     HandoutRepository handouts,
                                     StatBlockRepository statBlocks,
                                     NoteService notes,
                                     WorldLocationTableLinkRepository locationTableLinks) {
        this.campaigns = campaigns;
        this.adventures = adventures;
        this.structured = structured;
        this.world = world;
        this.quests = quests;
        this.traps = traps;
        this.hazards = hazards;
        this.tables = tables;
        this.handouts = handouts;
        this.statBlocks = statBlocks;
        this.notes = notes;
        this.locationTableLinks = locationTableLinks;
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

        structured.addCheck(campaignId, rich.getId(), new SceneCheckCommand(
                "Siegel erkennen", "int", "history", 15, SceneCheckVisibility.DM_FACING,
                "Die Gruppe erkennt das Wappen sofort.", "Nichts.",
                "Die Gruppe erkennt es als adelig, aber nicht welches Haus.",
                null, null, null, "Fixture, S. 22", 1));

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

        Note boteNote = notes.create(campaignId, NoteType.NPC, "Der Bote",
                "Trug den Brief, kennt den Absender nicht.", "bote", true);

        // One participant deliberately left unlinked from statblock, mirroring the 2 of 67 in the real package.
        structured.addParticipant(campaignId, rich.getId(), new SceneParticipantCommand(
                "Namenloser Bote", 1, SceneParticipantDisposition.NEUTRAL,
                "Am Eingang", null, boteNote.getId(), "Fixture, S. 22", 1));

        structured.addTransition(campaignId, rich.getId(), new SceneTransitionCommand(
                SceneTransitionKind.CHOICE, "Weiter in den Gang", second.getId(),
                null, null, "Nur wenn die Truhe offen ist.", "Fixture, S. 23", 0));

        structured.addTransition(campaignId, rich.getId(), new SceneTransitionCommand(
                SceneTransitionKind.EXIT, "Zur\u00fcck nach Phandalin", null,
                "Phandalin, Kapitel 2", "Nur bei Tageslicht.",
                "Die Gruppe verliert einen halben Tag.", "Fixture, S. 23", 1));

        structured.addLink(campaignId, rich.getId(), new SceneLinkCommand(
                SceneLinkRole.RELATED_SCENE, SceneLinkTargetScope.PACKAGE, "SCENE", second.getId(),
                null, null, "Der Gang", null, 0));

        Faction faction = world.createFaction(campaignId, new FactionCommand(
                "Orden des Panzerhandschuhs", "Ordnung herstellen", "Kontakte",
                FACTION_REPUTATION_NOTES,
                null, "order", "Fixture, S. 30"));

        WorldLocation parent = world.createLocation(campaignId, new LocationCommand(
                "Phandalin", LocationKind.SETTLEMENT, null, null, null, null,
                "Ein Grenzdorf.", "Schmied, Gasthaus", LOCATION_SECRETS,
                null, null, "town", "Fixture, S. 28"));

        WorldLocation child = world.createLocation(campaignId, new LocationCommand(
                "Stonehill Inn", LocationKind.SITE, parent.getId(), null, null, null,
                "Das Gasthaus am Platz.", "Zimmer, Bier", null,
                null, null, "inn", "Fixture, S. 29"));

        Note daranNote = notes.create(campaignId, NoteType.NPC, "Daran Edermath",
                "Wei\u00df, wo die Karte liegt.", "npc", true);

        WorldNpc npc = world.createNpc(campaignId, new NpcCommand(
                "Daran Edermath", "Obstbauer und Ex-Ritter", WorldDisposition.FRIENDLY,
                faction.getId(), child.getId(), daranNote.getId(), spaeher.getId(),
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
                "main", QUEST_REWARDS, QUEST_PREREQUISITES, QUEST_OUTCOME_NOTES));
        quests.addObjective(campaignId, quest.getId(), new QuestObjectiveCommand(
                "Karte beschaffen", "Die Karte liegt bei Daran.", QuestObjectiveStatus.NOT_STARTED,
                QuestObjectiveCompletionMode.ALL, 0, "Fixture, S. 40"));

        Trap trap = traps.create(campaignId, new TrapWrite(
                "pit-trap", "Fallgrube", "Eine zehn Fu\u00df tiefe Grube unter loser Erde.",
                ThreatSeverity.SETBACK, 1, 4,
                "Wer auf die lose Erde tritt.", "Der Gang vor der T\u00fcr",
                12, new ThreatCheckWrite(ThreatCheckMode.CHECK, "wis", "perception", 12),
                List.of(), null,
                new ThreatCheckWrite(ThreatCheckMode.SAVE, "dex", null, 13),
                "2d6", List.of(DamageType.BLUDGEONING),
                "Das Opfer liegt am Boden.",
                ThreatResetMode.MANUAL, null, null,
                "Ein Brett \u00fcber der Grube macht sie harmlos.", List.of()), null);

        Hazard hazard = hazards.create(campaignId, new HazardWrite(
                "green-slime", "Gr\u00fcner Schleim", "\u00c4tzender Schleim an der Decke.",
                ThreatSeverity.SETBACK, 1, 4, HazardExposureMode.ON_ENTER,
                "Beim Betreten des Feldes", "10-Fu\u00df-Feld",
                new ThreatCheckWrite(ThreatCheckMode.CHECK, "int", "nature", 11),
                "1d6", List.of(DamageType.ACID),
                "Der Schleim frisst sich durch R\u00fcstung.",
                "Feuer oder K\u00e4lte zerst\u00f6ren ihn.",
                List.of()), null);

        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.TRAP, "Fallgrube im Gang",
                "Der Gang vor der T\u00fcr ist untergraben.", "Fixture, S. 23", 4,
                ThreatKind.TRAP, trap.getId()));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.HAZARD, "Schleim an der Decke",
                "\u00dcber dem Schreibtisch h\u00e4ngt gr\u00fcner Schleim.", "Fixture, S. 23", 5,
                ThreatKind.HAZARD, hazard.getId()));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.SCALING, "F\u00fcr gr\u00f6\u00dfere Gruppen",
                "Bei f\u00fcnf oder mehr Charakteren: ein weiterer Sp\u00e4her.", "Fixture, S. 23", 6));
        structured.addSection(campaignId, rich.getId(), new SceneSectionCommand(
                SceneSectionKind.DEVELOPMENT, "Wenn die Gruppe zu lange braucht",
                "Der Bote kehrt zur\u00fcck und schl\u00e4gt Alarm.", "Fixture, S. 23", 7));

        RollableTable table = tables.create(campaignId, new RollableTableWrite(
                "wilderness", "Zufallsbegegnungen Wildnis", "F\u00fcr Reisen zwischen Orten.",
                TableAddressMode.RANGE, "1d6", TableCategory.ENCOUNTER, List.of("travel"),
                List.of(
                        new RollableTableEntryWrite("goblins", 1, 3, null, "2 Goblins", null, List.of()),
                        new RollableTableEntryWrite("nothing", 4, 6, null, "Nichts passiert", null, List.of())
                )), null);

        WorldLocationTableLink locationTable = new WorldLocationTableLink();
        locationTable.setLocation(parent);
        locationTable.setTable(table);
        locationTable.setRole(RollableTableLinkRole.RANDOM_ENCOUNTERS);
        locationTable.setSortOrder(0);
        locationTableLinks.save(locationTable);

        Campaign campaignRef = campaigns.findById(campaignId).orElseThrow();
        Handout dmHandout = handout(campaignRef, DM_ONLY_HANDOUT_TITLE, "karte,versteck", true);
        Handout playerHandout = handout(campaignRef, PLAYER_HANDOUT_TITLE, "karte,region", false);
        playerHandout.setPresented(true);
        handouts.save(playerHandout);

        return new Seeded(campaignId, adventureId, one.getId(), two.getId(),
                rich.getId(), second.getId(), faction.getId(), parent.getId(), child.getId(),
                npc.getId(), quest.getId(), trap.getId(), hazard.getId(), table.getId(),
                dmHandout.getId(), playerHandout.getId());
    }

    private Handout handout(Campaign campaign, String title, String tags, boolean dmOnly) {
        Handout handout = new Handout();
        handout.setCampaign(campaign);
        handout.setTitle(title);
        handout.setTags(tags);
        handout.setContentType("image/png");
        handout.setFileName(UUID.randomUUID() + ".png");
        handout.setDmOnly(dmOnly);
        handout.setPresented(false);
        return handouts.save(handout);
    }
}
