package dev.hendrikhoemberg.dmhelper.campaign.readiness;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneMapRequirement;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantDisposition;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantRepository;
import dev.hendrikhoemberg.dmhelper.adventure.service.AdventureService;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMember;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class BulkSeedFixtures {

    private final CampaignRepository campaigns;
    private final AdventureService adventures;
    private final StatBlockRepository statBlocks;
    private final SceneParticipantRepository participants;
    private final PartyMemberRepository partyMembers;

    public BulkSeedFixtures(CampaignRepository campaigns,
                            AdventureService adventures,
                            StatBlockRepository statBlocks,
                            SceneParticipantRepository participants,
                            PartyMemberRepository partyMembers) {
        this.campaigns = campaigns;
        this.adventures = adventures;
        this.statBlocks = statBlocks;
        this.participants = participants;
        this.partyMembers = partyMembers;
    }

    @Transactional
    public UUID campaignWithThreeSeedableScenes() {
        Campaign campaign = new Campaign();
        campaign.setName("Bulk Seed Fixture");
        campaigns.save(campaign);

        PartyMember hero = new PartyMember();
        hero.setCampaign(campaign);
        hero.setCharacterName("Fixture Hero");
        hero.setActive(true);
        hero.setAc(14);
        hero.setMaxHp(12);
        hero.setCurrentHp(12);
        partyMembers.save(hero);

        StatBlock goblin = new StatBlock();
        goblin.setSource(ContentSource.CUSTOM);
        goblin.setCampaign(campaign);
        goblin.setSourceKey("bulk-seed-goblin");
        goblin.setName("Bulk Seed Goblin");
        goblin.setCr("1/4");
        goblin.setType("Humanoid");
        goblin.setAc(15);
        goblin.setHp("7 (2d6)");
        goblin.setSpeed("30 ft.");
        goblin.setDexScore(14);
        statBlocks.save(goblin);

        Adventure adventure = adventures.createAdventure(
                campaign.getId(), "Bulk Seed Adventure", null, null);
        Chapter chapter = adventures.createChapter(adventure.getId(), "Chapter 1", null);

        for (int i = 1; i <= 3; i++) {
            Scene scene = adventures.createScene(
                    chapter.getId(), "Seedable Scene " + i, "seedable-" + i, null);
            scene.setMapRequirement(SceneMapRequirement.NONE);

            SceneParticipant participant = new SceneParticipant();
            participant.setScene(scene);
            participant.setDisplayName("Goblin group " + i);
            participant.setQuantity(i);
            participant.setDisposition(SceneParticipantDisposition.HOSTILE);
            participant.setStatBlock(goblin);
            participant.setSortOrder(0);
            participants.save(participant);
            scene.getParticipants().add(participant);
        }
        return campaign.getId();
    }
}
