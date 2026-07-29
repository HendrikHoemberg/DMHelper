package dev.hendrikhoemberg.dmhelper.handout.packagev2;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.CampaignManifestV2.HandoutDto;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignExportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignImportContext;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignManifestAssembler;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionExporter;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.section.CampaignSectionImporter;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import dev.hendrikhoemberg.dmhelper.handout.service.HandoutService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class HandoutSectionAdapter implements CampaignSectionExporter, CampaignSectionImporter {

    private final HandoutService handoutService;
    private final HandoutRepository handoutRepository;

    public HandoutSectionAdapter(HandoutService handoutService, HandoutRepository handoutRepository) {
        this.handoutService = handoutService;
        this.handoutRepository = handoutRepository;
    }

    @Override
    public String sectionName() {
        return "Handout";
    }

    @Override
    public int order() {
        return 500;
    }

    @Override
    public void exportSection(CampaignExportContext context, CampaignManifestAssembler target) {
        var handouts = handoutRepository.findByCampaignIdOrderByTitleAsc(context.campaignId());

        List<HandoutDto> dtos = handouts.stream()
                .map(h -> exportHandout(h, context))
                .toList();
        target.handouts(dtos);
    }

    private HandoutDto exportHandout(Handout handout, CampaignExportContext context) {
        String key = context.key(CampaignContentType.HANDOUT, handout.getId(), handout.getTitle());

        byte[] fileContent;
        try {
            fileContent = handoutService.getFileContent(handout.getId());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read handout file: " + handout.getId(), e);
        }

        String assetKey = "handout-" + handout.getId().toString().substring(0, 8);
        String sha256 = sha256(fileContent);

        var descriptor = new AssetDescriptor(
                assetKey, "assets/handouts/" + assetKey,
                handout.getContentType(), fileContent.length, sha256, handout.getFileName()
        );
        context.assets().add(descriptor, fileContent);

        List<String> tagsList = parseTags(handout.getTags());

        return new HandoutDto(
                key, handout.getTitle(), tagsList,
                assetKey, handout.getContentType(),
                handout.isDmOnly(), handout.isPresented(),
                null, null, null,
                handout.getAssetKind().name()
        );
    }

    @Override
    public void importSection(CampaignManifestV2 source, CampaignImportContext context) {
        List<HandoutDto> dtos = source.handouts();
        if (dtos == null) return;

        var campaign = context.campaign();

        for (HandoutDto dto : dtos) {
            byte[] bytes;
            try {
                Path assetPath = context.requireAsset(dto.assetRef());
                bytes = Files.readAllBytes(assetPath);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read handout asset: " + dto.assetRef(), e);
            }

            String tags = dto.tags() != null ? String.join(", ", dto.tags()) : "";

            Handout handout;
            try {
                handout = handoutService.createImported(
                        campaign.getId(),
                        dto.title(),
                        tags,
                        dto.title() + extensionFor(dto.contentType()),
                        dto.contentType(),
                        bytes
                );
            } catch (IOException e) {
                throw new RuntimeException("Failed to create imported handout: " + dto.title(), e);
            }

            handout.setDmOnly(dto.dmOnly());
            handout.setPresented(dto.presented());
            handout.setAssetKind(kindOf(dto));
            handoutRepository.save(handout);

            context.register(CampaignContentType.HANDOUT, dto.key(), handout, handout.getId());
        }
    }

    private static Handout.AssetKind kindOf(HandoutDto dto) {
        if (dto.assetKind() != null && !dto.assetKind().isBlank()) {
            return Handout.AssetKind.valueOf(dto.assetKind());
        }
        return Handout.AssetKind.SOURCE_PAGE;
    }

    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        var result = new ArrayList<String>();
        for (var part : tags.split(",")) {
            var trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String extensionFor(String contentType) {
        if (contentType == null) return "";
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    private static String sha256(byte[] bytes) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
