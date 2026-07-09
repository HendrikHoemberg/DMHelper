package dev.hendrikhoemberg.dmhelper.library.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;
import dev.hendrikhoemberg.dmhelper.library.data.*;
import dev.hendrikhoemberg.dmhelper.library.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/library")
public class LibraryController {

    private final StatBlockService service;
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

    public LibraryController(StatBlockService service,
                             SpellService spellService,
                             ConditionService conditionService,
                             RuleSectionService ruleSectionService,
                             EquipmentItemService equipmentItemService,
                             MagicItemService magicItemService,
                             CharacterClassService characterClassService,
                             SpeciesService speciesService,
                             BackgroundService backgroundService,
                             FeatService featService) {
        this.service = service;
        this.spellService = spellService;
        this.conditionService = conditionService;
        this.ruleSectionService = ruleSectionService;
        this.equipmentItemService = equipmentItemService;
        this.magicItemService = magicItemService;
        this.characterClassService = characterClassService;
        this.speciesService = speciesService;
        this.backgroundService = backgroundService;
        this.featService = featService;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public String list() {
        return "library/list";
    }

    @GetMapping("/statblocks")
    public String search(@RequestParam(required = false) String search,
                         @RequestParam(required = false) String cr,
                         @RequestParam(required = false) String type,
                         @RequestParam(required = false) String source,
                         Model model) {
        StatBlock.Source sourceEnum = null;
        if (source != null && !source.isBlank()) {
            sourceEnum = StatBlock.Source.valueOf(source);
        }
        List<StatBlock> results = service.search(sourceEnum, cr, type, search);
        model.addAttribute("statblocks", results);
        return "library/_card :: card-list";
    }

    @GetMapping("/statblocks/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        StatBlock sb = service.findById(id);
        enrichStatBlock(sb);
        model.addAttribute("sb", sb);
        return "library/detail";
    }

    @GetMapping("/statblocks/new")
    public String newForm(Model model) {
        model.addAttribute("sb", null);
        model.addAttribute("campaignId", null);
        return "library/_form :: form";
    }

    @GetMapping("/statblocks/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        StatBlock sb = service.findById(id);
        model.addAttribute("sb", sb);
        model.addAttribute("campaignId", sb.getCampaign() != null ? sb.getCampaign().getId() : null);
        return "library/_form :: form";
    }

    @PostMapping("/statblocks")
    public String create(@RequestParam(required = false) UUID campaignId,
                         @RequestParam String name, @RequestParam String cr,
                         @RequestParam String type, @RequestParam int ac,
                         @RequestParam String hp, @RequestParam String speed,
                         @RequestParam int strScore, @RequestParam int dexScore,
                         @RequestParam int conScore, @RequestParam int intScore,
                         @RequestParam int wisScore, @RequestParam int chaScore,
                         @RequestParam(required = false) String size,
                         @RequestParam(required = false) String alignment,
                         @RequestParam(required = false) Integer strSave,
                         @RequestParam(required = false) Integer dexSave,
                         @RequestParam(required = false) Integer conSave,
                         @RequestParam(required = false) Integer intSave,
                         @RequestParam(required = false) Integer wisSave,
                         @RequestParam(required = false) Integer chaSave,
                         @RequestParam(required = false) String skills,
                         @RequestParam(required = false) String damageVulnerabilities,
                         @RequestParam(required = false) String damageResistances,
                         @RequestParam(required = false) String damageImmunities,
                         @RequestParam(required = false) String conditionImmunities,
                         @RequestParam(required = false) String senses,
                         @RequestParam(required = false) String languages,
                         @RequestParam(required = false) String traits,
                         @RequestParam(required = false) String actions,
                         @RequestParam(required = false) String bonusActions,
                         @RequestParam(required = false) String reactions,
                         @RequestParam(required = false) String legendaryActions,
                         @RequestParam(required = false) String legendaryDescription,
                         @RequestParam(required = false) String lairActions,
                         Model model) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        StatBlock sb = service.createCustom(campaignId, name, cr, type, ac, hp, speed,
                strScore, dexScore, conScore, intScore, wisScore, chaScore,
                strSave, dexSave, conSave, intSave, wisSave, chaSave,
                skills, damageVulnerabilities, damageResistances,
                damageImmunities, conditionImmunities, senses, languages);
        if (size != null) sb.setSize(size);
        if (alignment != null) sb.setAlignment(alignment);
        if (traits != null) sb.setTraits(traits);
        if (actions != null) sb.setActions(actions);
        if (bonusActions != null) sb.setBonusActions(bonusActions);
        if (reactions != null) sb.setReactions(reactions);
        if (legendaryActions != null) sb.setLegendaryActions(legendaryActions);
        if (legendaryDescription != null) sb.setLegendaryDescription(legendaryDescription);
        if (lairActions != null) sb.setLairActions(lairActions);
        model.addAttribute("sb", sb);
        model.addAttribute("statblocks", List.of(sb));
        return "library/_card :: card";
    }

