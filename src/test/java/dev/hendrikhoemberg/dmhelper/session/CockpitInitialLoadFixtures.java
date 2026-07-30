package dev.hendrikhoemberg.dmhelper.session;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Combatant;
import dev.hendrikhoemberg.dmhelper.encounter.data.CombatantRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Builds the smallest campaign that renders a full cockpit with a workspace map. */
@Component
public class CockpitInitialLoadFixtures {

    private final CampaignRepository campaigns;
    private final GameMapRepository maps;
    private final SessionLifecycleService lifecycle;
    private final AdventureRepository adventures;
    private final ChapterRepository chapters;
    private final SceneRepository scenes;
    private final AdventureService adventureService;
    private final EncounterRepository encounters;
    private final CombatantRepository combatants;
    private final PartyMemberRepository partyMembers;
    private final StatBlockRepository statBlocks;

    public CockpitInitialLoadFixtures(CampaignRepository campaigns,
                                      GameMapRepository maps,
                                      SessionLifecycleService lifecycle,
                                      AdventureRepository adventures,
                                      ChapterRepository chapters,
                                      SceneRepository scenes,
                                      AdventureService adventureService,
                                      EncounterRepository encounters,
                                      CombatantRepository combatants,
                                      PartyMemberRepository partyMembers,
                                      StatBlockRepository statBlocks) {
        this.campaigns = campaigns;
        this.maps = maps;
        this.lifecycle = lifecycle;
        this.adventures = adventures;
        this.chapters = chapters;
        this.scenes = scenes;
        this.adventureService = adventureService;
        this.encounters = encounters;
        this.combatants = combatants;
        this.partyMembers = partyMembers;
        this.statBlocks = statBlocks;
    }

    @Transactional
    public UUID campaignWithRunningSession() {
        Campaign campaign = new Campaign();
        campaign.setName("Initial Load Fixture");
        campaigns.save(campaign);

        GameMap map = new GameMap();
        map.setCampaign(campaign);
        map.setName("Fixture Map");
        maps.save(map);

        Adventure adv = new Adventure();
        adv.setCampaign(campaign);
        adv.setName("Fixture Adventure");
        adventures.save(adv);

        Chapter ch = new Chapter();
        ch.setAdventure(adv);
        ch.setTitle("Chapter 1");
        ch = chapters.save(ch);

        Scene scene = new Scene();
        scene.setChapter(ch);
        scene.setTitle("Crypt Door");
        scene.setBody("The stone door looms before you.");
        scene.setSortOrder(1);
        scene.setMap(map);
        scenes.save(scene);

        adventureService.setCurrentScene(campaign.getId(), scene.getId());

        lifecycle.start(campaign.getId(), map.getId());
        return campaign.getId();
    }

    @Transactional
    public UUID plannedEncounterWithOneCombatant(UUID campaignId) {
        Campaign campaign = campaigns.findById(campaignId).orElseThrow();
        GameMap map = maps.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst();
        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setMap(map);
        encounter.setName("Reactivation Fixture");
        encounter.setStatus(Encounter.Status.PLANNED);
        encounters.save(encounter);

        Combatant goblin = new Combatant();
        goblin.setEncounter(encounter);
        goblin.setName("Goblin");
        goblin.setMaxHp(7);
        goblin.setCurrentHp(7);
        combatants.save(goblin);
        return encounter.getId();
    }

    @Transactional
    public UUID campaignWithRunningSessionAndFourPartyMembers() {
        UUID campaignId = campaignWithRunningSession();
        Campaign campaign = campaigns.findById(campaignId).orElseThrow();
        for (String name : new String[] {"Nym", "Lyra", "Grimm", "Thorin"}) {
            PartyMember member = new PartyMember();
            member.setCampaign(campaign);
            member.setCharacterName(name);
            member.setAc(14);
            member.setMaxHp(10);
            member.setCurrentHp(10);
            member.setPassivePerception(12);
            partyMembers.save(member);
        }
        return campaignId;
    }

    public record TwoEncounters(UUID campaignId, UUID mapA, UUID mapB, UUID encounterA, UUID encounterB) {}
    public record GroupedEncounter(UUID campaignId, UUID mapId, UUID encounterId, String groupId, List<UUID> memberIds) {}

