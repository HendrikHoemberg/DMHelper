package dev.hendrikhoemberg.dmhelper.library.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.library.data.StatBlock;
import org.springframework.stereotype.Component;

@Component
public class StatBlockReferenceResolver {

    private final LibraryContentReferenceResolver libraryRefs;

    public StatBlockReferenceResolver(LibraryContentReferenceResolver libraryRefs) {
        this.libraryRefs = libraryRefs;
    }

    public ContentReference referenceFor(StatBlock statBlock, CampaignExportContext context) {
        return libraryRefs.referenceFor(statBlock, context);
    }

    public StatBlock resolve(ContentReference reference, CampaignImportContext context) {
        return libraryRefs.resolveStatBlock(reference, context);
    }
}
