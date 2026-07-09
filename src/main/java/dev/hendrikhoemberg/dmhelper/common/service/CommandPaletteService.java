package dev.hendrikhoemberg.dmhelper.common.service;

import dev.hendrikhoemberg.dmhelper.encounter.data.EncounterRepository;
import dev.hendrikhoemberg.dmhelper.gamemap.data.GameMapRepository;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.notes.data.NoteRepository;
import dev.hendrikhoemberg.dmhelper.notes.data.QuickNoteRepository;
import dev.hendrikhoemberg.dmhelper.party.data.PartyMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CommandPaletteService {

    public record SearchResultItem(String id, String title, String type, String subtype, String url) {}

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

    public CommandPaletteService(NoteRepository noteRepo, QuickNoteRepository quickNoteRepo,
                                  StatBlockRepository statBlockRepo, SpellRepository spellRepo,
                                  ConditionRepository conditionRepo, RuleSectionRepository ruleSectionRepo,
                                  EquipmentItemRepository equipmentItemRepo, MagicItemRepository magicItemRepo,
                                  CharacterClassRepository characterClassRepo, SpeciesRepository speciesRepo,
                                  BackgroundRepository backgroundRepo, FeatRepository featRepo,
                                  GameMapRepository gameMapRepo, EncounterRepository encounterRepo,
                                  HandoutRepository handoutRepo, PartyMemberRepository partyMemberRepo) {
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
    }

    public List<SearchResultItem> search(String query, UUID campaignId, String typeFilter) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.strip().toLowerCase();
        List<SearchResultItem> results = new ArrayList<>();

        if (campaignId != null) {
            noteRepo.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                    .filter(n -> matches(n.getTitle(), q) || matches(n.getBody(), q))
                    .limit(MAX_RESULTS)
                    .map(n -> new SearchResultItem(n.getId().toString(), n.getTitle(), "note",
                            n.getType().name(), "/campaigns/" + campaignId + "/notes/" + n.getId()))
                    .forEach(results::add);

            quickNoteRepo.findByCampaignIdOrderByCreatedAtDesc(campaignId).stream()
                    .filter(qn -> matches(qn.getBody(), q))
                    .limit(5)
                    .map(qn -> new SearchResultItem(qn.getId().toString(),
                            qn.getBody().length() > 80 ? qn.getBody().substring(0, 77) + "..." : qn.getBody(),
                            "quicknote", qn.getTargetType(),
                            "/campaigns/" + campaignId + "/notes"))
                    .forEach(results::add);

            gameMapRepo.findByCampaignIdOrderBySortOrderAsc(campaignId).stream()
                    .filter(m -> matches(m.getName(), q))
                    .limit(MAX_RESULTS)
                    .map(m -> new SearchResultItem(m.getId().toString(), m.getName(), "map",
                            m.getGridWidth() + "x" + m.getGridHeight(),
                            "/campaigns/" + campaignId + "/maps/" + m.getId() + "/battle"))
                    .forEach(results::add);

            encounterRepo.findByCampaignIdOrderByNameAsc(campaignId).stream()
                    .filter(e -> matches(e.getName(), q))
                    .limit(MAX_RESULTS)
                    .map(e -> new SearchResultItem(e.getId().toString(), e.getName(), "encounter",
                            e.getStatus().name(),
                            "/campaigns/" + campaignId + "/encounters/" + e.getId()))
                    .forEach(results::add);

            handoutRepo.findByCampaignIdOrderByTitleAsc(campaignId).stream()
                    .filter(h -> matches(h.getTitle(), q))
                    .limit(MAX_RESULTS)
                    .map(h -> new SearchResultItem(h.getId().toString(), h.getTitle(), "handout",
                            null, "/campaigns/" + campaignId + "/handouts/" + h.getId() + "/view"))
                    .forEach(results::add);

            partyMemberRepo.findByCampaignIdOrderByCharacterNameAsc(campaignId).stream()
                    .filter(pm -> matches(pm.getCharacterName(), q) || matches(pm.getPlayerName(), q))
                    .limit(MAX_RESULTS)
                    .map(pm -> new SearchResultItem(pm.getId().toString(), pm.getCharacterName(),
                            "party-member", pm.getClassAndLevel(),
                            "/campaigns/" + campaignId + "/party/" + pm.getId()))
                    .forEach(results::add);
        }

        statBlockRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(sb -> new SearchResultItem(sb.getId().toString(), sb.getName(), "statblock",
                        sb.getType() + " (CR " + sb.getCr() + ")",
                        "/library/statblocks/" + sb.getId()))
                .forEach(results::add);

        spellRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(s -> new SearchResultItem(s.getId().toString(), s.getName(), "spell",
                        "Level " + s.getLevel() + " " + s.getSchool(),
                        "/library/spells/" + s.getId()))
                .forEach(results::add);

        conditionRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(c -> new SearchResultItem(c.getId().toString(), c.getName(), "condition",
                        null, "/library/conditions/" + c.getId()))
                .forEach(results::add);

        ruleSectionRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(r -> new SearchResultItem(r.getId().toString(), r.getName(), "rule",
                        r.getRuleset(), "/library/rules/" + r.getId()))
                .forEach(results::add);

        equipmentItemRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(e -> new SearchResultItem(e.getId().toString(), e.getName(), "equipment",
                        e.getCategory().toString(), "/library/equipment/" + e.getId()))
                .forEach(results::add);

        magicItemRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(m -> new SearchResultItem(m.getId().toString(), m.getName(), "magic-item",
                        m.getRarity(), "/library/magic-items/" + m.getId()))
                .forEach(results::add);

        characterClassRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(c -> new SearchResultItem(c.getId().toString(), c.getName(), "class",
                        null, "/library/classes/" + c.getId()))
                .forEach(results::add);

        speciesRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(s -> new SearchResultItem(s.getId().toString(), s.getName(), "species",
                        null, "/library/species/" + s.getId()))
                .forEach(results::add);

        backgroundRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(b -> new SearchResultItem(b.getId().toString(), b.getName(), "background",
                        null, "/library/backgrounds/" + b.getId()))
                .forEach(results::add);

        featRepo.findByNameContainingIgnoreCaseOrderByNameAsc(q).stream()
                .limit(MAX_RESULTS)
                .map(f -> new SearchResultItem(f.getId().toString(), f.getName(), "feat",
                        null, "/library/feats/" + f.getId()))
                .forEach(results::add);

        return results;
    }

    private boolean matches(String field, String query) {
        return field != null && field.toLowerCase().contains(query);
    }
}
