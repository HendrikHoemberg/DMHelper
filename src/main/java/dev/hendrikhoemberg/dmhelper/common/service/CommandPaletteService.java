package dev.hendrikhoemberg.dmhelper.common.service;

import dev.hendrikhoemberg.dmhelper.adventure.data.SceneRepository;
import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldNpcRepository;
import dev.hendrikhoemberg.dmhelper.world.data.WorldLocationRepository;
import dev.hendrikhoemberg.dmhelper.world.data.FactionRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTable;
import dev.hendrikhoemberg.dmhelper.rollabletable.data.RollableTableRepository;
import dev.hendrikhoemberg.dmhelper.sheet.data.CharacterSheetRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.HazardRepository;
import dev.hendrikhoemberg.dmhelper.threat.data.TrapRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CommandPaletteService {

    public record SearchResultItem(String id, String title, String type, String subtype, String url) {}

    private record RankedResult(SearchResultItem item, int relevance) {}

    private static final int MAX_RESULTS = 20;

    private final NoteRepository noteRepo;
    private final QuickNoteRepository quickNoteRepo;
    private final StatBlockRepository statBlockRepo;
    private final SpellRepository spellRepo;
    private final ConditionRepository conditionRepo;
    private final RuleSectionRepository ruleSectionRepo;
    private final EquipmentItemRepository equipmentItemRepo;
    private final MagicItemRepository magicItemRepo;
    private final CharacterClassRepository characterClassRepo;
    private final SpeciesRepository speciesRepo;
    private final BackgroundRepository backgroundRepo;
    private final FeatRepository featRepo;
    private final GameMapRepository gameMapRepo;
    private final EncounterRepository encounterRepo;
    private final HandoutRepository handoutRepo;
    private final PartyMemberRepository partyMemberRepo;
    private final SceneRepository sceneRepo;
    private final ContentDestinationRegistry destinations;
    private final CharacterSheetRepository characterSheetRepo;
    private final WorldNpcRepository worldNpcRepo;
    private final WorldLocationRepository worldLocationRepo;
    private final FactionRepository factionRepo;
    private final RollableTableRepository rollableTableRepo;
    private final TrapRepository trapRepo;
    private final HazardRepository hazardRepo;

    public CommandPaletteService(NoteRepository noteRepo, QuickNoteRepository quickNoteRepo,
                                   StatBlockRepository statBlockRepo, SpellRepository spellRepo,
                                   ConditionRepository conditionRepo, RuleSectionRepository ruleSectionRepo,
                                   EquipmentItemRepository equipmentItemRepo, MagicItemRepository magicItemRepo,
                                   CharacterClassRepository characterClassRepo, SpeciesRepository speciesRepo,
                                   BackgroundRepository backgroundRepo, FeatRepository featRepo,
                                   GameMapRepository gameMapRepo, EncounterRepository encounterRepo,
                                   HandoutRepository handoutRepo, PartyMemberRepository partyMemberRepo,
                                   SceneRepository sceneRepo,
                                   ContentDestinationRegistry destinations,
                                   CharacterSheetRepository characterSheetRepo,
                                   WorldNpcRepository worldNpcRepo,
                                   WorldLocationRepository worldLocationRepo,
                                   FactionRepository factionRepo,
                                   RollableTableRepository rollableTableRepo,
                                   TrapRepository trapRepo,
                                   HazardRepository hazardRepo) {
        this.noteRepo = noteRepo;
        this.quickNoteRepo = quickNoteRepo;
        this.statBlockRepo = statBlockRepo;
        this.spellRepo = spellRepo;
        this.conditionRepo = conditionRepo;
        this.ruleSectionRepo = ruleSectionRepo;
        this.equipmentItemRepo = equipmentItemRepo;
        this.magicItemRepo = magicItemRepo;
        this.characterClassRepo = characterClassRepo;
        this.speciesRepo = speciesRepo;
        this.backgroundRepo = backgroundRepo;
        this.featRepo = featRepo;
        this.gameMapRepo = gameMapRepo;
        this.encounterRepo = encounterRepo;
        this.handoutRepo = handoutRepo;
        this.partyMemberRepo = partyMemberRepo;
        this.sceneRepo = sceneRepo;
        this.destinations = destinations;
        this.characterSheetRepo = characterSheetRepo;
        this.worldNpcRepo = worldNpcRepo;
        this.worldLocationRepo = worldLocationRepo;
        this.factionRepo = factionRepo;
        this.rollableTableRepo = rollableTableRepo;
        this.trapRepo = trapRepo;
        this.hazardRepo = hazardRepo;
    }

    public List<SearchResultItem> search(String query, UUID campaignId) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.strip().toLowerCase();
        List<RankedResult> results = new ArrayList<>();

        if (campaignId != null) {
            noteRepo.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                    .filter(n -> matches(n.getTitle(), q) || matches(n.getBody(), q))
                    .forEach(n -> {
                        SearchResultItem item = new SearchResultItem(n.getId().toString(), n.getTitle(), "note",
                                n.getType().name(),
                                destinations.campaign(ContentDestinationRegistry.CampaignType.NOTE,
                                        campaignId, n.getId(), null));
                        add(results, item, n.getBody(), q, true);
                    });

            quickNoteRepo.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                    .filter(qn -> matches(qn.getBody(), q))
                    .map(qn -> new SearchResultItem(qn.getId().toString(),
                            qn.getBody().length() > 80 ? qn.getBody().substring(0, 77) + "..." : qn.getBody(),
                            "quicknote", qn.getTargetType(),
                            destinations.campaign(ContentDestinationRegistry.CampaignType.QUICK_NOTE, campaignId, qn.getId(), null)))
                    .forEach(item -> add(results, item, item.title(), q, true));

            gameMapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId).stream()
                    .filter(m -> matches(m.getName(), q))
                    .map(m -> new SearchResultItem(m.getId().toString(), m.getName(), "map",
                            m.getGridWidth() + "x" + m.getGridHeight(),
                            destinations.campaign(ContentDestinationRegistry.CampaignType.MAP, campaignId, m.getId(), null)))
                    .forEach(item -> add(results, item, null, q, true));

            encounterRepo.findByCampaignIdOrderByNameAsc(campaignId).stream()
                    .filter(e -> matches(e.getName(), q))
                    .map(e -> new SearchResultItem(e.getId().toString(), e.getName(), "encounter",
                            e.getStatus().name(),
                            destinations.campaign(ContentDestinationRegistry.CampaignType.ENCOUNTER, campaignId, e.getId(), null)))
                    .forEach(item -> add(results, item, null, q, true));

            handoutRepo.findByCampaignIdOrderByTitleAsc(campaignId).stream()
                    .filter(h -> matches(h.getTitle(), q))
                    .map(h -> new SearchResultItem(h.getId().toString(), h.getTitle(), "handout",
                            null, destinations.campaign(ContentDestinationRegistry.CampaignType.HANDOUT, campaignId, h.getId(), null)))
                    .forEach(item -> add(results, item, null, q, true));

            partyMemberRepo.findByCampaignIdOrderByCharacterNameAsc(campaignId).stream()
                    .filter(pm -> matches(pm.getCharacterName(), q) || matches(pm.getPlayerName(), q))
                    .map(pm -> new SearchResultItem(pm.getId().toString(), pm.getCharacterName(),
                            "party-member", pm.getClassAndLevel(),
                            destinations.campaign(
                                    characterSheetRepo.findByPartyMemberId(pm.getId()).isPresent()
                                            ? ContentDestinationRegistry.CampaignType.PARTY_MEMBER_SHEET
                                            : ContentDestinationRegistry.CampaignType.PARTY_MEMBER,
                                    campaignId, pm.getId(), null)))
                    .forEach(item -> add(results, item, null, q, true));

            worldNpcRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId).stream()
                    .filter(n -> matches(n.getName(), q))
                    .map(n -> new SearchResultItem(n.getId().toString(), n.getName(), "world-npc",
                            n.getRole(),
                            destinations.campaign(ContentDestinationRegistry.CampaignType.WORLD_NPC, campaignId, n.getId(), null)))
                    .forEach(item -> add(results, item, null, q, true));

            worldLocationRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId).stream()
                    .filter(l -> matches(l.getName(), q))
                    .map(l -> new SearchResultItem(l.getId().toString(), l.getName(), "world-location",
                            l.getKind().name(),
                            destinations.campaign(ContentDestinationRegistry.CampaignType.WORLD_LOCATION, campaignId, l.getId(), null)))
                    .forEach(item -> add(results, item, null, q, true));

            factionRepo.findByCampaignIdOrderByNameAscIdAsc(campaignId).stream()
                    .filter(f -> matches(f.getName(), q))
                    .map(f -> new SearchResultItem(f.getId().toString(), f.getName(), "faction",
                            null,
                            destinations.campaign(ContentDestinationRegistry.CampaignType.FACTION, campaignId, f.getId(), null)))
                    .forEach(item -> add(results, item, null, q, true));

            sceneRepo.findByChapterAdventureCampaignId(campaignId).stream()
                    .filter(s -> matches(s.getTitle(), q))
                    .map(s -> {
                        String subtype = (s.getSceneKey() != null ? s.getSceneKey() + " · " : "")
                                + s.getChapter().getAdventure().getName();
                        return new SearchResultItem(s.getId().toString(), s.getTitle(), "scene",
                                subtype,
                                destinations.campaign(ContentDestinationRegistry.CampaignType.SCENE, campaignId, s.getId(),
                                        s.getChapter().getAdventure().getId()));
                    })
                    .forEach(item -> add(results, item, null, q, true));
        }

        statBlockRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(sb -> new SearchResultItem(sb.getId().toString(), sb.getName(), "statblock",
                        sb.getType() + " (CR " + sb.getCr() + ")",
                        destinations.library(ContentDestinationRegistry.LibraryType.STATBLOCK, sb.getId(), sb.getSourceKey(), sb.getName())))
                .forEach(item -> add(results, item, null, q, false));

        spellRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(s -> new SearchResultItem(s.getId().toString(), s.getName(), "spell",
                        "Level " + s.getLevel() + " " + s.getSchool(),
                        destinations.library(ContentDestinationRegistry.LibraryType.SPELL, s.getId(), s.getSourceKey(), s.getName())))
                .forEach(item -> add(results, item, null, q, false));

        conditionRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(c -> new SearchResultItem(c.getId().toString(), c.getName(), "condition",
                        null, destinations.library(ContentDestinationRegistry.LibraryType.CONDITION, c.getId(), null, c.getName())))
                .forEach(item -> add(results, item, null, q, false));

        ruleSectionRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(r -> new SearchResultItem(r.getId().toString(), r.getName(), "rule",
                        r.getRuleset(), destinations.library(ContentDestinationRegistry.LibraryType.RULE, r.getId(), null, r.getName())))
                .forEach(item -> add(results, item, null, q, false));

        equipmentItemRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(e -> new SearchResultItem(e.getId().toString(), e.getName(), "equipment",
                        e.getCategory().toString(), destinations.library(ContentDestinationRegistry.LibraryType.EQUIPMENT, e.getId(), null, e.getName())))
                .forEach(item -> add(results, item, null, q, false));

        magicItemRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(m -> new SearchResultItem(m.getId().toString(), m.getName(), "magic-item",
                        m.getRarity(), destinations.library(ContentDestinationRegistry.LibraryType.MAGIC_ITEM, m.getId(), null, m.getName())))
                .forEach(item -> add(results, item, null, q, false));

        characterClassRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(c -> new SearchResultItem(c.getId().toString(), c.getName(), "class",
                        null, destinations.library(ContentDestinationRegistry.LibraryType.CLASS, c.getId(), c.getSourceKey(), c.getName())))
                .forEach(item -> add(results, item, null, q, false));

        speciesRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(s -> new SearchResultItem(s.getId().toString(), s.getName(), "species",
                        null, destinations.library(ContentDestinationRegistry.LibraryType.SPECIES, s.getId(), null, s.getName())))
                .forEach(item -> add(results, item, null, q, false));

        backgroundRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(b -> new SearchResultItem(b.getId().toString(), b.getName(), "background",
                        null, destinations.library(ContentDestinationRegistry.LibraryType.BACKGROUND, b.getId(), null, b.getName())))
                .forEach(item -> add(results, item, null, q, false));

        featRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .map(f -> new SearchResultItem(f.getId().toString(), f.getName(), "feat",
                        null, destinations.library(ContentDestinationRegistry.LibraryType.FEAT, f.getId(), null, f.getName())))
                .forEach(item -> add(results, item, null, q, false));

        rollableTableRepo.findVisibleByCampaignId(campaignId).stream()
                .filter(rt -> matches(rt.getName(), q) || matches(rt.getDescription(), q)
                        || matches(rt.getCategory() != null ? rt.getCategory().name() : null, q)
                        || matches(rt.getTags(), q))
                .forEach(table -> {
                    SearchResultItem item = new SearchResultItem(
                            table.getId().toString(), table.getName(), "rollable-table",
                            table.getCategory() != null ? table.getCategory().name().toLowerCase() : null,
                            destinations.library(ContentDestinationRegistry.LibraryType.ROLLABLE_TABLE,
                                    table.getId(), table.getSourceKey(), table.getName()));
                    boolean campaignOwned = campaignId != null && table.getCampaign() != null
                            && campaignId.equals(table.getCampaign().getId());
                    add(results, item, null, q, campaignOwned);
                });

        trapRepo.findVisibleByCampaignId(campaignId).stream()
                .filter(t -> matches(t.getName(), q) || matches(t.getDescription(), q)
                        || matches(t.getTriggerDescription(), q)
                        || matches(t.getSeverity() != null ? t.getSeverity().name() : null, q))
                .forEach(trap -> {
                    SearchResultItem item = new SearchResultItem(
                            trap.getId().toString(), trap.getName(), "trap",
                            trap.getSeverity() != null ? trap.getSeverity().name().toLowerCase() : null,
                            destinations.library(ContentDestinationRegistry.LibraryType.TRAP,
                                    trap.getId(), trap.getSourceKey(), trap.getName()));
                    boolean campaignOwned = campaignId != null && trap.getCampaign() != null
                            && campaignId.equals(trap.getCampaign().getId());
                    add(results, item, null, q, campaignOwned);
                });

        hazardRepo.findVisibleByCampaignId(campaignId).stream()
                .filter(h -> matches(h.getName(), q) || matches(h.getDescription(), q)
                        || matches(h.getExposureText(), q)
                        || matches(h.getSeverity() != null ? h.getSeverity().name() : null, q))
                .forEach(hazard -> {
                    SearchResultItem item = new SearchResultItem(
                            hazard.getId().toString(), hazard.getName(), "hazard",
                            hazard.getSeverity() != null ? hazard.getSeverity().name().toLowerCase() : null,
                            destinations.library(ContentDestinationRegistry.LibraryType.HAZARD,
                                    hazard.getId(), hazard.getSourceKey(), hazard.getName()));
                    boolean campaignOwned = campaignId != null && hazard.getCampaign() != null
                            && campaignId.equals(hazard.getCampaign().getId());
                    add(results, item, null, q, campaignOwned);
                });

        Set<String> seen = new HashSet<>();
        return results.stream()
                .sorted(Comparator.comparingInt(RankedResult::relevance)
                        .thenComparing(result -> result.item().title(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(result -> result.item().type()))
                .filter(result -> seen.add(result.item().type() + ":" + result.item().id()))
                .limit(MAX_RESULTS)
                .map(RankedResult::item)
                .toList();
    }

    private int relevance(String title, String body, String query, boolean campaignOwned) {
        String normalizedTitle = title == null ? "" : title.toLowerCase();
        int base;
        if (normalizedTitle.equals(query)) base = 0;
        else if (normalizedTitle.startsWith(query)) base = 10;
        else if (normalizedTitle.contains(query)) base = 20;
        else if (body != null && body.toLowerCase().contains(query)) base = 30;
        else base = 40;
        return base + (campaignOwned ? 0 : 1);
    }

    private void add(List<RankedResult> results, SearchResultItem item,
                     String searchableBody, String query, boolean campaignOwned) {
        results.add(new RankedResult(item,
                relevance(item.title(), searchableBody, query, campaignOwned)));
    }

    private boolean matches(String field, String query) {
        return field != null && field.toLowerCase().contains(query);
    }
}
