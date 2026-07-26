package dev.hendrikhoemberg.dmhelper.support;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterPrep;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterRewards;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CombatantCreateRequest;
import dev.hendrikhoemberg.dmhelper.encounter.service.EncounterService.CreateRequest;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService;
import dev.hendrikhoemberg.dmhelper.party.service.PartyMemberService.PartyLiveStateDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * A campaign shaped like a prepared session: a roster with live state worth scanning and one
 * PLANNED encounter with combatants, preparation notes and rewards. Everything is invented —
 * no published-campaign content may enter this file.
 */
@Component
public class PreparationSurfaceFixture {

    public record Seeded(
            UUID campaignId,
            UUID adventureId,
            UUID chapterId,
            UUID sceneId,
            UUID encounterId,
            UUID woundedMemberId,
            UUID inactiveMemberId) {}

    public static final String WOUNDED_MEMBER = "Aral Quickfoot";
    public static final String WOUNDED_PLAYER = "Sam";
    public static final String HEALTHY_MEMBER = "Bryn Stonehand";
    public static final String THIRD_MEMBER = "Cora Vale";
    public static final String INACTIVE_MEMBER = "Dain Underbough";
    public static final String CONDITION_NAME = "poisoned";

    public static final String ENCOUNTER_NAME = "Gate Watch Ambush";
    public static final String MONSTER_NAME = "Hooded Ambusher";
    public static final String PREP_TACTICS =
            "The ambushers loose one volley from the gatehouse roof, then drop to the courtyard.";
    public static final String PREP_MORALE = "They break once two of them are down.";
    public static final String PREP_ENVIRONMENT = "Dim light, wet cobbles, 10-foot gatehouse roof.";
    public static final String PREP_SCENE_KEY = "GW1";
    public static final int REWARD_XP_TOTAL = 450;

    private final CampaignRepository campaigns;
    private final AdventureService adventures;
    private final PartyMemberService party;
    private final EncounterService encounters;

    public PreparationSurfaceFixture(CampaignRepository campaigns,
                                     AdventureService adventures,
                                     PartyMemberService party,
                                     EncounterService encounters) {
        this.campaigns = campaigns;
        this.adventures = adventures;
        this.party = party;
        this.encounters = encounters;
    }

    @Transactional
    public Seeded seed() {
        Campaign campaign = new Campaign();
        campaign.setName("Preparation Surface Fixture");
        campaign.setDescription("Roster and prepared encounter for surface tests");
        UUID campaignId = campaigns.save(campaign).getId();

        Adventure adventure = adventures.createAdventure(
                campaignId, "The Gate Watch", "Single-chapter fixture", "Fixture, p. 1");
        Chapter chapter = adventures.createChapter(
                adventure.getId(), "Chapter One", "Opening chapter.");
        Scene scene = adventures.createScene(
                chapter.getId(), "The Gatehouse", PREP_SCENE_KEY, "A squat stone gatehouse.");

        PartyMember wounded = party.create(campaignId, WOUNDED_MEMBER, WOUNDED_PLAYER,
                "Rogue 3", 15, 24, 4, 30, 14, 12, 13, null);
        party.updateLiveState(wounded.getId(), new PartyLiveStateDto(
                0, false, 0, 0, 0, null, "[\"" + CONDITION_NAME + "\"]", 9, 24));

        PartyMember healthy = party.create(campaignId, HEALTHY_MEMBER, "Kim",
                "Fighter 3", 18, 30, 1, 30, 12, 11, 10, null);
        party.updateLiveState(healthy.getId(), new PartyLiveStateDto(
                5, false, 0, 0, 0, null, "[]", 30, 30));

        party.create(campaignId, THIRD_MEMBER, "Jo",
                "Cleric 3", 16, 22, 2, 30, 13, 15, 11, null);

        PartyMember inactive = party.create(campaignId, INACTIVE_MEMBER, "Lee",
                "Wizard 2", 12, 14, 2, 30, 11, 10, 16, null);
        party.setActive(inactive.getId(), false);

        UUID encounterId = encounters.create(campaignId, new CreateRequest(ENCOUNTER_NAME, null)).id();
        encounters.addCombatant(encounterId,
                new CombatantCreateRequest(MONSTER_NAME + " A", 16, "MONSTER", null, null, null));
        encounters.addCombatant(encounterId,
                new CombatantCreateRequest(MONSTER_NAME + " B", 16, "MONSTER", null, null, null));
        encounters.addCombatant(encounterId,
                new CombatantCreateRequest(WOUNDED_MEMBER, 24, "PC", null, null, wounded.getId()));

        encounters.updatePrep(encounterId, new EncounterPrep(
                PREP_TACTICS, PREP_MORALE, "They surrender if surrounded.",
                PREP_ENVIRONMENT, "Fixture, p. 4", "Add one ambusher per extra character.",
                PREP_SCENE_KEY));
        encounters.updateRewards(encounterId, new EncounterRewards(
                REWARD_XP_TOTAL, 150, List.of(), List.of(), List.of(),
                "Split the purse between the watch and the party."));

        return new Seeded(campaignId, adventure.getId(), chapter.getId(), scene.getId(),
                encounterId, wounded.getId(), inactive.getId());
    }
}
