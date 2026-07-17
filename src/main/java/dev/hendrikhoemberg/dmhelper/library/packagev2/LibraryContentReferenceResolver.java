package dev.hendrikhoemberg.dmhelper.library.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.io.CampaignPackageException;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.CampaignImportProblem;
import dev.hendrikhoemberg.dmhelper.campaign.service.validation.ImportSeverity;
import dev.hendrikhoemberg.dmhelper.library.data.Background;
import dev.hendrikhoemberg.dmhelper.library.data.BackgroundRepository;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClass;
import dev.hendrikhoemberg.dmhelper.library.data.CharacterClassRepository;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItem;
import dev.hendrikhoemberg.dmhelper.library.data.EquipmentItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Feat;
import dev.hendrikhoemberg.dmhelper.library.data.FeatRepository;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItem;
import dev.hendrikhoemberg.dmhelper.library.data.MagicItemRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Species;
import dev.hendrikhoemberg.dmhelper.library.data.SpeciesRepository;
import dev.hendrikhoemberg.dmhelper.library.data.Spell;
import dev.hendrikhoemberg.dmhelper.library.data.SpellRepository;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Resolves library content between package (campaign-scoped CUSTOM) and catalog (SRD) references.
 * User-global CUSTOM content is not embeddable in a campaign package and is rejected on export.
 */
@Component
public class LibraryContentReferenceResolver {

    private final SpellRepository spellRepository;
    private final SpeciesRepository speciesRepository;
    private final BackgroundRepository backgroundRepository;
    private final CharacterClassRepository characterClassRepository;
    private final FeatRepository featRepository;
    private final MagicItemRepository magicItemRepository;
    private final EquipmentItemRepository equipmentItemRepository;
    private final StatBlockRepository statBlockRepository;

    public LibraryContentReferenceResolver(SpellRepository spellRepository,
                                           SpeciesRepository speciesRepository,
                                           BackgroundRepository backgroundRepository,
                                           CharacterClassRepository characterClassRepository,
                                           FeatRepository featRepository,
                                           MagicItemRepository magicItemRepository,
                                           EquipmentItemRepository equipmentItemRepository,
                                           StatBlockRepository statBlockRepository) {
        this.spellRepository = spellRepository;
        this.speciesRepository = speciesRepository;
        this.backgroundRepository = backgroundRepository;
        this.characterClassRepository = characterClassRepository;
        this.featRepository = featRepository;
        this.magicItemRepository = magicItemRepository;
        this.equipmentItemRepository = equipmentItemRepository;
        this.statBlockRepository = statBlockRepository;
    }

    public ContentReference referenceFor(ContentSource source,
                                         Campaign campaign,
                                         CampaignContentType type,
                                         UUID entityId,
                                         String displayName,
                                         String sourceKey,
                                         CampaignExportContext context) {
        if (source == ContentSource.SRD) {
            return context.catalogRef(type, sourceKey);
        }
        if (campaign == null) {
            throw nonCampaignDependency(type, sourceKey, displayName);
        }
        if (!campaign.getId().equals(context.campaignId())) {
            throw new IllegalStateException(
                    "Library entity " + type + " '" + sourceKey
                            + "' belongs to a different campaign than the export target");
        }
        return context.packageRef(type, entityId, displayName);
    }

    public ContentReference referenceFor(Spell spell, CampaignExportContext context) {
        return referenceFor(spell.getSource(), spell.getCampaign(), CampaignContentType.SPELL,
                spell.getId(), spell.getName(), spell.getSourceKey(), context);
    }

    public ContentReference referenceFor(Species species, CampaignExportContext context) {
        return referenceFor(species.getSource(), species.getCampaign(), CampaignContentType.SPECIES,
                species.getId(), species.getName(), species.getSourceKey(), context);
    }

    public ContentReference referenceFor(Background background, CampaignExportContext context) {
        return referenceFor(background.getSource(), background.getCampaign(), CampaignContentType.BACKGROUND,
                background.getId(), background.getName(), background.getSourceKey(), context);
    }

    public ContentReference referenceFor(CharacterClass characterClass, CampaignExportContext context) {
        return referenceFor(characterClass.getSource(), characterClass.getCampaign(), CampaignContentType.CLASS,
                characterClass.getId(), characterClass.getName(), characterClass.getSourceKey(), context);
    }

    public ContentReference referenceFor(Feat feat, CampaignExportContext context) {
        return referenceFor(feat.getSource(), feat.getCampaign(), CampaignContentType.FEAT,
                feat.getId(), feat.getName(), feat.getSourceKey(), context);
    }

    public ContentReference referenceFor(MagicItem item, CampaignExportContext context) {
        return referenceFor(item.getSource(), item.getCampaign(), CampaignContentType.MAGIC_ITEM,
                item.getId(), item.getName(), item.getSourceKey(), context);
    }

    public ContentReference referenceFor(EquipmentItem item, CampaignExportContext context) {
        return referenceFor(item.getSource(), item.getCampaign(), CampaignContentType.EQUIPMENT_ITEM,
                item.getId(), item.getName(), item.getSourceKey(), context);
    }

