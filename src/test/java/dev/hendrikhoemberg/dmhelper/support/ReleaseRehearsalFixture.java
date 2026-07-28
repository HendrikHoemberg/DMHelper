package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.adventure.data.*;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService;
import dev.hendrikhoemberg.dmhelper.adventure.service.SceneStructuredContentService.*;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransitionRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterWaveRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.WaveTriggerKind;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.service.GameMapService;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.*;
import dev.hendrikhoemberg.dmhelper.quest.service.QuestService;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ReleaseRehearsalFixture {

    public enum Shape {
        /** One chapter, one map, one hostile scene. The baseline rehearsal. */
        LINEAR_ONE_MAP,
        /** Two chapters, three branch choices, two map scales, and a map-free wave encounter. */
        BRANCHED_TWO_MAPS
    }

    public record Seeded(UUID campaignId, UUID adventureId, UUID hostileSceneId,
                         UUID ambushSceneId, UUID branchedEncounterId, UUID branchedMainWaveId,
                         UUID branchedReserveWaveId, List<UUID> branchedReserveCombatantIds,
                         UUID branchSceneId, UUID playableMapId, UUID playerSafeHandoutId,
                         UUID dmSourceHandoutId, UUID derivativeHandoutId, UUID questId,
                         List<UUID> partyMemberIds) {}

    private final CampaignRepository campaigns;
    private final AdventureService adventures;
    private final SceneStructuredContentService structured;
    private final StatBlockRepository statBlocks;
    private final GameMapService maps;
    private final GameMapRepository mapRepository;
    private final HandoutService handouts;
    private final HandoutRepository handoutRepository;
    private final QuestService quests;
    private final PartyMemberRepository partyMembers;
    private final EncounterService encounters;
    private final SceneRepository scenes;
    private final AdventureRepository adventureRepository;
    private final SceneSectionRepository sections;
    private final SceneParticipantRepository participants;
    private final SceneTransitionRepository transitions;
    private final EncounterRepository encounterRepository;
    private final EncounterWaveRepository encounterWaveRepository;
    private final CombatantRepository combatantRepository;

    public ReleaseRehearsalFixture(CampaignRepository campaigns, AdventureService adventures,
                                   SceneStructuredContentService structured,
                                   StatBlockRepository statBlocks, GameMapService maps,
                                   GameMapRepository mapRepository, HandoutService handouts,
                                   HandoutRepository handoutRepository, QuestService quests,
                                   PartyMemberRepository partyMembers, EncounterService encounters,
                                   SceneRepository scenes, AdventureRepository adventureRepository,
                                   SceneSectionRepository sections, SceneParticipantRepository participants,
                                   SceneTransitionRepository transitions, EncounterRepository encounterRepository,
                                   EncounterWaveRepository encounterWaveRepository,
                                   CombatantRepository combatantRepository) {
        this.campaigns = campaigns;
        this.adventures = adventures;
        this.structured = structured;
        this.statBlocks = statBlocks;
        this.maps = maps;
        this.mapRepository = mapRepository;
        this.handouts = handouts;
        this.handoutRepository = handoutRepository;
        this.quests = quests;
        this.partyMembers = partyMembers;
        this.encounters = encounters;
        this.scenes = scenes;
        this.adventureRepository = adventureRepository;
        this.sections = sections;
        this.participants = participants;
        this.transitions = transitions;
        this.encounterRepository = encounterRepository;
        this.encounterWaveRepository = encounterWaveRepository;
        this.combatantRepository = combatantRepository;
    }

    @Transactional
    public Seeded seed() throws IOException { return seed(Shape.LINEAR_ONE_MAP, false); }

    @Transactional
    public Seeded seed(Shape shape) throws IOException { return seed(shape, false); }

    @Transactional
    public Seeded seedForRehearsal(Shape shape) throws IOException { return seed(shape, true); }

    private Seeded seed(Shape shape, boolean leaveHostileEncounterUnlinked) throws IOException {
        Campaign campaign = new Campaign();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        campaign.setName("The Hollow Beacon " + suffix);
        campaign.setDescription("Synthetic session rehearsal campaign");
        UUID campaignId = campaigns.save(campaign).getId();

        var adventure = adventures.createAdventure(campaignId, "Lanterns Below",
                "An invented expedition beneath a quiet marsh beacon.", "Synthetic source");
        var chapter = adventures.createChapter(adventure.getId(), "The Drowned Stair", "A measured descent.");
        Scene approach = adventures.createScene(chapter.getId(), "Mossbound Approach", "A1",
                "A lantern-marked path leads toward the sealed undercroft.");
        Scene hostile = adventures.createScene(chapter.getId(), "Beacon Undercroft", "A2",
                "Cold water gathers around a cracked stone dais.");
        Scene branch = adventures.createScene(chapter.getId(), "Tideglass Gallery", "A3",
                "A side gallery offers a quieter route through the ruins.");
        structured.updateMetadata(campaignId, approach.getId(), new SceneMetadataCommand(
                "The approach is quiet but freshly disturbed.", "Synthetic, scene A1", "approach,marsh", null));
        structured.updateMetadata(campaignId, hostile.getId(), new SceneMetadataCommand(
                "The undercroft is ready for a tactical encounter.", "Synthetic, scene A2", "hostile,undercroft", null));
        structured.updateMetadata(campaignId, branch.getId(), new SceneMetadataCommand(
                "A branching gallery opens beyond the approach.", "Synthetic, scene A3", "branch,ruins", null));
        structured.addSection(campaignId, approach.getId(), new SceneSectionCommand(
                SceneSectionKind.READ_ALOUD, "The low bell", "A low bell trembles beneath the wet stone.", "Synthetic, A1", 0));
        structured.addSection(campaignId, hostile.getId(), new SceneSectionCommand(
                SceneSectionKind.READ_ALOUD, "Undercroft floor", "Four silhouettes turn as the lantern light enters.", "Synthetic, A2", 0));
        structured.addTransition(campaignId, approach.getId(), new SceneTransitionCommand(
                SceneTransitionKind.CHOICE, "Descend to the undercroft", hostile.getId(), null,
                "When the party follows the bell.", "The descent is slick.", "Synthetic, A1", 0));
        structured.addTransition(campaignId, approach.getId(), new SceneTransitionCommand(
                SceneTransitionKind.CHOICE, "Take the tideglass gallery", branch.getId(), null,
                "When the party avoids the bell.", "The gallery is narrow.", "Synthetic, A1", 1));

        Scene ambush = null;
        if (shape == Shape.BRANCHED_TWO_MAPS) {
            var secondChapter = adventures.createChapter(adventure.getId(), "The Lantern Vault",
                    "The descent opens into a chamber beyond the marsh ruins.");
            ambush = adventures.createScene(secondChapter.getId(), "Lantern Vault Ambush", "B1",
                    "The vault is quiet until movement answers from the dark.");
            structured.updateMetadata(campaignId, ambush.getId(), new SceneMetadataCommand(
                    "The vault becomes hostile without a battle map.", "Synthetic, scene B1", "hostile,vault", null));
            structured.addSection(campaignId, ambush.getId(), new SceneSectionCommand(
                    SceneSectionKind.READ_ALOUD, "Vault silence", "The lantern flame bends toward unseen footsteps.",
                    "Synthetic, B1", 0));
            structured.addTransition(campaignId, approach.getId(), new SceneTransitionCommand(
                    SceneTransitionKind.CHOICE, "Cross the sealed bridge", ambush.getId(), null,
                    "When the party chooses the long bridge.", "The bridge groans below you.", "Synthetic, A1", 2));
            structured.addTransition(campaignId, branch.getId(), new SceneTransitionCommand(
                    SceneTransitionKind.CHOICE, "Enter the lantern vault", ambush.getId(), null,
                    "When the gallery route reaches the vault.", "The vault answers with movement.", "Synthetic, A3", 0));
        }

        List<StatBlock> foeBlocks = List.of(
                statBlock(campaign, "Bog Sentinel", "1/2", 15, "18 (4d8)", 2, "bog-sentinel"),
                statBlock(campaign, "Bog Skirmisher", "1/4", 13, "11 (2d8)", 3, "bog-skirmisher-steps"),
                statBlock(campaign, "Bog Skirmisher", "1/4", 13, "11 (2d8)", 3, "bog-skirmisher-reeds"),
                statBlock(campaign, "Marsh Warden", "2", 16, "30 (4d10+8)", 1, "marsh-warden"));
        String[] names = {"Sentinel at the sluice", "Skirmisher by the steps",
                "Skirmisher in the reeds", "Warden of the bell"};
        for (int i = 0; i < foeBlocks.size(); i++) {
            var participant = structured.addParticipant(campaignId, hostile.getId(), new SceneParticipantCommand(
                    names[i], 1, SceneParticipantDisposition.HOSTILE, "Undercroft position " + (i + 1),
                    foeBlocks.get(i).getId(), null, "Synthetic, A2", i));
            participant.setStatBlock(foeBlocks.get(i));
            participants.save(participant);
        }

        var map = maps.create(campaignId, "Beacon Undercroft [" + suffix + "]", 20, 15, 64);
        maps.updateMode(map.getId(), "GRID", true);
        hostile.setMapRequirement(SceneMapRequirement.REQUIRED);
        hostile.setMap(map);
        scenes.save(hostile);
        var encounter = encounters.create(campaignId, new EncounterService.CreateRequest(
                "Undercroft Alarm", map.getId()));
        var encounterEntity = encounterRepository.findById(encounter.id()).orElseThrow();
        encounterEntity.setEncounterKey("undercroft-alarm-" + suffix);
        encounterRepository.save(encounterEntity);
        if (!leaveHostileEncounterUnlinked) adventures.linkEncounter(hostile.getId(), encounter.id());
        for (int i = 0; i < foeBlocks.size(); i++) {
            StatBlock foe = foeBlocks.get(i);
            var combatant = encounters.addCombatant(encounter.id(), new EncounterService.CombatantCreateRequest(
                    foe.getName(), 0, "MONSTER", null, foe.getId()));
            var combatantEntity = combatantRepository.findById(combatant.id()).orElseThrow();
            combatantEntity.setNotes("rehearsal-" + suffix + "-combatant-" + (i + 1));
            combatantRepository.save(combatantEntity);
        }

        if (shape == Shape.BRANCHED_TWO_MAPS) {
            var secondMap = maps.create(campaignId, "Tideglass Gallery [" + suffix + "]", 12, 10, 48);
            maps.updateMode(secondMap.getId(), "GRID", true);
            branch.setMapRequirement(SceneMapRequirement.REQUIRED);
            branch.setMap(secondMap);
            scenes.save(branch);

            ambush.setMapRequirement(SceneMapRequirement.NONE);
            scenes.save(ambush);
            for (int i = 0; i < foeBlocks.size(); i++) {
                var participant = structured.addParticipant(campaignId, ambush.getId(), new SceneParticipantCommand(
                        "Vault threat " + (i + 1), 1, SceneParticipantDisposition.HOSTILE,
                        "Theatre-of-mind position " + (i + 1), foeBlocks.get(i).getId(), null,
                        "Synthetic, B1", i));
                participant.setStatBlock(foeBlocks.get(i));
                participants.save(participant);
            }
        }

        UUID branchedEncounterId = null;
        UUID branchedMainWaveId = null;
        UUID branchedReserveWaveId = null;
        List<UUID> branchedReserveCombatantIds = List.of();
        if (shape == Shape.BRANCHED_TWO_MAPS) {
            var branchedEncounter = encounters.create(campaignId,
                    new EncounterService.CreateRequest("Encounter: Lantern Vault Ambush", null));
            var branchedEntity = encounterRepository.findById(branchedEncounter.id()).orElseThrow();
            branchedEntity.setEncounterKey("lantern-vault-ambush-" + suffix);
            encounterRepository.save(branchedEntity);
            adventures.linkEncounter(ambush.getId(), branchedEncounter.id());

            for (int i = 0; i < foeBlocks.size(); i++) {
                encounters.addFromLibrary(branchedEncounter.id(), new EncounterService.AddFromLibraryRequest(
                        foeBlocks.get(i).getId(), 1, names[i], null, null, null, null));
            }
            branchedMainWaveId = encounters.listWaves(branchedEncounter.id()).stream()
                    .filter(wave -> wave.waveKey().equals("main"))
                    .findFirst().orElseThrow().id();
            var reserveWave = encounters.createWave(branchedEncounter.id(),
                    new EncounterService.CreateWaveRequest("vault-reinforcements", "Vault reinforcements",
                            WaveTriggerKind.MANUAL, null, "Synthetic reserve wave."));
            branchedReserveWaveId = reserveWave.id();
            var reserveCombatants = encounters.addFromLibrary(branchedEncounter.id(),
                    new EncounterService.AddFromLibraryRequest(foeBlocks.get(0).getId(), 1,
                            "Vault reinforcements", reserveWave.id(), null, null, null));
            reserveCombatants.forEach(combatant -> combatantRepository.findById(combatant.id()).ifPresent(entity -> {
                entity.setNotes("rehearsal-" + suffix + "-reserve-combatant");
                combatantRepository.save(entity);
            }));
            branchedReserveCombatantIds = reserveCombatants.stream().map(EncounterService.CombatantDto::id).toList();
            branchedEncounterId = branchedEncounter.id();
        }

        byte[] image = png("safe");
        Handout playerSafe = handouts.createImported(campaignId, "Beacon Approach (player map)", "map",
                "beacon-approach.png", "image/png", image);
        handouts.classify(campaignId, playerSafe.getId(), Handout.SafetyClassification.PLAYER_SAFE);
        playerSafe.setAssetKind(Handout.AssetKind.REGIONAL_MAP);
        playerSafe.setPresented(true);
        handoutRepository.save(playerSafe);
        Handout dmSource = handouts.createImported(campaignId, "Undercroft reference page", "source",
                "undercroft-reference.png", "image/png", image);
        handouts.classify(campaignId, dmSource.getId(), Handout.SafetyClassification.DM_SOURCE);
        dmSource.setAssetKind(Handout.AssetKind.SOURCE_PAGE);
        handoutRepository.save(dmSource);
        Handout derivative = handouts.createDerivative(campaignId, dmSource.getId(),
                "Undercroft player extract", "{\"sourceWidth\":1,\"sourceHeight\":1,\"cropX\":0,\"cropY\":0,\"cropWidth\":1,\"cropHeight\":1,\"redactions\":[]}",
                new MockMultipartFile("file", "extract.png", "image/png", image));
        derivative.setAssetKind(Handout.AssetKind.PLAYER_HANDOUT);
        handoutRepository.save(derivative);

        Quest quest = quests.createQuest(campaignId, new QuestService.QuestCommand(
                "Light the hollow beacon", QuestStatus.ACTIVE,
                "Restore the beacon before the marsh tide rises.", "Synthetic, quest 1", "main",
                "A safe route through the marsh", "Find the bell chamber", "The marsh crossing remains open."));
        quests.addObjective(campaignId, quest.getId(), new QuestService.QuestObjectiveCommand(
                "Recover the wickstone", "Find the wickstone beneath the bell.", QuestObjectiveStatus.COMPLETED,
                QuestObjectiveCompletionMode.ALL, 0, "Synthetic, quest 1"));
        quests.addObjective(campaignId, quest.getId(), new QuestService.QuestObjectiveCommand(
                "Relight the beacon", "Place the wickstone in the hollow lantern.", QuestObjectiveStatus.NOT_STARTED,
                QuestObjectiveCompletionMode.ALL, 1, "Synthetic, quest 1"));
        if (shape == Shape.BRANCHED_TWO_MAPS) {
            quests.addObjective(campaignId, quest.getId(), new QuestService.QuestObjectiveCommand(
                    "Reach the lantern vault", "Choose a route into the second chapter.", QuestObjectiveStatus.NOT_STARTED,
                    QuestObjectiveCompletionMode.ALL, 2, "Synthetic, quest 1"));
        }

        List<UUID> partyIds = new ArrayList<>();
        partyIds.add(party(campaign, "Ilsa Fenwright", 18, 34, 14));
        partyIds.add(party(campaign, "Ordo Brack", 14, 27, 11));
        partyIds.add(party(campaign, "Nesh Vell", 12, 21, 16));
        partyIds.add(party(campaign, "Tamsin Aroe", 15, 25, 13));
        adventures.setCurrentScene(campaignId, approach.getId());
        return new Seeded(campaignId, adventure.getId(), hostile.getId(), ambush != null ? ambush.getId() : null,
                branchedEncounterId, branchedMainWaveId, branchedReserveWaveId, branchedReserveCombatantIds,
                branch.getId(), map.getId(),
                playerSafe.getId(), dmSource.getId(), derivative.getId(), quest.getId(), partyIds);
    }

    @Transactional(readOnly = true)
    public List<UUID> statBlockIdsOf(Seeded seeded) {
        return List.of("bog-sentinel", "bog-skirmisher-steps", "bog-skirmisher-reeds", "marsh-warden")
                .stream()
                .map(key -> statBlocks.findByCampaignIdAndSourceKey(seeded.campaignId(), key).orElseThrow().getId())
                .toList();
    }

    /** Hostile scenes that declare no map requirement — theatre of mind. */
    @Transactional(readOnly = true)
    public long mapFreeHostileSceneCount(Seeded seeded) {
        return scenes.findByChapterAdventureCampaignId(seeded.campaignId()).stream()
                .filter(s -> s.getParticipants().stream()
                        .anyMatch(p -> p.getDisposition() == SceneParticipantDisposition.HOSTILE))
                .filter(s -> s.getMapRequirement() == null || s.getMapRequirement() == SceneMapRequirement.NONE)
                .count();
    }

    private StatBlock statBlock(Campaign campaign, String name, String cr, int ac, String hp, int initiativeBonus,
                                String sourceKey) {
        StatBlock block = new StatBlock();
        block.setSource(ContentSource.CUSTOM);
        block.setCampaign(campaign);
        block.setName(name);
        block.setCr(cr);
        block.setType("Marsh construct");
        block.setAc(ac);
        block.setHp(hp);
        block.setSpeed("30 ft.");
        block.setDexScore(initiativeBonus + 10);
        block.setSourceKey(sourceKey);
        return statBlocks.save(block);
    }

    private UUID party(Campaign campaign, String name, int ac, int hp, int passive) {
        PartyMember member = new PartyMember();
        member.setCampaign(campaign);
        member.setCharacterName(name);
        member.setClassAndLevel("Rehearsal adventurer");
        member.setAc(ac);
        member.setMaxHp(hp);
        member.setCurrentHp(hp);
        member.setInitiativeBonus(2);
        member.setSpeed(30);
        member.setPassivePerception(passive);
        member.setPassiveInsight(10);
        member.setPassiveInvestigation(10);
        return partyMembers.save(member).getId();
    }

    private static byte[] png(String label) {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0xff000000 | (label.hashCode() & 0x00ffffff));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(image, "png", bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not create fixture image", e);
        }
    }

    @Transactional(readOnly = true)
    public String textualContentOf(Seeded seeded) {
        StringBuilder text = new StringBuilder();
        campaigns.findById(seeded.campaignId()).ifPresent(c -> append(text, c.getName(), c.getDescription()));
        adventureRepository.findById(seeded.adventureId()).ifPresent(a ->
                append(text, a.getName(), a.getDescription(), a.getSourceAttribution()));
        adventures.findChaptersByAdventure(seeded.adventureId()).forEach(chapter ->
                append(text, chapter.getTitle(), chapter.getIntro()));

        scenes.findByCampaignIdOrderByChapterAndSort(seeded.campaignId()).forEach(scene -> {
            append(text, scene.getTitle(), scene.getSceneKey(), scene.getBody(), scene.getSummary(),
                    scene.getSourceLocator(), scene.getTags(), scene.getMapRegionKey(), scene.getMapRequirement());
            sections.findBySceneIdOrderBySortOrderAsc(scene.getId()).forEach(section ->
                    append(text, section.getKind(), section.getLabel(), section.getBody(), section.getSourceLocator(),
                            section.getThreatKind(), section.getThreatId()));
            transitions.findBySceneIdOrderBySortOrderAsc(scene.getId()).forEach(transition ->
                    append(text, transition.getKind(), transition.getLabel(), transition.getExternalDestination(),
                            transition.getCondition(), transition.getDmNote(), transition.getSourceLocator(),
                            transition.getTargetScene() == null ? null : transition.getTargetScene().getTitle()));
            participants.findBySceneIdOrderBySortOrderAsc(scene.getId()).forEach(participant -> {
                append(text, participant.getDisplayName(), participant.getQuantity(), participant.getDisposition(),
                        participant.getPlacementHint(), participant.getSourceLocator());
                if (participant.getStatBlock() != null) {
                    statBlocks.findById(participant.getStatBlock().getId()).ifPresent(block -> append(text,
                            block.getName(), block.getSource(), block.getCr(), block.getType(), block.getSize(),
                            block.getAlignment(), block.getAc(), block.getHp(), block.getSpeed(), block.getSkills(),
                            block.getSenses(), block.getLanguages(), block.getTraits(), block.getActions(),
                            block.getBonusActions(), block.getReactions(), block.getSourceKey()));
                }
            });
        });

        statBlocks.findByCampaignIdOrderByNameAsc(seeded.campaignId()).forEach(block -> append(text,
                block.getName(), block.getSource(), block.getCr(), block.getType(), block.getSize(), block.getAlignment(),
                block.getAc(), block.getHp(), block.getSpeed(), block.getSkills(), block.getSenses(), block.getLanguages(),
                block.getTraits(), block.getActions(), block.getBonusActions(), block.getReactions(), block.getSourceKey()));
        mapRepository.findByCampaignIdOrderBySortOrderAsc(seeded.campaignId()).forEach(map -> append(text,
                map.getName(), map.getGridWidth(), map.getGridHeight(), map.getCellSizePx(), map.getSortOrder(),
                map.getGridType(), map.getMovementMode(), map.isShowGrid(), map.getDocument()));
        handoutRepository.findByCampaignIdOrderByTitleAsc(seeded.campaignId()).forEach(handout -> append(text,
                handout.getTitle(), handout.getTags(), handout.getFileName(), handout.getContentType(),
                handout.getSafetyClassification(), handout.getAssetKind(), handout.isDmOnly(), handout.isPresented(),
                handout.getDerivativeRecipe(), handout.getSourceHandout() == null ? null : handout.getSourceHandout().getId()));
        quests.getQuest(seeded.campaignId(), seeded.questId()).getObjectives().forEach(objective -> append(text,
                objective.getTitle(), objective.getDescription(), objective.getStatus(), objective.getCompletionMode(),
                objective.getSourceLocator()));
        partyMembers.findByCampaignIdOrderByCharacterNameAsc(seeded.campaignId()).forEach(member -> append(text,
                member.getCharacterName(), member.getPlayerName(), member.getClassAndLevel(), member.getAc(), member.getMaxHp(),
                member.getCurrentHp(), member.getInitiativeBonus(), member.getSpeed(), member.getPassivePerception(),
                member.getPassiveInsight(), member.getPassiveInvestigation(), member.getNotes()));
        var quest = quests.getQuest(seeded.campaignId(), seeded.questId());
        append(text, quest.getTitle(), quest.getStatus(), quest.getSummary(), quest.getSourceLocator(), quest.getTags(),
                quest.getRewards(), quest.getPrerequisites(), quest.getOutcomeNotes(), seeded.questId());
        encounterRepository.findByCampaignIdOrderByNameAsc(seeded.campaignId()).forEach(encounter -> {
            append(text, encounter.getName(), encounter.getStatus(), encounter.getCombatPhase(), encounter.getEncounterKey(),
                    encounter.getPrepJson(), encounter.getRewardsJson(), encounter.getLairActionName(),
                    encounter.getLairActionDescription(), encounter.getRound(), encounter.getActiveTurnIndex(),
                    encounter.getLogSequence(), encounter.isLairActionTriggered(), encounter.getVictoryCueDurationSeconds(),
                    encounter.getMap() == null ? null : encounter.getMap().getName(),
                    encounter.getCombatAudioCue() == null ? null : encounter.getCombatAudioCue().getName(),
                    encounter.getVictoryAudioCue() == null ? null : encounter.getVictoryAudioCue().getName());
            encounterWaveRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()).forEach(wave -> append(text,
                    wave.getWaveKey(), wave.getName(), wave.getSortOrder(), wave.getStatus(), wave.getTriggerKind(),
                    wave.getTriggerValue(), wave.getNotes(),
                    combatantRepository.countByWaveId(wave.getId())));
            combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()).forEach(combatant -> {
                append(text, combatant.getName(), combatant.getInitiative(), combatant.getSortOrder(), combatant.getMaxHp(),
                        combatant.getTieBreaker(), combatant.getCurrentHp(), combatant.getTempHp(), combatant.getKind(),
                        combatant.getGroupId(), combatant.isGroupLeader(), combatant.isDefeated(), combatant.isHidden(),
                        combatant.getConditionsJson(), combatant.getConcentratingOn(), combatant.isConcentrationCheckPending(),
                        combatant.getLegendaryActionsUsed(), combatant.getLegendaryActionsMax(),
                        combatant.getLegendaryResistancesUsed(), combatant.getLegendaryResistancesMax(),
                        combatant.getRechargedAbilities(), combatant.getNotes(),
                        combatant.getStartX(), combatant.getStartY(), combatant.getPlacementRegionKey(),
                        combatant.getThreatKind(), combatant.getThreatId());
                if (combatant.getWave() != null) {
                    append(text, combatant.getWave().getWaveKey(), combatant.getWave().getName(),
                            combatant.getWave().getStatus(), combatant.getWave().getTriggerKind(),
                            combatant.getWave().getTriggerValue(), combatant.getWave().getNotes());
                }
                if (combatant.getStatBlock() != null) {
                    statBlocks.findById(combatant.getStatBlock().getId()).ifPresent(block -> append(text,
                            block.getName(), block.getSource(), block.getCr(), block.getType(), block.getAc(), block.getHp()));
                }
            });
        });
        return text.toString();
    }

    private static void append(StringBuilder text, Object... values) {
        for (Object value : values) if (value != null) text.append(value).append('\n');
    }
}
