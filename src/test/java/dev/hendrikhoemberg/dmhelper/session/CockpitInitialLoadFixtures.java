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
import dev.hendrikhoemberg.dmhelper.session.service.SessionLifecycleService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    public CockpitInitialLoadFixtures(CampaignRepository campaigns,
                                      GameMapRepository maps,
                                      SessionLifecycleService lifecycle,
                                      AdventureRepository adventures,
                                      ChapterRepository chapters,
                                      SceneRepository scenes,
                                      AdventureService adventureService,
                                      EncounterRepository encounters,
                                      CombatantRepository combatants,
                                      PartyMemberRepository partyMembers) {
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
}
