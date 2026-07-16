package dev.hendrikhoemberg.dmhelper.adventure.packagev2;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneStatus;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AdventureDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ChapterDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.encounter.data.Encounter;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMap;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class AdventureSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final AdventureRepository adventureRepo;
    private final ChapterRepository chapterRepo;
    private final SceneRepository sceneRepo;

    public AdventureSectionAdapter(AdventureRepository adventureRepo,
                                    ChapterRepository chapterRepo,
                                    SceneRepository sceneRepo) {
        this.adventureRepo = adventureRepo;
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
    }

    @Override
    public String sectionName() {
        return "Adventure";
    }

    @Override
    public int order() {
        return 900;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        List<Adventure> adventures = adventureRepo.findByCampaignIdOrderBySortOrderAscIdAsc(context.campaignId());

        List<AdventureDto> adventureDtos = adventures.stream()
                .map(adv -> {
                    String advKey = context.key(CampaignContentType.ADVENTURE, adv.getId(), adv.getName());

                    List<Chapter> chapters = chapterRepo.findByAdventureIdOrderBySortOrderAscIdAsc(adv.getId());
                    List<ChapterDto> chapterDtos = chapters.stream()
                            .map(ch -> {
                                String chKey = context.key(CampaignContentType.CHAPTER, ch.getId(), ch.getTitle());

                                List<Scene> scenes = sceneRepo.findByChapterIdOrderBySortOrderAscIdAsc(ch.getId());
                                List<SceneDto> sceneDtos = scenes.stream()
                                        .map(sc -> {
                                            String scKey = context.key(CampaignContentType.SCENE, sc.getId(), sc.getTitle());
                                            Map<String, Integer> pin = null;
                                            if (sc.getPinX() != null) {
                                                pin = Map.of("x", sc.getPinX(), "y", sc.getPinY());
                                            }
                                            ContentReference mapRef = sc.getMap() != null
                                                    ? context.packageRef(CampaignContentType.MAP, sc.getMap().getId(), sc.getMap().getName())
                                                    : null;
                                            ContentReference encounterRef = sc.getEncounter() != null
                                                    ? context.packageRef(CampaignContentType.ENCOUNTER, sc.getEncounter().getId(), sc.getEncounter().getName())
                                                    : null;
                                            List<ContentReference> statblockRefs = sc.getStatBlocks().stream()
                                                    .map(sb -> context.packageRef(CampaignContentType.STATBLOCK, sb.getId(), sb.getName()))
                                                    .toList();
                                            List<ContentReference> handoutRefs = sc.getHandouts().stream()
                                                    .map(h -> context.packageRef(CampaignContentType.HANDOUT, h.getId(), h.getTitle()))
                                                    .toList();
                                            return new SceneDto(
                                                    scKey, sc.getTitle(), sc.getBody(),
                                                    sc.getStatus().name(), sc.getSortOrder(),
                                                    mapRef, pin, encounterRef,
                                                    statblockRefs, handoutRefs
                                            );
                                        }).toList();
                                return new ChapterDto(chKey, ch.getTitle(), ch.getIntro(), ch.getSortOrder(), sceneDtos);
                            }).toList();
                    return new AdventureDto(advKey, adv.getName(), adv.getDescription(),
                            adv.getSourceAttribution(), adv.getSortOrder(), chapterDtos, adv.getCreatedAt());
                }).toList();

        target.adventures(adventureDtos);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<AdventureDto> adventureDtos = source.adventures();
        if (adventureDtos == null) return;

        var campaign = context.campaign();

        for (AdventureDto advDto : adventureDtos) {
            Adventure adv = new Adventure();
            adv.setCampaign(campaign);
            adv.setName(advDto.name());
            adv.setDescription(advDto.description());
            adv.setSourceAttribution(advDto.sourceAttribution());
            adv.setSortOrder(advDto.sortOrder());
            if (advDto.createdAt() != null) {
                adv.setCreatedAt(advDto.createdAt());
            }
            adventureRepo.save(adv);
            context.register(CampaignContentType.ADVENTURE, advDto.key(), adv, adv.getId());

            if (advDto.chapters() == null) continue;

            for (ChapterDto chDto : advDto.chapters()) {
                Chapter ch = new Chapter();
                ch.setAdventure(adv);
                ch.setTitle(chDto.title());
                ch.setIntro(chDto.intro());
                ch.setSortOrder(chDto.sortOrder());
                chapterRepo.save(ch);
                context.register(CampaignContentType.CHAPTER, chDto.key(), ch, ch.getId());

                if (chDto.scenes() == null) continue;

                for (SceneDto scDto : chDto.scenes()) {
                    Scene sc = new Scene();
                    sc.setChapter(ch);
                    sc.setTitle(scDto.title());
                    sc.setBody(scDto.body());
                    sc.setStatus(scDto.status() != null
                            ? SceneStatus.valueOf(scDto.status())
                            : SceneStatus.UNVISITED);
                    sc.setSortOrder(scDto.sortOrder());
                    if (scDto.pin() != null) {
                        sc.setPinX(scDto.pin().get("x"));
                        sc.setPinY(scDto.pin().get("y"));
                    }
                    sceneRepo.save(sc);
                    context.register(CampaignContentType.SCENE, scDto.key(), sc, sc.getId());

                    context.defer("scene-relations:" + scDto.key(), () -> {
                        Scene scene = context.require(
                                ContentReference.packageRef(CampaignContentType.SCENE, scDto.key()),
                                CampaignContentType.SCENE, Scene.class);

                        if (scDto.mapRef() != null) {
                            GameMap map = context.require(scDto.mapRef(), CampaignContentType.MAP, GameMap.class);
                            scene.setMap(map);
                        }
                        if (scDto.encounterRef() != null) {
                            Encounter encounter = context.require(scDto.encounterRef(), CampaignContentType.ENCOUNTER, Encounter.class);
                            scene.setEncounter(encounter);
                        }
                        if (scDto.statblockRefs() != null) {
                            for (ContentReference ref : scDto.statblockRefs()) {
                                if (ref.scope() == ContentReference.Scope.CATALOG) continue;
                                StatBlock sb = context.require(ref, CampaignContentType.STATBLOCK, StatBlock.class);
                                scene.getStatBlocks().add(sb);
                            }
                        }
                        if (scDto.handoutRefs() != null) {
                            for (ContentReference ref : scDto.handoutRefs()) {
                                if (ref.scope() == ContentReference.Scope.CATALOG) continue;
                                Handout h = context.require(ref, CampaignContentType.HANDOUT, Handout.class);
                                scene.getHandouts().add(h);
                            }
                        }
                        sceneRepo.save(scene);
                    });
                }
            }
        }
    }
}
