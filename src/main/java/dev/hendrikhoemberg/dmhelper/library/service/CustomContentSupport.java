package dev.hendrikhoemberg.dmhelper.library.service;

import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence;
import dev.hendrikhoemberg.dmhelper.library.data.ContentProvenance;
import dev.hendrikhoemberg.dmhelper.library.data.ContentSource;
import dev.hendrikhoemberg.dmhelper.library.data.LicenseClassification;
import org.springframework.stereotype.Component;

import java.util.Locale;

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
}
