package dev.hendrikhoemberg.dmhelper.library.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.catalog.CampaignCatalogService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlockRepository;
import org.springframework.stereotype.Component;

@Component
public class StatBlockReferenceResolver {

    private final StatBlockRepository repository;

    public StatBlockReferenceResolver(StatBlockRepository repository) {
        this.repository = repository;
    }

    public ContentReference referenceFor(StatBlock statBlock, CampaignExportContext context) {
        return statBlock.getSource() == StatBlock.Source.SRD
                ? context.catalogRef(CampaignContentType.STATBLOCK, statBlock.getSourceKey())
                : context.packageRef(CampaignContentType.STATBLOCK,
                        statBlock.getId(), statBlock.getName());
    }

    public StatBlock resolve(ContentReference reference, CampaignImportContext context) {
        if (reference.scope() == ContentReference.Scope.PACKAGE) {
            return context.require(reference, CampaignContentType.STATBLOCK, StatBlock.class);
        }
        if (reference.type() != CampaignContentType.STATBLOCK) {
            throw new IllegalArgumentException("Expected type STATBLOCK but reference has type " + reference.type());
        }
        if (!CampaignCatalogService.RULESET.equals(reference.ruleset())) {
            throw new IllegalArgumentException("Unsupported statblock ruleset: " + reference.ruleset());
        }
        return repository.findBySourceAndSourceKey(StatBlock.Source.SRD, reference.sourceKey())
                .orElseThrow(() -> new IllegalStateException(
                        "No SRD statblock for catalog key " + reference.sourceKey()));
    }
}