    @PutMapping("/statblocks/{id}")
    public String update(@PathVariable UUID id,
                         @RequestParam String name, @RequestParam String cr,
                         @RequestParam String type, @RequestParam int ac,
                         @RequestParam String hp, @RequestParam String speed,
                         @RequestParam int strScore, @RequestParam int dexScore,
                         @RequestParam int conScore, @RequestParam int intScore,
                         @RequestParam int wisScore, @RequestParam int chaScore,
                         @RequestParam(required = false) String size,
                         @RequestParam(required = false) String alignment,
                         @RequestParam(required = false) Integer strSave,
                         @RequestParam(required = false) Integer dexSave,
                         @RequestParam(required = false) Integer conSave,
                         @RequestParam(required = false) Integer intSave,
                         @RequestParam(required = false) Integer wisSave,
                         @RequestParam(required = false) Integer chaSave,
                         @RequestParam(required = false) String skills,
                         @RequestParam(required = false) String damageVulnerabilities,
                         @RequestParam(required = false) String damageResistances,
                         @RequestParam(required = false) String damageImmunities,
                         @RequestParam(required = false) String conditionImmunities,
                         @RequestParam(required = false) String senses,
                         @RequestParam(required = false) String languages,
                         @RequestParam(required = false) String traits,
                         @RequestParam(required = false) String actions,
                         @RequestParam(required = false) String bonusActions,
                         @RequestParam(required = false) String reactions,
                         @RequestParam(required = false) String legendaryActions,
                         @RequestParam(required = false) String legendaryDescription,
                         @RequestParam(required = false) String lairActions,
                         Model model) {
        StatBlock sb = service.updateCustom(id, name, cr, type, ac, hp, speed,
                strScore, dexScore, conScore, intScore, wisScore, chaScore,
                strSave, dexSave, conSave, intSave, wisSave, chaSave,
                skills, damageVulnerabilities, damageResistances,
                damageImmunities, conditionImmunities, senses, languages);
        if (size != null) sb.setSize(size);
        if (alignment != null) sb.setAlignment(alignment);
        if (traits != null) sb.setTraits(traits);
        if (actions != null) sb.setActions(actions);
        if (bonusActions != null) sb.setBonusActions(bonusActions);
        if (reactions != null) sb.setReactions(reactions);
        if (legendaryActions != null) sb.setLegendaryActions(legendaryActions);
        if (legendaryDescription != null) sb.setLegendaryDescription(legendaryDescription);
        if (lairActions != null) sb.setLairActions(lairActions);
        enrichStatBlock(sb);
        model.addAttribute("sb", sb);
        return "library/detail";
    }

    @DeleteMapping("/statblocks/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/statblocks/{id}/clone")
    public String clone(@PathVariable UUID id, Model model) {
        StatBlock original = service.findById(id);
        StatBlock cloned = service.cloneAsCustom(id, null, original.getName() + " (custom)");
        enrichStatBlock(cloned);
        model.addAttribute("sb", cloned);
        return "library/detail";
    }

    @PutMapping("/statblocks/{id}/promote")
    public String promoteToGlobal(@PathVariable UUID id, Model model) {
        StatBlock promoted = service.promoteToGlobal(id);
        enrichStatBlock(promoted);
        model.addAttribute("sb", promoted);
        return "library/_card :: card";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    // ------- Compendium tab routes (read-only) -------

    @GetMapping("/conditions")
    public String searchConditions(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("conditions", conditionService.search(search));
        return "library/_condition-card :: condition-card-list";
    }

    @GetMapping("/rules")
    public String searchRules(@RequestParam(required = false) String search,
                              @RequestParam(required = false) String ruleset,
                              Model model) {
        model.addAttribute("rules", ruleSectionService.search(search, ruleset));
        return "library/_rule-card :: rule-card-list";
    }

    @GetMapping("/equipment")
    public String searchEquipment(@RequestParam(required = false) String search,
                                  @RequestParam(required = false) String category,
                                  Model model) {
        EquipmentItem.Category cat = null;
        if (category != null && !category.isBlank()) {
            cat = EquipmentItem.Category.valueOf(category);
        }
        model.addAttribute("equipment", equipmentItemService.search(search, cat));
        return "library/_equipment-card :: equipment-card-list";
    }

    @GetMapping("/magic-items")
    public String searchMagicItems(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) String rarity,
                                   @RequestParam(required = false) String category,
                                   Model model) {
        model.addAttribute("magicItems", magicItemService.search(search, rarity, category));
        return "library/_magic-item-card :: magic-item-card-list";
    }

    @GetMapping("/classes")
    public String searchClasses(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("classes", characterClassService.search(search));
        return "library/_class-card :: class-card-list";
    }

    @GetMapping("/species")
    public String searchSpecies(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("speciesList", speciesService.search(search));
        return "library/_species-card :: species-card-list";
    }

