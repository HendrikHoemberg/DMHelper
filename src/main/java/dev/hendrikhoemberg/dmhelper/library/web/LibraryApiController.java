package dev.hendrikhoemberg.dmhelper.library.web;

import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.library.service.*;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1/library")
public class LibraryApiController {

    public record StatBlockSummary(UUID id, String name, String cr, String type,
                                   String hp, int ac, int xp) {}

    private final StatBlockService statBlockService;
    private final SpellService spellService;
    private final ConditionService conditionService;
    private final RuleSectionService ruleSectionService;
    private final EquipmentItemService equipmentItemService;
    private final MagicItemService magicItemService;
    private final CharacterClassService characterClassService;
    private final SpeciesService speciesService;
    private final BackgroundService backgroundService;
    private final FeatService featService;
    private final ObjectMapper objectMapper;

    public LibraryApiController(StatBlockService statBlockService,
                                SpellService spellService,
                                ConditionService conditionService,
                                RuleSectionService ruleSectionService,
                                EquipmentItemService equipmentItemService,
                                MagicItemService magicItemService,
                                CharacterClassService characterClassService,
                                SpeciesService speciesService,
                                BackgroundService backgroundService,
                                FeatService featService,
                                ObjectMapper objectMapper) {
        this.statBlockService = statBlockService;
        this.spellService = spellService;
        this.conditionService = conditionService;
        this.ruleSectionService = ruleSectionService;
        this.equipmentItemService = equipmentItemService;
        this.magicItemService = magicItemService;
        this.characterClassService = characterClassService;
        this.speciesService = speciesService;
        this.backgroundService = backgroundService;
        this.featService = featService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/statblocks/search")
    public List<StatBlockSummary> searchStatblocks(@RequestParam(required = false) String q) {
        String search = (q != null) ? q : "";
        return statBlockService.search(null, null, null, search).stream()
                .map(sb -> new StatBlockSummary(sb.getId(), sb.getName(), sb.getCr(),
                        sb.getType(), sb.getHp(), sb.getAc(), sb.getXp()))
                .toList();
    }

    @GetMapping("/statblocks/{id}")
    public StatBlockRuntimeProjection getStatblock(@PathVariable UUID id) {
        var sb = statBlockService.findById(id);
        return StatBlockRuntimeProjection.from(sb, objectMapper);
    }

    @GetMapping("/srd-keys")
    public List<String> srdKeys() {
        var statblocks = statBlockService.findAll().stream()
                .filter(sb -> sb.getSource() == ContentSource.SRD)
                .map(StatBlock::getSourceKey);
        var spells = spellService.findAll().stream().map(Spell::getSourceKey);
        var conditions = conditionService.findAll().stream().map(Condition::getSourceKey);
        var rules = ruleSectionService.findAll().stream().map(RuleSection::getSourceKey);
        var equipment = equipmentItemService.findAll().stream().map(EquipmentItem::getSourceKey);
        var magicItems = magicItemService.findAll().stream().map(MagicItem::getSourceKey);
        var classes = characterClassService.findAll().stream().map(CharacterClass::getSourceKey);
        var species = speciesService.findAll().stream().map(Species::getSourceKey);
        var backgrounds = backgroundService.findAll().stream().map(Background::getSourceKey);
        var feats = featService.findAll().stream().map(Feat::getSourceKey);
        return Stream.of(statblocks, spells, conditions, rules, equipment,
                        magicItems, classes, species, backgrounds, feats)
                .flatMap(s -> s)
                .sorted()
                .toList();
    }

    public record ConditionSummary(String sourceKey, String name, String description) {
        public static ConditionSummary from(Condition c) {
            return new ConditionSummary(c.getSourceKey(), c.getName(), c.getDescription());
        }
    }

    @GetMapping("/conditions")
    public List<ConditionSummary> listConditions() {
        return conditionService.findAll().stream()
                .map(ConditionSummary::from)
                .toList();
    }
}
