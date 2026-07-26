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

    public enum Shape { LINEAR_ONE_MAP }

    public record Seeded(UUID campaignId, UUID adventureId, UUID hostileSceneId,
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
        this.combatantRepository = combatantRepository;
    }

    @Transactional
    public Seeded seed() throws IOException { return seed(Shape.LINEAR_ONE_MAP); }

    @Transactional
    public Seeded seed(Shape shape) throws IOException {
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

        List<StatBlock> foeBlocks = List.of(
                statBlock(campaign, "Bog Sentinel", "1/2", 15, "18 (4d8)", 2),
                statBlock(campaign, "Bog Skirmisher", "1/4", 13, "11 (2d8)", 3),
                statBlock(campaign, "Bog Skirmisher", "1/4", 13, "11 (2d8)", 3),
                statBlock(campaign, "Marsh Warden", "2", 16, "30 (4d10+8)", 1));
        String[] names = {"Sentinel at the sluice", "Skirmisher by the steps",
                "Skirmisher in the reeds", "Warden of the bell"};
        for (int i = 0; i < foeBlocks.size(); i++) {
            var participant = structured.addParticipant(campaignId, hostile.getId(), new SceneParticipantCommand(
                    names[i], 1, SceneParticipantDisposition.HOSTILE, "Undercroft position " + (i + 1),
                    foeBlocks.get(i).getId(), null, "Synthetic, A2", i));
            participant.setStatBlock(foeBlocks.get(i));
            participants.save(participant);
        }

        var map = maps.create(campaignId, "Beacon Undercroft", 20, 15, 64);
        maps.updateMode(map.getId(), "GRID", true);
        hostile.setMapRequirement(SceneMapRequirement.REQUIRED);
        hostile.setMap(map);
        scenes.save(hostile);
        var encounter = encounters.create(campaignId, new EncounterService.CreateRequest(
                "Undercroft Alarm", map.getId()));
        adventures.linkEncounter(hostile.getId(), encounter.id());
        for (StatBlock foe : foeBlocks) {
            encounters.addCombatant(encounter.id(), new EncounterService.CombatantCreateRequest(
                    foe.getName(), 0, "MONSTER", null, foe.getId(), null));
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

        List<UUID> partyIds = new ArrayList<>();
        partyIds.add(party(campaign, "Ilsa Fenwright", 18, 34, 14));
        partyIds.add(party(campaign, "Ordo Brack", 14, 27, 11));
        partyIds.add(party(campaign, "Nesh Vell", 12, 21, 16));
        partyIds.add(party(campaign, "Tamsin Aroe", 15, 25, 13));
        return new Seeded(campaignId, adventure.getId(), hostile.getId(), branch.getId(), map.getId(),
                playerSafe.getId(), dmSource.getId(), derivative.getId(), quest.getId(), partyIds);
    }

    private StatBlock statBlock(Campaign campaign, String name, String cr, int ac, String hp, int initiativeBonus) {
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
        mapRepository.findById(seeded.playableMapId()).ifPresent(map -> append(text, map.getName(), map.getGridWidth(),
                map.getGridHeight(), map.getCellSizePx(), map.getGridType(), map.getMovementMode(), map.isShowGrid(), map.getDocument()));
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
                    encounter.isLairActionTriggered(), encounter.getMap() == null ? null : encounter.getMap().getName());
            combatantRepository.findByEncounterIdOrderBySortOrderAsc(encounter.getId()).forEach(combatant -> {
                append(text, combatant.getName(), combatant.getInitiative(), combatant.getSortOrder(), combatant.getMaxHp(),
                        combatant.getCurrentHp(), combatant.getTempHp(), combatant.getKind(), combatant.getGroupId(),
                        combatant.isGroupLeader(), combatant.isDefeated(), combatant.isHidden(), combatant.getConditionsJson(),
                        combatant.getConcentratingOn(), combatant.getRechargedAbilities(), combatant.getNotes(),
                        combatant.getStartX(), combatant.getStartY(), combatant.getPlacementRegionKey(),
                        combatant.getThreatKind(), combatant.getThreatId());
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