    @GetMapping("/backgrounds")
    public String searchBackgrounds(@RequestParam(required = false) String search, Model model) {
        model.addAttribute("backgrounds", backgroundService.search(search));
        return "library/_background-card :: background-card-list";
    }

    @GetMapping("/feats")
    public String searchFeats(@RequestParam(required = false) String search,
                              @RequestParam(required = false) String category,
                              Model model) {
        model.addAttribute("feats", featService.search(search, category));
        return "library/_feat-card :: feat-card-list";
    }

    @GetMapping("/classes/{sourceKey}")
    public String classDetail(@PathVariable String sourceKey, Model model) {
        CharacterClass cls = characterClassService.findBySourceKey(sourceKey)
                .orElseThrow(() -> new RuntimeException("Class not found: " + sourceKey));
        model.addAttribute("classDetail", cls);

        // Build levelFeatures map from features JSON
        List<Map<String, Object>> features = parseJsonList(cls.getFeatures());
        Map<Integer, List<Map<String, Object>>> levelFeatures = new TreeMap<>();
        if (features != null) {
            for (Map<String, Object> f : features) {
                Object gainedAt = f.get("gained_at");
                int level = 1;
                if (gainedAt instanceof List<?> list && !list.isEmpty()) {
                    Object first = list.get(0);
                    if (first instanceof Map<?, ?> m && m.get("level") instanceof Number n) {
                        level = n.intValue();
                    }
                }
                levelFeatures.computeIfAbsent(level, k -> new ArrayList<>()).add(f);
            }
        }
        model.addAttribute("levelFeatures", levelFeatures);

        // Parse saving throws
        model.addAttribute("savingThrows", formatSavingThrows(cls.getSavingThrows()));

        // Parse spellcasting
        model.addAttribute("spellcasting", parseJsonObject(cls.getSpellcasting()));

        // If this is a subclass, load the base class
        if (cls.getSubclassOf() != null && !cls.getSubclassOf().isBlank()) {
            characterClassService.findBySourceKey(cls.getSubclassOf())
                    .ifPresent(base -> model.addAttribute("subclass", base));
        }

        // If this is a base class, load its subclasses
        List<CharacterClass> subclasses = characterClassService
                .findAll().stream()
                .filter(c -> cls.getSourceKey().equals(c.getSubclassOf()))
                .toList();
        model.addAttribute("subclasses", subclasses);

        return "library/class-detail";
    }

    // ------- Spell routes (read-only reference) -------

    @GetMapping("/spells")
    public String searchSpells(@RequestParam(required = false) String search,
                               @RequestParam(required = false) Integer level,
                               @RequestParam(required = false) String school,
                               Model model) {
        List<Spell> spells = spellService.search(search, level, school);
        model.addAttribute("spells", spells);
        return "library/_spell-card :: spell-card-list";
    }

    private void enrichStatBlock(StatBlock sb) {
        sb.setTraitsParsed(enrichWithRolls(parseJsonArray(sb.getTraits())));
        sb.setActionsParsed(enrichWithRolls(parseJsonArray(sb.getActions())));
        sb.setBonusActionsParsed(enrichWithRolls(parseJsonArray(sb.getBonusActions())));
        sb.setReactionsParsed(enrichWithRolls(parseJsonArray(sb.getReactions())));
        sb.setLegendaryActionsParsed(enrichWithRolls(parseJsonArray(sb.getLegendaryActions())));
        sb.setLairActionsParsed(enrichWithRolls(parseJsonArray(sb.getLairActions())));
    }

    private static final Pattern ATK_PATTERN = Pattern.compile("\\+(\\d+) to hit");
    private static final Pattern DMG_PATTERN = Pattern.compile("\\((\\d+d\\d+[-+]?\\d*)\\)");

    private List<Map<String, String>> enrichWithRolls(List<Map<String, String>> parsed) {
        for (Map<String, String> entry : parsed) {
            String name = entry.getOrDefault("name", "");
            Matcher atkM = ATK_PATTERN.matcher(name);
            if (atkM.find()) {
                entry.put("attackBonus", atkM.group(1));
            }
            Matcher dmgM = DMG_PATTERN.matcher(name);
            if (dmgM.find()) {
                entry.put("damageExpr", dmgM.group(1));
            }
        }
        return parsed;
    }

    private List<Map<String, String>> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return List.of(Map.of("name", "(parse error)", "description", json));
        }
    }

    private List<Map<String, Object>> parseJsonList(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> parseJsonObject(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private String formatSavingThrows(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(json);
            List<String> parts = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode n : node) {
                    if (n.isObject() && n.has("name")) parts.add(n.get("name").asText());
                    else if (n.isObject() && n.has("ability")) parts.add(n.get("ability").asText());
                    else if (n.isTextual()) parts.add(n.asText());
                }
            }
            return parts.isEmpty() ? null : String.join(", ", parts);
        } catch (Exception e) {
            return null;
        }
    }
}
