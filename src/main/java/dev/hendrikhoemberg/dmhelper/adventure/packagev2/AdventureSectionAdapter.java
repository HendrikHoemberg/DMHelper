package dev.hendrikhoemberg.dmhelper.adventure.packagev2;

import dev.hendrikhoemberg.dmhelper.adventure.data.Adventure;
import dev.hendrikhoemberg.dmhelper.adventure.data.AdventureRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Chapter;
import dev.hendrikhoemberg.dmhelper.adventure.data.ChapterRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.Scene;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneCheck;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneLink;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipant;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneSection;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneStatus;
import dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransition;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.AdventureDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.ChapterDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneCheckDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneLinkDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneParticipantDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneSectionDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SceneTransitionDto;
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
import dev.hendrikhoemberg.dmhelper.library.packagev2.StatBlockReferenceResolver;
import dev.hendrikhoemberg.dmhelper.notes.data.Note;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class AdventureSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final AdventureRepository adventureRepo;
    private final ChapterRepository chapterRepo;
    private final SceneRepository sceneRepo;
    private final StatBlockReferenceResolver statBlockResolver;

    public AdventureSectionAdapter(AdventureRepository adventureRepo,
                                    ChapterRepository chapterRepo,
                                    SceneRepository sceneRepo,
                                    StatBlockReferenceResolver statBlockResolver) {
        this.adventureRepo = adventureRepo;
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.statBlockResolver = statBlockResolver;
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
                                                    .map(sb -> statBlockResolver.referenceFor(sb, context))
                                                    .toList();
                                            List<ContentReference> handoutRefs = sc.getHandouts().stream()
                                                    .map(h -> context.packageRef(CampaignContentType.HANDOUT, h.getId(), h.getTitle()))
                                                    .toList();
                                            List<String> tags = sc.getTags() == null || sc.getTags().isBlank() ? List.of()
                                                    : List.of(sc.getTags().split(","));
                                            List<SceneSectionDto> sectionDtos = sc.getSections().stream()
                                                    .map(sec -> new SceneSectionDto(
                                                            sec.getKind().name(), sec.getLabel(), sec.getBody(),
                                                            sec.getSourceLocator(), sec.getSortOrder()))
                                                    .toList();
                                            List<SceneCheckDto> checkDtos = sc.getChecks().stream()
                                                    .map(chk -> {
                                                        ContentReference ruleRef = chk.getRuleScope() != null && "CATALOG".equals(chk.getRuleScope())
                                                                ? ContentReference.catalogRef(CampaignContentType.RULE, chk.getRuleRuleset(), chk.getRuleSourceKey())
                                                                : null;
                                                        return new SceneCheckDto(
                                                                chk.getLabel(), chk.getAbility(), chk.getSkill(), chk.getDc(),
                                                                chk.getVisibility() != null ? chk.getVisibility().name() : null,
                                                                chk.getSuccess(), chk.getFailure(), chk.getPartial(),
                                                                ruleRef, chk.getSourceLocator(), chk.getSortOrder());
                                                    })
                                                    .toList();
                                            List<SceneParticipantDto> participantDtos = sc.getParticipants().stream()
                                                    .map(p -> {
                                                        ContentReference statblockRef = p.getStatBlock() != null
                                                                ? statBlockResolver.referenceFor(p.getStatBlock(), context)
                                                                : null;
                                                        ContentReference noteRef = p.getNote() != null
                                                                ? context.packageRef(CampaignContentType.NOTE, p.getNote().getId(), p.getNote().getTitle())
                                                                : null;
                                                        return new SceneParticipantDto(
                                                                p.getDisplayName(), p.getQuantity(),
                                                                p.getDisposition() != null ? p.getDisposition().name() : null,
                                                                p.getPlacementHint(), statblockRef, noteRef,
                                                                p.getSourceLocator(), p.getSortOrder());
                                                    })
                                                    .toList();
                                            List<SceneTransitionDto> transitionDtos = sc.getTransitions().stream()
                                                    .map(t -> {
                                                        String tKey = context.key(CampaignContentType.TRANSITION, t.getId(), t.getLabel());
                                                        ContentReference targetRef = t.getTargetScene() != null
                                                                ? context.packageRef(CampaignContentType.SCENE, t.getTargetScene().getId(), t.getTargetScene().getTitle())
                                                                : null;
                                                        return new SceneTransitionDto(
                                                                tKey, t.getKind().name(), t.getLabel(),
                                                                targetRef, t.getExternalDestination(),
                                                                t.getCondition(), t.getDmNote(),
                                                                t.getSourceLocator(), t.getSortOrder());
                                                    })
                                                    .toList();
                                            List<SceneLinkDto> linkDtos = sc.getLinks().stream()
                                                    .map(l -> new SceneLinkDto(
                                                            l.getRole().name(),
                                                            toContentRef(l, context),
                                                            l.getDisplayText(), l.getCondition(), l.getSortOrder()))
                                                    .toList();
                                            return new SceneDto(
                                                    scKey, sc.getTitle(), sc.getBody(),
                                                    sc.getStatus().name(), sc.getSortOrder(),
                                                    mapRef, pin, encounterRef,
                                                    statblockRefs, handoutRefs,
                                                    sc.getSummary(), sc.getSourceLocator(), tags,
                                                    sc.getMapRegionKey(),
                                                    sectionDtos, checkDtos, participantDtos,
                                                    transitionDtos, linkDtos
                                            );
                                        }).toList();
                                return new ChapterDto(chKey, ch.getTitle(), ch.getIntro(), ch.getSortOrder(), sceneDtos);
                            }).toList();
                    return new AdventureDto(advKey, adv.getName(), adv.getDescription(),
                            adv.getSourceAttribution(), adv.getSortOrder(), chapterDtos, adv.getCreatedAt());
                }).toList();

        target.adventures(adventureDtos);
    }

    private static ContentReference toContentRef(SceneLink link, CampaignExportContext context) {
        if (link.getTargetScope() == dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope.CATALOG) {
            return ContentReference.catalogRef(CampaignContentType.valueOf(link.getTargetType()), link.getCatalogRuleset(), link.getCatalogSourceKey());
        }
        return ContentReference.packageRef(CampaignContentType.valueOf(link.getTargetType()), link.getTargetId().toString());
    }

    private static CampaignContentType contentTypeFor(String targetType) {
        try {
            return CampaignContentType.valueOf(targetType);
        } catch (IllegalArgumentException e) {
            return CampaignContentType.NOTE;
        }
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
                    sc.setSummary(scDto.summary());
                    sc.setSourceLocator(scDto.sourceLocator());
                    if (scDto.tags() != null) {
                        sc.setTags(String.join(",", scDto.tags()));
                    }
                    sc.setMapRegionKey(scDto.mapRegionKey());
                    if (scDto.sections() != null) {
                        for (SceneSectionDto secDto : scDto.sections()) {
                            SceneSection sec = new SceneSection();
                            sec.setScene(sc);
                            sec.setKind(dev.hendrikhoemberg.dmhelper.adventure.data.SceneSectionKind.valueOf(secDto.kind()));
                            sec.setLabel(secDto.label());
                            sec.setBody(secDto.body());
                            sec.setSourceLocator(secDto.sourceLocator());
                            sec.setSortOrder(secDto.sortOrder());
                            sc.getSections().add(sec);
                        }
                    }
                    if (scDto.checks() != null) {
                        for (SceneCheckDto chkDto : scDto.checks()) {
                            SceneCheck chk = new SceneCheck();
                            chk.setScene(sc);
                            chk.setLabel(chkDto.label());
                            chk.setAbility(chkDto.ability());
                            chk.setSkill(chkDto.skill());
                            chk.setDc(chkDto.dc());
                            if (chkDto.visibility() != null) {
                                chk.setVisibility(dev.hendrikhoemberg.dmhelper.adventure.data.SceneCheckVisibility.valueOf(chkDto.visibility()));
                            }
                            chk.setSuccess(chkDto.success());
                            chk.setFailure(chkDto.failure());
                            chk.setPartial(chkDto.partial());
                            if (chkDto.ruleRef() != null && chkDto.ruleRef().scope() == ContentReference.Scope.CATALOG) {
                                chk.setRuleScope("CATALOG");
                                chk.setRuleRuleset(chkDto.ruleRef().ruleset());
                                chk.setRuleSourceKey(chkDto.ruleRef().sourceKey());
                            }
                            chk.setSourceLocator(chkDto.sourceLocator());
                            chk.setSortOrder(chkDto.sortOrder());
                            sc.getChecks().add(chk);
                        }
                    }
                    if (scDto.participants() != null) {
                        for (SceneParticipantDto pDto : scDto.participants()) {
                            SceneParticipant p = new SceneParticipant();
                            p.setScene(sc);
                            p.setDisplayName(pDto.displayName());
                            p.setQuantity(pDto.quantity());
                            if (pDto.disposition() != null) {
                                p.setDisposition(dev.hendrikhoemberg.dmhelper.adventure.data.SceneParticipantDisposition.valueOf(pDto.disposition()));
                            }
                            p.setPlacementHint(pDto.placementHint());
                            p.setSourceLocator(pDto.sourceLocator());
                            p.setSortOrder(pDto.sortOrder());
                            sc.getParticipants().add(p);
                        }
                    }
                    if (scDto.transitions() != null) {
                        for (SceneTransitionDto tDto : scDto.transitions()) {
                            SceneTransition t = new SceneTransition();
                            t.setScene(sc);
                            t.setKind(dev.hendrikhoemberg.dmhelper.adventure.data.SceneTransitionKind.valueOf(tDto.kind()));
                            t.setLabel(tDto.label());
                            t.setExternalDestination(tDto.externalDestination());
                            t.setCondition(tDto.condition());
                            t.setDmNote(tDto.dmNote());
                            t.setSourceLocator(tDto.sourceLocator());
                            t.setSortOrder(tDto.sortOrder());
                            sc.getTransitions().add(t);
                        }
                    }
                    if (scDto.links() != null) {
                        for (SceneLinkDto lDto : scDto.links()) {
                            SceneLink l = new SceneLink();
                            l.setScene(sc);
                            l.setRole(dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkRole.valueOf(lDto.role()));
                            l.setDisplayText(lDto.displayText());
                            l.setCondition(lDto.condition());
                            l.setSortOrder(lDto.sortOrder());
                            if (lDto.targetRef() != null) {
                                l.setTargetScope(lDto.targetRef().scope() == ContentReference.Scope.CATALOG
                                        ? dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope.CATALOG
                                        : dev.hendrikhoemberg.dmhelper.adventure.data.SceneLinkTargetScope.PACKAGE);
                                l.setTargetType(lDto.targetRef().type().name());
                                if (lDto.targetRef().scope() == ContentReference.Scope.CATALOG) {
                                    l.setCatalogRuleset(lDto.targetRef().ruleset());
                                    l.setCatalogSourceKey(lDto.targetRef().sourceKey());
                                }
                            }
                            sc.getLinks().add(l);
                        }
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
                                scene.getStatBlocks().add(statBlockResolver.resolve(ref, context));
                            }
                        }
                        if (scDto.handoutRefs() != null) {
                            for (ContentReference ref : scDto.handoutRefs()) {
                                if (ref.scope() == ContentReference.Scope.CATALOG) continue;
                                Handout h = context.require(ref, CampaignContentType.HANDOUT, Handout.class);
                                scene.getHandouts().add(h);
                            }
                        }
                        // Restore participant statblock/note references
                        if (scDto.participants() != null) {
                            for (int i = 0; i < scDto.participants().size() && i < scene.getParticipants().size(); i++) {
                                var pDto = scDto.participants().get(i);
                                var p = scene.getParticipants().get(i);
                                if (pDto.statblockRef() != null) {
                                    p.setStatBlock(statBlockResolver.resolve(pDto.statblockRef(), context));
                                }
                                if (pDto.noteRef() != null) {
                                    Note note = context.require(pDto.noteRef(), CampaignContentType.NOTE, Note.class);
                                    p.setNote(note);
                                }
                            }
                        }
                        // Restore transition target scene references
                        if (scDto.transitions() != null) {
                            for (int i = 0; i < scDto.transitions().size() && i < scene.getTransitions().size(); i++) {
                                var tDto = scDto.transitions().get(i);
                                var t = scene.getTransitions().get(i);
                                context.register(CampaignContentType.TRANSITION, tDto.key(), t, t.getId());
                                if (tDto.targetSceneRef() != null) {
                                    Scene target = context.require(tDto.targetSceneRef(), CampaignContentType.SCENE, Scene.class);
                                    t.setTargetScene(target);
                                }
                            }
                        }
                        sceneRepo.save(scene);
                    });
                }
            }
        }
    }
}
