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
    private final CustomContentSupport customContentSupport;
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
                             FeatService featService,
                             CustomContentSupport customContentSupport) {
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
        this.customContentSupport = customContentSupport;
        this.objectMapper = new ObjectMapper();
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "monsters") String tab,
                       @RequestParam(required = false, defaultValue = "") String search,
                       Model model) {
        Set<String> tabs = Set.of("monsters", "spells", "conditions", "rules", "equipment",
                "magic-items", "classes", "species", "backgrounds", "feats");
        String initialTab = tabs.contains(tab) ? tab : "monsters";
        model.addAttribute("initialTab", initialTab);
        model.addAttribute("initialSearch", search);
        model.addAttribute("activeCategory", CATEGORY_NAMES.get(initialTab)[0]);
        model.addAttribute("activeCategorySingular", CATEGORY_NAMES.get(initialTab)[1]);
        model.addAttribute("activeCategoryNewHref", CATEGORY_NAMES.get(initialTab)[2]);
        return "library/list";
    }

    /** Display names, creation-action names, and creation routes for the ten compendium categories. */
    private static final Map<String, String[]> CATEGORY_NAMES = Map.of(
            "monsters", new String[]{"Monsters", "Monster", "/library/statblocks/new"},
            "spells", new String[]{"Spells", "Spell", "/library/spells/new"},
            "conditions", new String[]{"Conditions", "Condition", "/library/conditions/new"},
            "rules", new String[]{"Rules", "Rule", "/library/rules/new"},
            "equipment", new String[]{"Equipment", "Equipment", "/library/equipment/new"},
            "magic-items", new String[]{"Magic Items", "Magic Item", "/library/magic-items/new"},
            "classes", new String[]{"Classes", "Class", "/library/classes/new"},
            "species", new String[]{"Species", "Species", "/library/species/new"},
            "backgrounds", new String[]{"Backgrounds", "Background", "/library/backgrounds/new"},
            "feats", new String[]{"Feats", "Feat", "/library/feats/new"});

    @GetMapping("/statblocks")
    public String search(@RequestParam(required = false) String search,
                         @RequestParam(required = false) String cr,
                         @RequestParam(required = false) String type,
                         @RequestParam(required = false) String source,
                         @RequestParam(required = false, defaultValue = "60") int limit,
                         Model model) {
        ContentSource sourceEnum = null;
        if (source != null && !source.isBlank()) {
            sourceEnum = ContentSource.valueOf(source);
        }
        List<StatBlock> results = service.search(sourceEnum, cr, type, search);
        model.addAttribute("statblocks", results);
        // When the source filter is on, every badge on screen says the same thing.
        model.addAttribute("sourceFiltered", sourceEnum != null);
        model.addAttribute("cap", limit);
        model.addAttribute("filterSearch", search);
        model.addAttribute("filterCr", cr);
        model.addAttribute("filterType", type);
        model.addAttribute("filterSource", source);
        return "library/_card :: card-list";
    }

    @GetMapping("/statblocks/{id}")
    public String detail(@PathVariable UUID id, Model model) {
        StatBlock sb = service.findById(id);
        enrichStatBlock(sb);
        model.addAttribute("sb", sb);
        model.addAttribute("campaignId", sb.getCampaignId());
        return "library/detail";
    }

    /** The statblock as a side sheet over the library, so browsing never loses its place (§5.3). */
    @GetMapping("/statblocks/{id}/sheet")
    public String sheet(@PathVariable UUID id, Model model) {
        StatBlock sb = service.findById(id);
        enrichStatBlock(sb);
        model.addAttribute("sb", sb);
        return "library/_sheet :: sheet";
    }

    /**
     * The library's form routes are reached by ordinary links, so each answers with a whole
     * page: a bare fragment arrives without a {@code <head>} and renders unstyled.
     */
    private String formPage(Model model, String fragment, String title) {
        model.addAttribute("formFragment", fragment);
        model.addAttribute("formTitle", title);
        return "library/form";
    }

    @GetMapping("/statblocks/new")
    public String newForm(Model model) {
        model.addAttribute("sb", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_form :: form", "New Statblock");
    }

    @GetMapping("/statblocks/{id}/edit")
    public String editForm(@PathVariable UUID id, Model model) {
        StatBlock sb = service.findById(id);
        model.addAttribute("sb", sb);
        model.addAttribute("campaignId", sb.getCampaign() != null ? sb.getCampaign().getId() : null);
        return formPage(model, "library/_form :: form", "Edit " + sb.getName());
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
                         @RequestParam(required = false) String lairActions) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        StatBlock sb = service.createCustom(campaignId, name, cr, type, ac, hp, speed,
                strScore, dexScore, conScore, intScore, wisScore, chaScore,
                strSave, dexSave, conSave, intSave, wisSave, chaSave,
                skills, damageVulnerabilities, damageResistances,
                damageImmunities, conditionImmunities, senses, languages,
                null, null);
        if (size != null) sb.setSize(size);
        if (alignment != null) sb.setAlignment(alignment);
        if (traits != null) sb.setTraits(traits);
        if (actions != null) sb.setActions(actions);
        if (bonusActions != null) sb.setBonusActions(bonusActions);
        if (reactions != null) sb.setReactions(reactions);
        if (legendaryActions != null) sb.setLegendaryActions(legendaryActions);
        if (legendaryDescription != null) sb.setLegendaryDescription(legendaryDescription);
        if (lairActions != null) sb.setLairActions(lairActions);
        return "redirect:/library/statblocks/" + sb.getId();
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

    // ------- Shared helpers -------

    private <T> String detailResponse(T entity, String attr, String view, Model model) {
        model.addAttribute(attr, entity);
        if (entity instanceof Spell s) { model.addAttribute("campaignId", s.getCampaign() != null ? s.getCampaign().getId() : null); }
        else if (entity instanceof Condition c) { model.addAttribute("campaignId", c.getCampaign() != null ? c.getCampaign().getId() : null); }
        else if (entity instanceof RuleSection r) { model.addAttribute("campaignId", r.getCampaign() != null ? r.getCampaign().getId() : null); }
        else if (entity instanceof EquipmentItem e) { model.addAttribute("campaignId", e.getCampaign() != null ? e.getCampaign().getId() : null); }
        else if (entity instanceof MagicItem m) { model.addAttribute("campaignId", m.getCampaign() != null ? m.getCampaign().getId() : null); }
        else if (entity instanceof CharacterClass c) { model.addAttribute("campaignId", c.getCampaign() != null ? c.getCampaign().getId() : null); }
        else if (entity instanceof Species s) { model.addAttribute("campaignId", s.getCampaign() != null ? s.getCampaign().getId() : null); }
        else if (entity instanceof Background b) { model.addAttribute("campaignId", b.getCampaign() != null ? b.getCampaign().getId() : null); }
        else if (entity instanceof Feat f) { model.addAttribute("campaignId", f.getCampaign() != null ? f.getCampaign().getId() : null); }
        return view;
    }

    private ContentSource resolveSource(Object entity) {
        if (entity instanceof Spell s) return s.getSource();
        if (entity instanceof Condition c) return c.getSource();
        if (entity instanceof RuleSection r) return r.getSource();
        if (entity instanceof EquipmentItem e) return e.getSource();
        if (entity instanceof MagicItem m) return m.getSource();
        if (entity instanceof CharacterClass c) return c.getSource();
        if (entity instanceof Species s) return s.getSource();
        if (entity instanceof Background b) return b.getSource();
        if (entity instanceof Feat f) return f.getSource();
        return null;
    }

    // ------- Spell routes -------

    @GetMapping("/spells/new")
    public String newSpellForm(Model model) {
        model.addAttribute("spell", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_spell-form :: spell-form", "New Spell");
    }

    @GetMapping("/spells/{id}")
    public String spellDetail(@PathVariable UUID id, Model model) {
        Spell spell = spellService.findById(id);
        return detailResponse(spell, "spell", "library/spell-detail", model);
    }

    @GetMapping("/spells/{id}/edit")
    public String editSpellForm(@PathVariable UUID id, Model model) {
        Spell spell = spellService.findById(id);
        customContentSupport.assertCustom(spell.getSource());
        model.addAttribute("spell", spell);
        model.addAttribute("campaignId", spell.getCampaign() != null ? spell.getCampaign().getId() : null);
        return formPage(model, "library/_spell-form :: spell-form", "Edit " + spell.getName());
    }

    @PostMapping("/spells")
    public String createSpell(@RequestParam(required = false) UUID campaignId,
                               @RequestParam String name,
                               @RequestParam(defaultValue = "0") int level,
                               @RequestParam(required = false) String school,
                               @RequestParam(required = false) String castingTime,
                               @RequestParam(required = false) String range,
                               @RequestParam(required = false) String components,
                               @RequestParam(required = false) String duration,
                               @RequestParam(required = false) String description,
                               @RequestParam(required = false) String higherLevel,
                               @RequestParam(defaultValue = "false") boolean ritual,
                               @RequestParam(defaultValue = "false") boolean concentration) {
        SpellService.SpellWrite request = new SpellService.SpellWrite(
                name, level, school, castingTime, range, components, duration,
                description, higherLevel, ritual, concentration, null);
        Spell spell = spellService.createCustom(campaignId, request, null);
        return "redirect:/library/spells/" + spell.getId();
    }

    @PutMapping("/spells/{id}")
    public String updateSpell(@PathVariable UUID id,
                               @RequestParam String name,
                               @RequestParam(defaultValue = "0") int level,
                               @RequestParam(required = false) String school,
                               @RequestParam(required = false) String castingTime,
                               @RequestParam(required = false) String range,
                               @RequestParam(required = false) String components,
                               @RequestParam(required = false) String duration,
                               @RequestParam(required = false) String description,
                               @RequestParam(required = false) String higherLevel,
                               @RequestParam(defaultValue = "false") boolean ritual,
                               @RequestParam(defaultValue = "false") boolean concentration,
                               Model model) {
        SpellService.SpellWrite request = new SpellService.SpellWrite(
                name, level, school, castingTime, range, components, duration,
                description, higherLevel, ritual, concentration, null);
        Spell spell = spellService.updateCustom(id, request, null);
        return detailResponse(spell, "spell", "library/spell-detail", model);
    }

    @DeleteMapping("/spells/{id}")
    public ResponseEntity<Void> deleteSpell(@PathVariable UUID id) {
        spellService.deleteCustom(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/spells/{id}/clone")
    public String cloneSpell(@PathVariable UUID id, Model model) {
        Spell cloned = spellService.cloneAsCustom(id, null, null);
        return detailResponse(cloned, "spell", "library/spell-detail", model);
    }

    @PutMapping("/spells/{id}/promote")
    public String promoteSpell(@PathVariable UUID id, Model model) {
        Spell promoted = spellService.promoteToGlobal(id);
        model.addAttribute("spell", promoted);
        return "library/spell-detail";
    }

    // ------- Condition routes -------

    @GetMapping("/conditions/new")
    public String newConditionForm(Model model) {
        model.addAttribute("condition", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_condition-form :: condition-form", "New Condition");
    }

    @GetMapping("/conditions/{id}")
    public String conditionDetail(@PathVariable UUID id, Model model) {
        Condition condition = conditionService.findById(id);
        return detailResponse(condition, "condition", "library/condition-detail", model);
    }

    @GetMapping("/conditions/{id}/edit")
    public String editConditionForm(@PathVariable UUID id, Model model) {
        Condition condition = conditionService.findById(id);
        customContentSupport.assertCustom(condition.getSource());
        model.addAttribute("condition", condition);
        model.addAttribute("campaignId", condition.getCampaign() != null ? condition.getCampaign().getId() : null);
        return formPage(model, "library/_condition-form :: condition-form", "Edit " + condition.getName());
    }

    @PostMapping("/conditions")
    public String createCondition(@RequestParam(required = false) UUID campaignId,
                                   @RequestParam String name,
                                   @RequestParam(required = false) String description) {
        ConditionService.ConditionWrite request = new ConditionService.ConditionWrite(name, description, null);
        Condition condition = conditionService.createCustom(campaignId, request, null);
        return "redirect:/library/conditions/" + condition.getId();
    }

    @PutMapping("/conditions/{id}")
    public String updateCondition(@PathVariable UUID id,
                                   @RequestParam String name,
                                   @RequestParam(required = false) String description,
                                   Model model) {
        ConditionService.ConditionWrite request = new ConditionService.ConditionWrite(name, description, null);
        Condition condition = conditionService.updateCustom(id, request, null);
        return detailResponse(condition, "condition", "library/condition-detail", model);
    }

    @DeleteMapping("/conditions/{id}")
    public ResponseEntity<Void> deleteCondition(@PathVariable UUID id) {
        conditionService.deleteCustom(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/conditions/{id}/clone")
    public String cloneCondition(@PathVariable UUID id, Model model) {
        Condition cloned = conditionService.cloneAsCustom(id, null, null);
        return detailResponse(cloned, "condition", "library/condition-detail", model);
    }

    @PutMapping("/conditions/{id}/promote")
    public String promoteCondition(@PathVariable UUID id, Model model) {
        Condition promoted = conditionService.promoteToGlobal(id);
        model.addAttribute("condition", promoted);
        return "library/condition-detail";
    }

    // ------- Rule routes -------

    @GetMapping("/rules/new")
    public String newRuleForm(Model model) {
        model.addAttribute("rule", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_rule-form :: rule-form", "New Rule");
    }

    @GetMapping("/rules/{id}")
    public String ruleDetail(@PathVariable UUID id, Model model) {
        RuleSection rule = ruleSectionService.findById(id);
        return detailResponse(rule, "rule", "library/rule-detail", model);
    }

    @GetMapping("/rules/{id}/edit")
    public String editRuleForm(@PathVariable UUID id, Model model) {
        RuleSection rule = ruleSectionService.findById(id);
        customContentSupport.assertCustom(rule.getSource());
        model.addAttribute("rule", rule);
        model.addAttribute("campaignId", rule.getCampaign() != null ? rule.getCampaign().getId() : null);
        return formPage(model, "library/_rule-form :: rule-form", "Edit " + rule.getName());
    }

    @PostMapping("/rules")
    public String createRule(@RequestParam(required = false) UUID campaignId,
                              @RequestParam String name,
                              @RequestParam(required = false) String body,
                              @RequestParam(required = false) String ruleset) {
        RuleSectionService.RuleSectionWrite request = new RuleSectionService.RuleSectionWrite(
                name, body, null, null, ruleset, null, null);
        RuleSection rule = ruleSectionService.createCustom(campaignId, request, null);
        return "redirect:/library/rules/" + rule.getId();
    }

    @PutMapping("/rules/{id}")
    public String updateRule(@PathVariable UUID id,
                              @RequestParam String name,
                              @RequestParam(required = false) String body,
                              @RequestParam(required = false) String ruleset,
                              Model model) {
        RuleSectionService.RuleSectionWrite request = new RuleSectionService.RuleSectionWrite(
                name, body, null, null, ruleset, null, null);
        RuleSection rule = ruleSectionService.updateCustom(id, request, null);
        return detailResponse(rule, "rule", "library/rule-detail", model);
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable UUID id) {
        ruleSectionService.deleteCustom(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/rules/{id}/clone")
    public String cloneRule(@PathVariable UUID id, Model model) {
        RuleSection cloned = ruleSectionService.cloneAsCustom(id, null, null);
        return detailResponse(cloned, "rule", "library/rule-detail", model);
    }

    @PutMapping("/rules/{id}/promote")
    public String promoteRule(@PathVariable UUID id, Model model) {
        RuleSection promoted = ruleSectionService.promoteToGlobal(id);
        model.addAttribute("rule", promoted);
        return "library/rule-detail";
    }

    // ------- Equipment routes -------

    @GetMapping("/equipment/new")
    public String newEquipmentForm(Model model) {
        model.addAttribute("equipmentItem", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_equipment-form :: equipment-form", "New Equipment");
    }

    @GetMapping("/equipment/{id}")
    public String equipmentDetail(@PathVariable UUID id, Model model) {
        EquipmentItem item = equipmentItemService.findById(id);
        return detailResponse(item, "equipmentItem", "library/equipment-detail", model);
    }

    @GetMapping("/equipment/{id}/edit")
    public String editEquipmentForm(@PathVariable UUID id, Model model) {
        EquipmentItem item = equipmentItemService.findById(id);
        customContentSupport.assertCustom(item.getSource());
        model.addAttribute("equipmentItem", item);
        model.addAttribute("campaignId", item.getCampaign() != null ? item.getCampaign().getId() : null);
        return formPage(model, "library/_equipment-form :: equipment-form", "Edit " + item.getName());
    }

    @PostMapping("/equipment")
    public String createEquipment(@RequestParam(required = false) UUID campaignId,
                                   @RequestParam String name,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String cost,
                                   @RequestParam(required = false) String weight,
                                   @RequestParam(required = false) String description) {
        EquipmentItem.Category cat = category != null && !category.isBlank()
                ? EquipmentItem.Category.valueOf(category) : EquipmentItem.Category.GEAR;
        EquipmentItemService.EquipmentItemWrite request = new EquipmentItemService.EquipmentItemWrite(
                name, cat, cost, weight, null, description, null);
        EquipmentItem item = equipmentItemService.createCustom(campaignId, request, null);
        return "redirect:/library/equipment/" + item.getId();
    }

    @PutMapping("/equipment/{id}")
    public String updateEquipment(@PathVariable UUID id,
                                   @RequestParam String name,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String cost,
                                   @RequestParam(required = false) String weight,
                                   @RequestParam(required = false) String description,
                                   Model model) {
        EquipmentItem.Category cat = category != null && !category.isBlank()
                ? EquipmentItem.Category.valueOf(category) : EquipmentItem.Category.GEAR;
        EquipmentItemService.EquipmentItemWrite request = new EquipmentItemService.EquipmentItemWrite(
                name, cat, cost, weight, null, description, null);
        EquipmentItem item = equipmentItemService.updateCustom(id, request, null);
        return detailResponse(item, "equipmentItem", "library/equipment-detail", model);
    }

    @DeleteMapping("/equipment/{id}")
    public ResponseEntity<Void> deleteEquipment(@PathVariable UUID id) {
        equipmentItemService.deleteCustom(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/equipment/{id}/clone")
    public String cloneEquipment(@PathVariable UUID id, Model model) {
        EquipmentItem cloned = equipmentItemService.cloneAsCustom(id, null, null);
        return detailResponse(cloned, "equipmentItem", "library/equipment-detail", model);
    }

    @PutMapping("/equipment/{id}/promote")
    public String promoteEquipment(@PathVariable UUID id, Model model) {
        EquipmentItem promoted = equipmentItemService.promoteToGlobal(id);
        model.addAttribute("equipmentItem", promoted);
        return "library/equipment-detail";
    }

    // ------- Magic item routes -------

    @GetMapping("/magic-items/new")
    public String newMagicItemForm(Model model) {
        model.addAttribute("magicItem", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_magic-item-form :: magic-item-form", "New Magic Item");
    }

    @GetMapping("/magic-items/{id}")
    public String magicItemDetail(@PathVariable UUID id, Model model) {
        MagicItem item = magicItemService.findById(id);
        return detailResponse(item, "magicItem", "library/magic-item-detail", model);
    }

    @GetMapping("/magic-items/{id}/edit")
    public String editMagicItemForm(@PathVariable UUID id, Model model) {
        MagicItem item = magicItemService.findById(id);
        customContentSupport.assertCustom(item.getSource());
        model.addAttribute("magicItem", item);
        model.addAttribute("campaignId", item.getCampaign() != null ? item.getCampaign().getId() : null);
        return formPage(model, "library/_magic-item-form :: magic-item-form", "Edit " + item.getName());
    }

    @PostMapping("/magic-items")
    public String createMagicItem(@RequestParam(required = false) UUID campaignId,
                                   @RequestParam String name,
                                   @RequestParam(required = false) String rarity,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(required = false) String cost,
                                   @RequestParam(required = false) String weight,
                                   @RequestParam(defaultValue = "false") boolean requiresAttunement,
                                   @RequestParam(required = false) String attunementDetail) {
        MagicItemService.MagicItemWrite request = new MagicItemService.MagicItemWrite(
                name, rarity, category, type, description, weight, cost,
                requiresAttunement, attunementDetail, null);
        MagicItem item = magicItemService.createCustom(campaignId, request, null);
        return "redirect:/library/magic-items/" + item.getId();
    }

    @PutMapping("/magic-items/{id}")
    public String updateMagicItem(@PathVariable UUID id,
                                   @RequestParam String name,
                                   @RequestParam(required = false) String rarity,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) String description,
                                   @RequestParam(required = false) String cost,
                                   @RequestParam(required = false) String weight,
                                   @RequestParam(defaultValue = "false") boolean requiresAttunement,
                                   @RequestParam(required = false) String attunementDetail,
                                   Model model) {
        MagicItemService.MagicItemWrite request = new MagicItemService.MagicItemWrite(
                name, rarity, category, type, description, weight, cost,
                requiresAttunement, attunementDetail, null);
        MagicItem item = magicItemService.updateCustom(id, request, null);
        return detailResponse(item, "magicItem", "library/magic-item-detail", model);
    }

    @DeleteMapping("/magic-items/{id}")
    public ResponseEntity<Void> deleteMagicItem(@PathVariable UUID id) {
        magicItemService.deleteCustom(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/magic-items/{id}/clone")
    public String cloneMagicItem(@PathVariable UUID id, Model model) {
        MagicItem cloned = magicItemService.cloneAsCustom(id, null, null);
        return detailResponse(cloned, "magicItem", "library/magic-item-detail", model);
    }

    @PutMapping("/magic-items/{id}/promote")
    public String promoteMagicItem(@PathVariable UUID id, Model model) {
        MagicItem promoted = magicItemService.promoteToGlobal(id);
        model.addAttribute("magicItem", promoted);
        return "library/magic-item-detail";
    }

    // ------- Class routes -------

    @GetMapping("/classes/new")
    public String newClassForm(Model model) {
        model.addAttribute("classDetail", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_class-form :: class-form", "New Class");
    }

    @GetMapping("/classes/id/{id}")
    public String classDetailById(@PathVariable UUID id, Model model) {
        CharacterClass cls = characterClassService.findById(id);
        return classDetailModel(cls, model);
    }

    private String classDetailModel(CharacterClass cls, Model model) {
        model.addAttribute("classDetail", cls);
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
        model.addAttribute("savingThrows", formatSavingThrows(cls.getSavingThrows()));
        model.addAttribute("spellcasting", parseJsonObject(cls.getSpellcasting()));
        if (cls.getSubclassOf() != null && !cls.getSubclassOf().isBlank()) {
            characterClassService.findBySourceKey(cls.getSubclassOf())
                    .ifPresent(base -> model.addAttribute("subclass", base));
        }
        List<CharacterClass> subclasses = characterClassService.findAll().stream()
                .filter(c -> cls.getSourceKey().equals(c.getSubclassOf())).toList();
        model.addAttribute("subclasses", subclasses);
        model.addAttribute("campaignId", cls.getCampaign() != null ? cls.getCampaign().getId() : null);
        return "library/class-detail";
    }

    @GetMapping("/classes/{id}/edit")
    public String editClassForm(@PathVariable UUID id, Model model) {
        CharacterClass cls = characterClassService.findById(id);
        customContentSupport.assertCustom(cls.getSource());
        model.addAttribute("classDetail", cls);
        model.addAttribute("campaignId", cls.getCampaign() != null ? cls.getCampaign().getId() : null);
        return formPage(model, "library/_class-form :: class-form", "Edit " + cls.getName());
    }

    @PostMapping("/classes")
    public String createClass(@RequestParam(required = false) UUID campaignId,
                               @RequestParam String name,
                               @RequestParam(required = false) String hitDie,
                               @RequestParam(required = false) String description) {
        CharacterClassService.CharacterClassWrite request = new CharacterClassService.CharacterClassWrite(
                name, hitDie, null, null, null, null, description, null, null);
        CharacterClass cls = characterClassService.createCustom(campaignId, request, null);
        return "redirect:/library/classes/id/" + cls.getId();
    }

    @PutMapping("/classes/{id}")
    public String updateClass(@PathVariable UUID id,
                               @RequestParam String name,
                               @RequestParam(required = false) String hitDie,
                               @RequestParam(required = false) String description,
                               Model model) {
        CharacterClassService.CharacterClassWrite request = new CharacterClassService.CharacterClassWrite(
                name, hitDie, null, null, null, null, description, null, null);
        CharacterClass cls = characterClassService.updateCustom(id, request, null);
        return classDetailModel(cls, model);
    }

    @DeleteMapping("/classes/{id}")
    public ResponseEntity<Void> deleteClass(@PathVariable UUID id) {
        characterClassService.deleteCustom(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/classes/{id}/clone")
    public String cloneClass(@PathVariable UUID id, Model model) {
        CharacterClass cloned = characterClassService.cloneAsCustom(id, null, null);
        return classDetailModel(cloned, model);
    }

    @PutMapping("/classes/{id}/promote")
    public String promoteClass(@PathVariable UUID id, Model model) {
        CharacterClass promoted = characterClassService.promoteToGlobal(id);
        return classDetailModel(promoted, model);
    }

    // ------- Species routes -------

    @GetMapping("/species/new")
    public String newSpeciesForm(Model model) {
        model.addAttribute("species", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_species-form :: species-form", "New Species");
    }

    @GetMapping("/species/{id}")
    public String speciesDetail(@PathVariable UUID id, Model model) {
        Species species = speciesService.findById(id);
        return detailResponse(species, "species", "library/species-detail", model);
    }

    @GetMapping("/species/{id}/edit")
    public String editSpeciesForm(@PathVariable UUID id, Model model) {
        Species species = speciesService.findById(id);
        customContentSupport.assertCustom(species.getSource());
        model.addAttribute("species", species);
        model.addAttribute("campaignId", species.getCampaign() != null ? species.getCampaign().getId() : null);
        return formPage(model, "library/_species-form :: species-form", "Edit " + species.getName());
    }

    @PostMapping("/species")
    public String createSpecies(@RequestParam(required = false) UUID campaignId,
                                 @RequestParam String name,
                                 @RequestParam(required = false) String size,
                                 @RequestParam(required = false) String speed,
                                 @RequestParam(required = false) String description) {
        SpeciesService.SpeciesWrite request = new SpeciesService.SpeciesWrite(
                name, size, speed, null, description, null);
        Species species = speciesService.createCustom(campaignId, request, null);
        return "redirect:/library/species/" + species.getId();
    }

    @PutMapping("/species/{id}")
    public String updateSpecies(@PathVariable UUID id,
                                 @RequestParam String name,
                                 @RequestParam(required = false) String size,
                                 @RequestParam(required = false) String speed,
                                 @RequestParam(required = false) String description,
                                 Model model) {
        SpeciesService.SpeciesWrite request = new SpeciesService.SpeciesWrite(
                name, size, speed, null, description, null);
        Species species = speciesService.updateCustom(id, request, null);
        return detailResponse(species, "species", "library/species-detail", model);
    }

    @DeleteMapping("/species/{id}")
    public ResponseEntity<Void> deleteSpecies(@PathVariable UUID id) {
        speciesService.deleteCustom(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/species/{id}/clone")
    public String cloneSpecies(@PathVariable UUID id, Model model) {
        Species cloned = speciesService.cloneAsCustom(id, null, null);
        return detailResponse(cloned, "species", "library/species-detail", model);
    }

    @PutMapping("/species/{id}/promote")
    public String promoteSpecies(@PathVariable UUID id, Model model) {
        Species promoted = speciesService.promoteToGlobal(id);
        model.addAttribute("species", promoted);
        return "library/species-detail";
    }

    // ------- Background routes -------

    @GetMapping("/backgrounds/new")
    public String newBackgroundForm(Model model) {
        model.addAttribute("background", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_background-form :: background-form", "New Background");
    }

    @GetMapping("/backgrounds/{id}")
    public String backgroundDetail(@PathVariable UUID id, Model model) {
        Background bg = backgroundService.findById(id);
        return detailResponse(bg, "background", "library/background-detail", model);
    }

    @GetMapping("/backgrounds/{id}/edit")
    public String editBackgroundForm(@PathVariable UUID id, Model model) {
        Background bg = backgroundService.findById(id);
        customContentSupport.assertCustom(bg.getSource());
        model.addAttribute("background", bg);
        model.addAttribute("campaignId", bg.getCampaign() != null ? bg.getCampaign().getId() : null);
        return formPage(model, "library/_background-form :: background-form", "Edit " + bg.getName());
    }

    @PostMapping("/backgrounds")
    public String createBackground(@RequestParam(required = false) UUID campaignId,
                                    @RequestParam String name,
                                    @RequestParam(required = false) String description) {
        BackgroundService.BackgroundWrite request = new BackgroundService.BackgroundWrite(
                name, null, null, null, null, description, null, null);
        Background bg = backgroundService.createCustom(campaignId, request, null);
        return "redirect:/library/backgrounds/" + bg.getId();
    }

    @PutMapping("/backgrounds/{id}")
    public String updateBackground(@PathVariable UUID id,
                                    @RequestParam String name,
                                    @RequestParam(required = false) String description,
                                    Model model) {
        BackgroundService.BackgroundWrite request = new BackgroundService.BackgroundWrite(
                name, null, null, null, null, description, null, null);
        Background bg = backgroundService.updateCustom(id, request, null);
        return detailResponse(bg, "background", "library/background-detail", model);
    }

    @DeleteMapping("/backgrounds/{id}")
    public ResponseEntity<Void> deleteBackground(@PathVariable UUID id) {
        backgroundService.deleteCustom(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/backgrounds/{id}/clone")
    public String cloneBackground(@PathVariable UUID id, Model model) {
        Background cloned = backgroundService.cloneAsCustom(id, null, null);
        return detailResponse(cloned, "background", "library/background-detail", model);
    }

    @PutMapping("/backgrounds/{id}/promote")
    public String promoteBackground(@PathVariable UUID id, Model model) {
        Background promoted = backgroundService.promoteToGlobal(id);
        model.addAttribute("background", promoted);
        return "library/background-detail";
    }

    // ------- Feat routes -------

    @GetMapping("/feats/new")
    public String newFeatForm(Model model) {
        model.addAttribute("feat", null);
        model.addAttribute("campaignId", null);
        return formPage(model, "library/_feat-form :: feat-form", "New Feat");
    }

    @GetMapping("/feats/{id}")
    public String featDetail(@PathVariable UUID id, Model model) {
        Feat feat = featService.findById(id);
        return detailResponse(feat, "feat", "library/feat-detail", model);
    }

    @GetMapping("/feats/{id}/edit")
    public String editFeatForm(@PathVariable UUID id, Model model) {
        Feat feat = featService.findById(id);
        customContentSupport.assertCustom(feat.getSource());
        model.addAttribute("feat", feat);
        model.addAttribute("campaignId", feat.getCampaign() != null ? feat.getCampaign().getId() : null);
        return formPage(model, "library/_feat-form :: feat-form", "Edit " + feat.getName());
    }

    @PostMapping("/feats")
    public String createFeat(@RequestParam(required = false) UUID campaignId,
                              @RequestParam String name,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String prerequisite,
                              @RequestParam(required = false) String benefit) {
        FeatService.FeatWrite request = new FeatService.FeatWrite(name, category, prerequisite, benefit, null);
        Feat feat = featService.createCustom(campaignId, request, null);
        return "redirect:/library/feats/" + feat.getId();
    }

    @PutMapping("/feats/{id}")
    public String updateFeat(@PathVariable UUID id,
                              @RequestParam String name,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String prerequisite,
                              @RequestParam(required = false) String benefit,
                              Model model) {
        FeatService.FeatWrite request = new FeatService.FeatWrite(name, category, prerequisite, benefit, null);
        Feat feat = featService.updateCustom(id, request, null);
        return detailResponse(feat, "feat", "library/feat-detail", model);
    }

    @DeleteMapping("/feats/{id}")
    public ResponseEntity<Void> deleteFeat(@PathVariable UUID id) {
        featService.deleteCustom(id);
        return ResponseEntity.ok().header("HX-Redirect", "/library").build();
    }

    @PostMapping("/feats/{id}/clone")
    public String cloneFeat(@PathVariable UUID id, Model model) {
        Feat cloned = featService.cloneAsCustom(id, null, null);
        return detailResponse(cloned, "feat", "library/feat-detail", model);
    }

    @PutMapping("/feats/{id}/promote")
    public String promoteFeat(@PathVariable UUID id, Model model) {
        Feat promoted = featService.promoteToGlobal(id);
        model.addAttribute("feat", promoted);
        return "library/feat-detail";
    }

    // ------- Compendium tab routes (read-only) -------

    @GetMapping("/conditions")
    public String searchConditions(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) String source,
                                   Model model) {
        ContentSource sourceEnum = source != null && !source.isBlank() ? ContentSource.valueOf(source) : null;
        List<Condition> results = sourceEnum != null
                ? conditionService.search(sourceEnum, null, search)
                : conditionService.search(search);
        model.addAttribute("conditions", results);
        model.addAttribute("sourceFiltered", sourceEnum != null);
        return "library/_condition-card :: condition-card-list";
    }

    @GetMapping("/rules")
    public String searchRules(@RequestParam(required = false) String search,
                              @RequestParam(required = false) String ruleset,
                              @RequestParam(required = false) String source,
                              Model model) {
        ContentSource sourceEnum = source != null && !source.isBlank() ? ContentSource.valueOf(source) : null;
        List<RuleSection> results;
        if (sourceEnum != null) {
            results = ruleSectionService.search(sourceEnum, null, search);
        } else {
            results = ruleSectionService.search(search, ruleset);
        }
        model.addAttribute("rules", results);
        model.addAttribute("sourceFiltered", sourceEnum != null);
        return "library/_rule-card :: rule-card-list";
    }

    @GetMapping("/equipment")
    public String searchEquipment(@RequestParam(required = false) String search,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String source,
                                  Model model) {
        ContentSource sourceEnum = source != null && !source.isBlank() ? ContentSource.valueOf(source) : null;
        List<EquipmentItem> results;
        if (sourceEnum != null) {
            results = equipmentItemService.search(sourceEnum, null, search);
        } else {
            EquipmentItem.Category cat = category != null && !category.isBlank()
                    ? EquipmentItem.Category.valueOf(category) : null;
            results = equipmentItemService.search(search, cat);
        }
        model.addAttribute("equipment", results);
        model.addAttribute("sourceFiltered", sourceEnum != null);
        return "library/_equipment-card :: equipment-card-list";
    }

    @GetMapping("/magic-items")
    public String searchMagicItems(@RequestParam(required = false) String search,
                                   @RequestParam(required = false) String rarity,
                                   @RequestParam(required = false) String category,
                                   @RequestParam(required = false) String source,
                                   Model model) {
        ContentSource sourceEnum = source != null && !source.isBlank() ? ContentSource.valueOf(source) : null;
        List<MagicItem> results;
        if (sourceEnum != null) {
            results = magicItemService.search(sourceEnum, null, search);
        } else {
            results = magicItemService.search(search, rarity, category);
        }
        model.addAttribute("magicItems", results);
        model.addAttribute("sourceFiltered", sourceEnum != null);
        return "library/_magic-item-card :: magic-item-card-list";
    }

    @GetMapping("/classes")
    public String searchClasses(@RequestParam(required = false) String search,
                                @RequestParam(required = false) String source,
                                Model model) {
        ContentSource sourceEnum = source != null && !source.isBlank() ? ContentSource.valueOf(source) : null;
        List<CharacterClass> results = sourceEnum != null
                ? characterClassService.search(sourceEnum, null, search)
                : characterClassService.search(search);
        model.addAttribute("classes", results);
        model.addAttribute("sourceFiltered", sourceEnum != null);
        return "library/_class-card :: class-card-list";
    }

    @GetMapping("/species")
    public String searchSpecies(@RequestParam(required = false) String search,
                                @RequestParam(required = false) String source,
                                Model model) {
        ContentSource sourceEnum = source != null && !source.isBlank() ? ContentSource.valueOf(source) : null;
        List<Species> results = sourceEnum != null
                ? speciesService.search(sourceEnum, null, search)
                : speciesService.search(search);
        model.addAttribute("speciesList", results);
        model.addAttribute("sourceFiltered", sourceEnum != null);
        return "library/_species-card :: species-card-list";
    }

    @GetMapping("/backgrounds")
    public String searchBackgrounds(@RequestParam(required = false) String search,
                                    @RequestParam(required = false) String source,
                                    Model model) {
        ContentSource sourceEnum = source != null && !source.isBlank() ? ContentSource.valueOf(source) : null;
        List<Background> results = sourceEnum != null
                ? backgroundService.search(sourceEnum, null, search)
                : backgroundService.search(search);
        model.addAttribute("backgrounds", results);
        model.addAttribute("sourceFiltered", sourceEnum != null);
        return "library/_background-card :: background-card-list";
    }

    @GetMapping("/feats")
    public String searchFeats(@RequestParam(required = false) String search,
                              @RequestParam(required = false) String category,
                              @RequestParam(required = false) String source,
                              Model model) {
        ContentSource sourceEnum = source != null && !source.isBlank() ? ContentSource.valueOf(source) : null;
        List<Feat> results;
        if (sourceEnum != null) {
            results = featService.search(sourceEnum, null, search);
        } else {
            results = featService.search(search, category);
        }
        model.addAttribute("feats", results);
        model.addAttribute("sourceFiltered", sourceEnum != null);
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
                               @RequestParam(required = false) String source,
                               Model model) {
        ContentSource sourceEnum = source != null && !source.isBlank() ? ContentSource.valueOf(source) : null;
        List<Spell> spells;
        if (sourceEnum != null) {
            spells = spellService.search(sourceEnum, null, search);
            if (level != null) spells = spells.stream().filter(s -> s.getLevel() == level).toList();
            if (school != null && !school.isBlank()) spells = spells.stream().filter(s -> school.equals(s.getSchool())).toList();
        } else {
            spells = spellService.search(search, level, school);
        }
        model.addAttribute("spells", spells);
        model.addAttribute("sourceFiltered", sourceEnum != null);
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
