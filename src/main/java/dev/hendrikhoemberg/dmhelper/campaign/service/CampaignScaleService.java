package dev.hendrikhoemberg.dmhelper.campaign.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.quest.data.QuestRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import dev.hendrikhoemberg.dmhelper.world.data.FactionRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldNpcRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CampaignScaleService {

    public record CampaignScale(
            long adventures, long chapters, long scenes, long quests,
            long npcs, long locations, long factions,
            long traps, long hazards, long tables,
            long handouts, long maps, long notes) {}

    private final AdventureRepository adventures;
    private final ChapterRepository chapters;
    private final SceneRepository scenes;
    private final QuestRepository quests;
    private final WorldNpcRepository npcs;
    private final WorldLocationRepository locations;
    private final FactionRepository factions;
    private final TrapRepository traps;
    private final HazardRepository hazards;
    private final RollableTableRepository tables;
    private final HandoutRepository handouts;
    private final GameMapRepository maps;
    private final NoteRepository notes;

    public CampaignScaleService(AdventureRepository adventures, ChapterRepository chapters,
                                SceneRepository scenes, QuestRepository quests,
                                WorldNpcRepository npcs, WorldLocationRepository locations,
                                FactionRepository factions, TrapRepository traps,
                                HazardRepository hazards, RollableTableRepository tables,
                                HandoutRepository handouts, GameMapRepository maps,
                                NoteRepository notes) {
        this.adventures = adventures;
        this.chapters = chapters;
        this.scenes = scenes;
        this.quests = quests;
        this.npcs = npcs;
        this.locations = locations;
        this.factions = factions;
        this.traps = traps;
        this.hazards = hazards;
        this.tables = tables;
        this.handouts = handouts;
        this.maps = maps;
        this.notes = notes;
    }

    @Transactional(readOnly = true)
    public CampaignScale scaleOf(UUID campaignId) {
        return new CampaignScale(
                adventures.countByCampaignId(campaignId),
                chapters.countByAdventureCampaignId(campaignId),
                scenes.countByChapterAdventureCampaignId(campaignId),
                quests.countByCampaignId(campaignId),
                npcs.countByCampaignId(campaignId),
                locations.countByCampaignId(campaignId),
                factions.countByCampaignId(campaignId),
                traps.countByCampaignId(campaignId),
                hazards.countByCampaignId(campaignId),
                tables.countByCampaignId(campaignId),
                handouts.countByCampaignId(campaignId),
                maps.countByCampaignId(campaignId),
                notes.countByCampaignId(campaignId));
    }
}
