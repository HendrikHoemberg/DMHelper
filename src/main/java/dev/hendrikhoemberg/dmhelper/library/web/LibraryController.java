package dev.hendrikhoemberg.dmhelper.library.web;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import dev.hendrikhoemberg.dmhelper.library.service.SpellService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/library")
public class LibraryController {

    private final StatBlockService service;
    private final SpellService spellService;
    private final ObjectMapper objectMapper;

    public LibraryController(StatBlockService service, SpellService spellService) {
        this.service = service;
        this.spellService = spellService;
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
        model.addAttribute("campaignId", sb.getCampaignId());
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
                         @RequestParam(required = false) String hpValue,
                         @RequestParam(required = false) String speedValue,
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
                hpValue, speedValue,
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
                         @RequestParam(required = false) String hpValue,
                         @RequestParam(required = false) String speedValue,
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
                hpValue, speedValue,
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

    // ------- SRD key catalog -------

    @GetMapping("/statblocks/srd-keys")
    @ResponseBody
    public List<String> srdKeys() {
        return service.findAll().stream()
                .filter(sb -> sb.getSource() == StatBlock.Source.SRD)
                .map(StatBlock::getSourceKey)
                .sorted()
                .toList();
    }

    private void enrichStatBlock(StatBlock sb) {
        sb.setTraitsParsed(parseJsonArray(sb.getTraits()));
        sb.setActionsParsed(parseJsonArray(sb.getActions()));
        sb.setBonusActionsParsed(parseJsonArray(sb.getBonusActions()));
        sb.setReactionsParsed(parseJsonArray(sb.getReactions()));
        sb.setLegendaryActionsParsed(parseJsonArray(sb.getLegendaryActions()));
        sb.setLairActionsParsed(parseJsonArray(sb.getLairActions()));
    }

    private List<Map<String, String>> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            return List.of(Map.of("name", "(parse error)", "description", json));
        }
    }
}