    @Transactional
    public TwoEncounters campaignWithTwoEncountersOnTwoMaps() {
        UUID campaignId = campaignWithRunningSession();
        Campaign campaign = campaigns.findById(campaignId).orElseThrow();

        GameMap mapA = maps.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst();

        GameMap mapB = new GameMap();
        mapB.setCampaign(campaign);
        mapB.setName("Second Map");
        maps.save(mapB);

        Encounter encounterA = new Encounter();
        encounterA.setCampaign(campaign);
        encounterA.setMap(mapA);
        encounterA.setName("First Encounter");
        encounterA.setStatus(Encounter.Status.PLANNED);
        encounters.save(encounterA);

        Combatant goblin1 = new Combatant();
        goblin1.setEncounter(encounterA);
        goblin1.setName("Goblin 1");
        goblin1.setMaxHp(7);
        goblin1.setCurrentHp(7);
        combatants.save(goblin1);

        Combatant goblin2 = new Combatant();
        goblin2.setEncounter(encounterA);
        goblin2.setName("Goblin 2");
        goblin2.setMaxHp(7);
        goblin2.setCurrentHp(7);
        combatants.save(goblin2);

        Encounter encounterB = new Encounter();
        encounterB.setCampaign(campaign);
        encounterB.setMap(mapB);
        encounterB.setName("Second Encounter");
        encounterB.setStatus(Encounter.Status.PLANNED);
        encounters.save(encounterB);

        Combatant hobgoblin1 = new Combatant();
        hobgoblin1.setEncounter(encounterB);
        hobgoblin1.setName("Hobgoblin 1");
        hobgoblin1.setMaxHp(11);
        hobgoblin1.setCurrentHp(11);
        combatants.save(hobgoblin1);

        Combatant hobgoblin2 = new Combatant();
        hobgoblin2.setEncounter(encounterB);
        hobgoblin2.setName("Hobgoblin 2");
        hobgoblin2.setMaxHp(11);
        hobgoblin2.setCurrentHp(11);
        combatants.save(hobgoblin2);

        return new TwoEncounters(campaignId, mapA.getId(), mapB.getId(), encounterA.getId(), encounterB.getId());
    }

    @Transactional
    public GroupedEncounter campaignWithGroupedEncounter() {
        UUID campaignId = campaignWithRunningSessionAndFourPartyMembers();
        Campaign campaign = campaigns.findById(campaignId).orElseThrow();

        StatBlock goblinSb = new StatBlock();
        goblinSb.setSource(ContentSource.CUSTOM);
        goblinSb.setName("Goblin");
        goblinSb.setCr("1/4");
        goblinSb.setType("humanoid");
        goblinSb.setAc(15);
        goblinSb.setHp("7 (2d6)");
        goblinSb.setXp(50);
        statBlocks.save(goblinSb);

        GameMap map = maps.findByCampaignIdOrderBySortOrderAsc(campaignId).getFirst();

        Encounter encounter = new Encounter();
        encounter.setCampaign(campaign);
        encounter.setMap(map);
        encounter.setName("Grouped Encounter");
        encounter.setStatus(Encounter.Status.ACTIVE);
        encounter.setCombatPhase(Encounter.CombatPhase.RUNNING);
        encounter.setRound(1);
        encounters.save(encounter);

        List<PartyMember> members = partyMembers.findByCampaignIdAndActiveTrueOrderByCharacterNameAsc(campaignId);

        int initiative = 20;
        for (PartyMember member : members) {
            Combatant pc = new Combatant();
            pc.setEncounter(encounter);
            pc.setPartyMember(member);
            pc.setName(member.getCharacterName());
            pc.setKind("PC");
            pc.setInitiative(initiative);
            pc.setMaxHp(member.getMaxHp());
            pc.setCurrentHp(member.getCurrentHp());
            pc.setSortOrder(0);
            combatants.save(pc);
            initiative--;
        }

        String groupId = UUID.randomUUID().toString();
        List<UUID> memberIds = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            Combatant goblin = new Combatant();
            goblin.setEncounter(encounter);
            goblin.setName("Goblin " + i);
            goblin.setKind("MONSTER");
            goblin.setStatBlock(goblinSb);
            goblin.setGroupId(groupId);
            goblin.setGroupLeader(i == 1);
            goblin.setMaxHp(7);
            goblin.setCurrentHp(7);
            goblin.setSortOrder(i);
            combatants.save(goblin);
            memberIds.add(goblin.getId());
        }

        return new GroupedEncounter(campaignId, map.getId(), encounter.getId(), groupId, memberIds);
    }
}