    public ContentReference referenceFor(StatBlock statBlock, CampaignExportContext context) {
        return referenceFor(statBlock.getSource(), statBlock.getCampaign(), CampaignContentType.STATBLOCK,
                statBlock.getId(), statBlock.getName(), statBlock.getSourceKey(), context);
    }

    public Spell resolveSpell(ContentReference ref, CampaignImportContext context) {
        return resolve(ref, CampaignContentType.SPELL, Spell.class, context, spellRepository::findBySourceAndSourceKey);
    }

    public Species resolveSpecies(ContentReference ref, CampaignImportContext context) {
        return resolve(ref, CampaignContentType.SPECIES, Species.class, context, speciesRepository::findBySourceAndSourceKey);
    }

    public Background resolveBackground(ContentReference ref, CampaignImportContext context) {
        return resolve(ref, CampaignContentType.BACKGROUND, Background.class, context, backgroundRepository::findBySourceAndSourceKey);
    }

    public CharacterClass resolveClass(ContentReference ref, CampaignImportContext context) {
        return resolve(ref, CampaignContentType.CLASS, CharacterClass.class, context, characterClassRepository::findBySourceAndSourceKey);
    }

    public Feat resolveFeat(ContentReference ref, CampaignImportContext context) {
        return resolve(ref, CampaignContentType.FEAT, Feat.class, context, featRepository::findBySourceAndSourceKey);
    }

    public MagicItem resolveMagicItem(ContentReference ref, CampaignImportContext context) {
        return resolve(ref, CampaignContentType.MAGIC_ITEM, MagicItem.class, context, magicItemRepository::findBySourceAndSourceKey);
    }

    public EquipmentItem resolveEquipmentItem(ContentReference ref, CampaignImportContext context) {
        return resolve(ref, CampaignContentType.EQUIPMENT_ITEM, EquipmentItem.class, context, equipmentItemRepository::findBySourceAndSourceKey);
    }

    public StatBlock resolveStatBlock(ContentReference ref, CampaignImportContext context) {
        return resolve(ref, CampaignContentType.STATBLOCK, StatBlock.class, context, statBlockRepository::findBySourceAndSourceKey);
    }

    /**
     * Resolve a class by local sourceKey for the export campaign (campaign custom → global custom → SRD).
     */
    public CharacterClass findClassForCampaign(UUID campaignId, String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return null;
        }
        return characterClassRepository.findByCampaignIdAndSourceKey(campaignId, sourceKey)
                .or(() -> characterClassRepository.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey))
                .or(() -> characterClassRepository.findBySourceAndSourceKey(ContentSource.SRD, sourceKey))
                .orElse(null);
    }

    public Feat findFeatForCampaign(UUID campaignId, String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return null;
        }
        return featRepository.findByCampaignIdAndSourceKey(campaignId, sourceKey)
                .or(() -> featRepository.findBySourceAndSourceKeyAndCampaignIsNull(ContentSource.CUSTOM, sourceKey))
                .or(() -> featRepository.findBySourceAndSourceKey(ContentSource.SRD, sourceKey))
                .orElse(null);
    }

    @FunctionalInterface
    private interface SrdLookup<T> {
        java.util.Optional<T> find(ContentSource source, String sourceKey);
    }

    private <T> T resolve(ContentReference ref,
                          CampaignContentType expectedType,
                          Class<T> clazz,
                          CampaignImportContext context,
                          SrdLookup<T> srdLookup) {
        if (ref == null) {
            return null;
        }
        if (ref.scope() == ContentReference.Scope.PACKAGE) {
            return context.require(ref, expectedType, clazz);
        }
        if (ref.type() != null && ref.type() != expectedType) {
            throw new IllegalStateException(
                    "Expected type " + expectedType + " but reference has type " + ref.type());
        }
        if (ref.ruleset() != null && !CampaignCatalogService.RULESET.equals(ref.ruleset())) {
            throw new IllegalStateException("Unsupported ruleset: " + ref.ruleset());
        }
        return srdLookup.find(ContentSource.SRD, ref.sourceKey())
                .orElseThrow(() -> new IllegalStateException(
                        "No SRD " + expectedType + " for catalog key " + ref.sourceKey()));
    }

    private static CampaignPackageException nonCampaignDependency(CampaignContentType type,
                                                                  String sourceKey,
                                                                  String displayName) {
        String label = displayName != null && !displayName.isBlank() ? displayName : sourceKey;
        return new CampaignPackageException(new CampaignImportProblem(
                ImportSeverity.ERROR,
                "NON_CAMPAIGN_CUSTOM_DEPENDENCY",
                "/library/" + type.name().toLowerCase(),
                "Campaign references user-global custom " + type + " '" + label
                        + "'. Clone it into the campaign before export.",
                "Clone the content into this campaign, or replace the reference with an SRD/catalog entry."));
    }
}
