package dev.hendrikhoemberg.dmhelper.campaign.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotation;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationRepository;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.SourceAnnotationDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SourceAnnotationSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final SourceAnnotationRepository repo;

    public SourceAnnotationSectionAdapter(SourceAnnotationRepository repo) {
        this.repo = repo;
    }

    @Override
    public String sectionName() {
        return "SourceAnnotation";
    }

    @Override
    public int order() {
        return 980;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var campaign = context.campaign();
        List<SourceAnnotation> annotations = repo.findByCampaignIdAndOwnerTypeAndOwnerId(
                campaign.getId(), "", UUID.randomUUID());
        annotations = repo.findAll();
        List<SourceAnnotationDto> dtos = annotations.stream()
                .filter(a -> a.getCampaign().getId().equals(campaign.getId()))
                .map(a -> {
                    String aKey = context.key(CampaignContentType.SOURCE_ANNOTATION, a.getId(), "annotation");
                    ContentReference ownerRef = ContentReference.packageRef(
                            CampaignContentType.valueOf(a.getOwnerType()), a.getOwnerId().toString());
                    return new SourceAnnotationDto(aKey, ownerRef, a.getFieldPath(),
                            a.getMessage(), a.getConfidence().name(), a.getSourceLocator(),
                            a.getStatus().name(), a.getResolutionNote(), a.getCreatedAt());
                }).toList();
        target.annotations(dtos);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<SourceAnnotationDto> dtos = source.annotations();
        if (dtos == null) return;
        var campaign = context.campaign();
        for (SourceAnnotationDto dto : dtos) {
            SourceAnnotation a = new SourceAnnotation();
            a.setCampaign(campaign);
            if (dto.ownerRef() != null) {
                a.setOwnerType(dto.ownerRef().type().name());
                a.setOwnerId(UUID.fromString(dto.ownerRef().key()));
            }
            a.setFieldPath(dto.fieldPath());
            a.setMessage(dto.message());
            if (dto.confidence() != null) {
                a.setConfidence(dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence.valueOf(dto.confidence()));
            }
            a.setSourceLocator(dto.sourceLocator());
            if (dto.status() != null) {
                a.setStatus(dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationStatus.valueOf(dto.status()));
            }
            a.setResolutionNote(dto.resolutionNote());
            if (dto.createdAt() != null) a.setCreatedAt(dto.createdAt());
            repo.save(a);
            context.register(CampaignContentType.SOURCE_ANNOTATION, dto.key(), a, a.getId());
        }
    }
}
