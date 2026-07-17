package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

@Component
public class CustomContentSupport {

    public String slugify(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    public void assertCustom(ContentSource source) {
        if (source != ContentSource.CUSTOM) {
            throw new IllegalArgumentException("Only custom content can be modified");
        }
    }

    public ContentProvenance defaultForCreate(LicenseClassification license) {
        ContentProvenance p = new ContentProvenance();
        p.setLicenseClassification(license != null ? license : LicenseClassification.ORIGINAL);
        p.setExtractionConfidence(SourceAnnotationConfidence.HIGH);
        return p;
    }

    public ContentProvenance defaultForSrdClone() {
        return defaultForCreate(LicenseClassification.SRD);
    }

    public ContentProvenance provenanceOrDefault(ContentProvenance provenanceOrNull) {
        return provenanceOrNull != null ? provenanceOrNull : defaultForCreate(LicenseClassification.ORIGINAL);
    }

    /**
     * Reject sourceKey collisions with SRD rows and with same-scope custom rows.
     */
    public void assertAvailableSourceKey(String sourceKey,
                                         UUID campaignIdOrNull,
                                         Predicate<String> srdKeyExists,
                                         BiPredicate<ContentSource, String> globalCustomKeyExists,
                                         BiPredicate<UUID, String> campaignKeyExists) {
        if (sourceKey == null || sourceKey.isBlank()) {
            throw new IllegalArgumentException("sourceKey is required");
        }
        if (srdKeyExists.test(sourceKey)) {
            throw new IllegalArgumentException("sourceKey collides with SRD content: " + sourceKey);
        }
        if (campaignIdOrNull == null) {
            if (globalCustomKeyExists.test(ContentSource.CUSTOM, sourceKey)) {
                throw new IllegalArgumentException("sourceKey already used by global custom content: " + sourceKey);
            }
        } else if (campaignKeyExists.test(campaignIdOrNull, sourceKey)) {
            throw new IllegalArgumentException("sourceKey already used in this campaign: " + sourceKey);
        }
    }
}
