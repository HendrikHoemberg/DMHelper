package dev.hendrikhoemberg.dmhelper.library.data;

import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

@Embeddable
public class ContentProvenance {

    @Column(name = "prov_source_title", length = 255)
    private String sourceTitle;

    @Column(name = "prov_edition_version", length = 100)
    private String editionVersion;

    @Column(name = "prov_source_locator", length = 500)
    private String sourceLocator;

    @Enumerated(EnumType.STRING)
    @Column(name = "prov_license", length = 30)
    private LicenseClassification licenseClassification;

    @Column(name = "prov_imported_at")
    private Instant importedAt;

    @Column(name = "prov_converter_id", length = 100)
    private String converterId;

    @Column(name = "prov_converter_version", length = 50)
    private String converterVersion;

    @Column(name = "prov_source_hash", length = 128)
    private String sourceHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "prov_confidence", length = 20)
    private SourceAnnotationConfidence extractionConfidence;

    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }

    public String getEditionVersion() { return editionVersion; }
    public void setEditionVersion(String editionVersion) { this.editionVersion = editionVersion; }

    public String getSourceLocator() { return sourceLocator; }
    public void setSourceLocator(String sourceLocator) { this.sourceLocator = sourceLocator; }

    public LicenseClassification getLicenseClassification() { return licenseClassification; }
    public void setLicenseClassification(LicenseClassification licenseClassification) { this.licenseClassification = licenseClassification; }

    public Instant getImportedAt() { return importedAt; }
    public void setImportedAt(Instant importedAt) { this.importedAt = importedAt; }

    public String getConverterId() { return converterId; }
    public void setConverterId(String converterId) { this.converterId = converterId; }

    public String getConverterVersion() { return converterVersion; }
    public void setConverterVersion(String converterVersion) { this.converterVersion = converterVersion; }

    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }

    public SourceAnnotationConfidence getExtractionConfidence() { return extractionConfidence; }
    public void setExtractionConfidence(SourceAnnotationConfidence extractionConfidence) { this.extractionConfidence = extractionConfidence; }
}
