package dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CatalogSnapshot;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.service.BackgroundService;
import dev.hendrikhoemberg.dmhelper.library.service.CharacterClassService;
import dev.hendrikhoemberg.dmhelper.library.service.ConditionService;
import dev.hendrikhoemberg.dmhelper.library.service.EquipmentItemService;
import dev.hendrikhoemberg.dmhelper.library.service.FeatService;
import dev.hendrikhoemberg.dmhelper.library.service.MagicItemService;
import dev.hendrikhoemberg.dmhelper.library.service.RuleSectionService;
import dev.hendrikhoemberg.dmhelper.library.service.SpeciesService;
import dev.hendrikhoemberg.dmhelper.library.service.SpellService;
import dev.hendrikhoemberg.dmhelper.library.service.StatBlockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import static tools.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CampaignCatalogService {

    public static final String CATALOG_VERSION = "srd-5.2-dmhelper-1";
    public static final String RULESET = "SRD_5_2";
    public static final String SOURCE = "SRD";

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

    public CampaignCatalogService(StatBlockService statBlockService,
                                   SpellService spellService,
                                   ConditionService conditionService,
                                   RuleSectionService ruleSectionService,
                                   EquipmentItemService equipmentItemService,
                                   MagicItemService magicItemService,
                                   CharacterClassService characterClassService,
                                   SpeciesService speciesService,
                                   BackgroundService backgroundService,
                                   FeatService featService) {
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
    }

    public CatalogSnapshot snapshot() {
        List<CatalogSnapshot.Entry> entries = new ArrayList<>();
        statBlockService.findAll().stream()
                .filter(sb -> sb.getSource() == ContentSource.SRD)
                .sorted(Comparator.comparing(StatBlock::getSourceKey))
                .map(sb -> new CatalogSnapshot.Entry(
                        CampaignContentType.STATBLOCK,
                        sb.getSourceKey(),
                        sb.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        spellService.findAll().stream()
                .sorted(Comparator.comparing(s -> s.getSourceKey()))
                .map(s -> new CatalogSnapshot.Entry(
                        CampaignContentType.SPELL,
                        s.getSourceKey(), s.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        conditionService.findAll().stream()
                .sorted(Comparator.comparing(c -> c.getSourceKey()))
                .map(c -> new CatalogSnapshot.Entry(
                        CampaignContentType.CONDITION,
                        c.getSourceKey(), c.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        ruleSectionService.findAll().stream()
                .sorted(Comparator.comparing(r -> r.getSourceKey()))
                .map(r -> new CatalogSnapshot.Entry(
                        CampaignContentType.RULE,
                        r.getSourceKey(), r.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        equipmentItemService.findAll().stream()
                .sorted(Comparator.comparing(e -> e.getSourceKey()))
                .map(e -> new CatalogSnapshot.Entry(
                        CampaignContentType.EQUIPMENT_ITEM,
                        e.getSourceKey(), e.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        magicItemService.findAll().stream()
                .sorted(Comparator.comparing(m -> m.getSourceKey()))
                .map(m -> new CatalogSnapshot.Entry(
                        CampaignContentType.MAGIC_ITEM,
                        m.getSourceKey(), m.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        characterClassService.findAll().stream()
                .sorted(Comparator.comparing(c -> c.getSourceKey()))
                .map(c -> new CatalogSnapshot.Entry(
                        CampaignContentType.CLASS,
                        c.getSourceKey(), c.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        speciesService.findAll().stream()
                .sorted(Comparator.comparing(s -> s.getSourceKey()))
                .map(s -> new CatalogSnapshot.Entry(
                        CampaignContentType.SPECIES,
                        s.getSourceKey(), s.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        backgroundService.findAll().stream()
                .sorted(Comparator.comparing(b -> b.getSourceKey()))
                .map(b -> new CatalogSnapshot.Entry(
                        CampaignContentType.BACKGROUND,
                        b.getSourceKey(), b.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        featService.findAll().stream()
                .sorted(Comparator.comparing(f -> f.getSourceKey()))
                .map(f -> new CatalogSnapshot.Entry(
                        CampaignContentType.FEAT,
                        f.getSourceKey(), f.getName(),
                        RULESET, SOURCE, List.of()))
                .forEach(entries::add);

        entries.sort(Comparator.comparing(CatalogSnapshot.Entry::type)
                .thenComparing(CatalogSnapshot.Entry::sourceKey));

        String hash = computeHash(entries);
        return new CatalogSnapshot(CATALOG_VERSION, hash, List.copyOf(entries));
    }

    public Optional<CatalogSnapshot.Entry> resolve(CampaignContentType type, String ruleset, String sourceKey) {
        if (!RULESET.equals(ruleset)) return Optional.empty();
        return snapshot().entries().stream()
                .filter(e -> e.type().equals(type) && e.sourceKey().equals(sourceKey))
                .findFirst();
    }

    private static String computeHash(List<CatalogSnapshot.Entry> entries) {
        try {
            JsonMapper mapper = JsonMapper.builder()
                    .enable(ORDER_MAP_ENTRIES_BY_KEYS)
                    .build();
            byte[] json = mapper.writeValueAsBytes(entries);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(json);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute catalog hash", e);
        }
    }
}
