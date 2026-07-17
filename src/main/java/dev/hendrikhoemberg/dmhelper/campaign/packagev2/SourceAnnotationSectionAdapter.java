package dev.hendrikhoemberg.dmhelper.campaign.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotation;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationConfidence;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationRepository;
import dev.hendrikhoemberg.dmhelper.campaign.data.SourceAnnotationStatus;
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
        List<SourceAnnotation> annotations =
                repo.findByCampaignIdOrderByCreatedAtAscIdAsc(context.campaignId());
        List<SourceAnnotationDto> dtos = annotations.stream().map(a -> {
            String aKey = context.key(CampaignContentType.SOURCE_ANNOTATION, a.getId(), "annotation");
            CampaignContentType ownerType = CampaignContentType.valueOf(a.getOwnerType());
            ContentReference ownerRef = context.packageRef(ownerType, a.getOwnerId(), a.getOwnerType());
            return new SourceAnnotationDto(
                    aKey,
                    ownerRef,
                    a.getFieldPath(),
                    a.getMessage(),
                    a.getConfidence() != null ? a.getConfidence().name() : null,
                    a.getSourceLocator(),
                    a.getStatus() != null ? a.getStatus().name() : null,
                    a.getResolutionNote(),
                    a.getCreatedAt());
        }).toList();
        target.annotations(dtos);
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<SourceAnnotationDto> dtos = source.annotations();
        if (dtos == null) {
            return;
        }
        var campaign = context.campaign();
        for (SourceAnnotationDto dto : dtos) {
            SourceAnnotation annotation = new SourceAnnotation();
            annotation.setCampaign(campaign);
            annotation.setFieldPath(dto.fieldPath());
            annotation.setMessage(dto.message());
            if (dto.confidence() != null) {
                annotation.setConfidence(SourceAnnotationConfidence.valueOf(dto.confidence()));
            }
            annotation.setSourceLocator(dto.sourceLocator());
            if (dto.status() != null) {
                annotation.setStatus(SourceAnnotationStatus.valueOf(dto.status()));
            }
            annotation.setResolutionNote(dto.resolutionNote());
            if (dto.createdAt() != null) {
                annotation.setCreatedAt(dto.createdAt());
            }

            if (dto.ownerRef() == null) {
                throw new IllegalArgumentException("Source annotation requires ownerRef: " + dto.key());
            }
            if (dto.ownerRef().scope() != ContentReference.Scope.PACKAGE) {
                throw new IllegalArgumentException(
                        "Source annotation ownerRef must be PACKAGE-scoped: " + dto.key());
            }
            CampaignContentType ownerType = dto.ownerRef().type();
            annotation.setOwnerType(ownerType.name());
            // Temporary placeholder until deferred resolution; column is non-null.
            annotation.setOwnerId(UUID.fromString("00000000-0000-0000-0000-000000000000"));
            repo.save(annotation);
            context.register(CampaignContentType.SOURCE_ANNOTATION, dto.key(), annotation, annotation.getId());

            ContentReference ownerRef = dto.ownerRef();
            context.defer("source-annotation-owner:" + dto.key(), () -> {
                Object owner = context.require(ownerRef, ownerType, Object.class);
                annotation.setOwnerId(entityId(owner));
                repo.save(annotation);
            });
        }
    }

    private static UUID entityId(Object entity) {
        try {
            return (UUID) entity.getClass().getMethod("getId").invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Cannot resolve package entity id for " + entity.getClass().getName(), e);
        }
    }
}
