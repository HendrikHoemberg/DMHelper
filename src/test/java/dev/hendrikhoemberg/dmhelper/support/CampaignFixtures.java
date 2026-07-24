package dev.hendrikhoemberg.dmhelper.support;

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
import dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessAcknowledgement;
import dev.hendrikhoemberg.dmhelper.campaign.readiness.ReadinessAcknowledgementRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class CampaignFixtures {

    private final CampaignRepository campaigns;
    private final AdventureService adventures;
    private final HandoutRepository handouts;
    private final StatBlockRepository statBlocks;
    private final GameMapRepository maps;
    private final ReadinessAcknowledgementRepository acknowledgements;
    private final SceneParticipantRepository participants;
    private final EntityManager em;

    public CampaignFixtures(CampaignRepository campaigns,
                            AdventureService adventures,
                            HandoutRepository handouts,
                            StatBlockRepository statBlocks,
                            GameMapRepository maps,
                            ReadinessAcknowledgementRepository acknowledgements,
                            SceneParticipantRepository participants,
                            EntityManager em) {
        this.campaigns = campaigns;
        this.adventures = adventures;
        this.handouts = handouts;
        this.statBlocks = statBlocks;
        this.maps = maps;
        this.acknowledgements = acknowledgements;
        this.participants = participants;
        this.em = em;
    }

    @Transactional
    public UUID operationalFixtureNotReady() {
        Campaign campaign = new Campaign();
        campaign.setName("Operational Not Ready Fixture");
        campaign.setDescription("Hostile scene without statblock, required map missing, unsafe handout");
        UUID cid = campaigns.save(campaign).getId();

        Adventure adventure = adventures.createAdventure(cid, "Test Adventure", null, null);
        Chapter chapter = adventures.createChapter(adventure.getId(), "Chapter 1", null);

        Scene hostileScene = adventures.createScene(chapter.getId(), "Ambush Encounter", null, null);
        SceneParticipant hostile = new SceneParticipant();
        hostile.setScene(hostileScene);
        hostile.setDisplayName("Ambush Brute");
        hostile.setQuantity(1);
        hostile.setDisposition(SceneParticipantDisposition.HOSTILE);
        hostile.setSortOrder(0);
        participants.save(hostile);
        hostileScene.getParticipants().add(hostile);

        Scene mapScene = adventures.createScene(chapter.getId(), "The Dark Cave", null, null);
        mapScene.setMapRequirement(SceneMapRequirement.REQUIRED);

        Campaign campaignRef = campaigns.findById(cid).orElseThrow();
        Handout handout = new Handout();
        handout.setCampaign(campaignRef);
        handout.setTitle("Unsafe Handout");
        handout.setFileName(UUID.randomUUID() + ".png");
        handout.setContentType("image/png");
        handout.setSafetyClassification(Handout.SafetyClassification.UNREVIEWED);
        handout.setAssetKind(Handout.AssetKind.SOURCE_PAGE);
        handout.setPresented(true);
        handouts.save(handout);

        em.flush();
        return cid;
    }

    @Transactional
    public UUID operationalFixtureReadyAfterRepair() {
        Campaign campaign = new Campaign();
        campaign.setName("Operational Ready Fixture");
        campaign.setDescription("All blockers resolved or acknowledged");
        UUID cid = campaigns.save(campaign).getId();

        StatBlock statblock = new StatBlock();
        statblock.setSource(ContentSource.CUSTOM);
        statblock.setName("Ambush Brute");
        statblock.setCr("1");
        statblock.setType("Humanoid");
        statblock.setAc(14);
        statblock.setHp("16 (3d8+3)");
        statblock = statBlocks.save(statblock);

        Campaign campaignRef = campaigns.findById(cid).orElseThrow();
        GameMap gameMap = new GameMap();
        gameMap.setCampaign(campaignRef);
        gameMap.setName("Cave Map");
        maps.save(gameMap);

        Adventure adventure = adventures.createAdventure(cid, "Test Adventure", null, null);
        Chapter chapter = adventures.createChapter(adventure.getId(), "Chapter 1", null);

        Scene hostileScene = adventures.createScene(chapter.getId(), "Ambush Encounter", null, null);
        hostileScene.setMapRequirement(SceneMapRequirement.NONE);
        SceneParticipant hostile = new SceneParticipant();
        hostile.setScene(hostileScene);
        hostile.setDisplayName("Ambush Brute");
        hostile.setQuantity(1);
        hostile.setDisposition(SceneParticipantDisposition.HOSTILE);
        hostile.setStatBlock(statblock);
        hostile.setSortOrder(0);
        participants.save(hostile);
        hostileScene.getParticipants().add(hostile);

        Scene mapScene = adventures.createScene(chapter.getId(), "The Dark Cave", null, null);
        mapScene.setMapRequirement(SceneMapRequirement.REQUIRED);
        mapScene.setMap(gameMap);

        Handout handout = new Handout();
        handout.setCampaign(campaignRef);
        handout.setTitle("Safe Handout");
        handout.setFileName(UUID.randomUUID() + ".png");
        handout.setContentType("image/png");
        handout.setSafetyClassification(Handout.SafetyClassification.PLAYER_SAFE);
        handout.setAssetKind(Handout.AssetKind.SOURCE_PAGE);
        handout.setPresented(true);
        handouts.save(handout);

        ReadinessAcknowledgement ack = new ReadinessAcknowledgement();
        ack.setCampaignId(cid);
        ack.setItemKey("omission:converter");
        ack.setAcceptedAt(Instant.now());
        acknowledgements.save(ack);

        em.flush();
        return cid;
    }
}
