package dev.hendrikhoemberg.dmhelper.handout.service;

import dev.hendrikhoemberg.dmhelper.adventure.service.SceneRefCleaner;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import dev.hendrikhoemberg.dmhelper.common.NotFoundException;
import dev.hendrikhoemberg.dmhelper.handout.data.Handout;
import dev.hendrikhoemberg.dmhelper.handout.data.HandoutRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class HandoutService {

    private final HandoutRepository handoutRepository;
    private final CampaignRepository campaignRepository;
    private final SceneRefCleaner sceneRefCleaner;
    private final Path filesDir;

    public HandoutService(HandoutRepository handoutRepository,
                          CampaignRepository campaignRepository,
                          SceneRefCleaner sceneRefCleaner,
                          @Value("${user.home}") String userHome) {
        this.handoutRepository = handoutRepository;
        this.campaignRepository = campaignRepository;
        this.sceneRefCleaner = sceneRefCleaner;
        this.filesDir = Path.of(userHome, ".dmhelper", "files");
    }

    @Transactional(readOnly = true)
    public List<Handout> findByCampaignId(UUID campaignId) {
        return handoutRepository.findByCampaignIdOrderByTitleAsc(campaignId);
    }

    @Transactional(readOnly = true)
    public Handout findById(UUID id) {
        return handoutRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Handout not found: " + id));
    }

    public Handout create(UUID campaignId, String title, String tags, MultipartFile file) throws IOException {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new NotFoundException("Campaign not found: " + campaignId));

        Handout handout = new Handout();
        handout.setCampaign(campaign);
        handout.setTitle(title != null && !title.isBlank() ? title.trim() : file.getOriginalFilename());
        handout.setTags(tags != null ? tags.trim() : "");
        handout.setContentType(file.getContentType());

        UUID fileId = UUID.randomUUID();
        String extension = getExtension(file.getOriginalFilename());
        String fileName = fileId + (extension.isEmpty() ? "" : "." + extension);
        handout.setFileName(fileName);

        storeFile(file, fileName);

        return handoutRepository.save(handout);
    }

    public Handout update(UUID id, String title, String tags) {
        Handout handout = findById(id);
        if (title != null && !title.isBlank()) {
            handout.setTitle(title.trim());
        }
        if (tags != null) {
            handout.setTags(tags.trim());
        }
        return handoutRepository.save(handout);
    }

    public Handout setPresented(UUID id, boolean presented) {
        Handout handout = findById(id);
        handout.setPresented(presented);
        if (presented) {
            handout.setDmOnly(false);
        }
        return handoutRepository.save(handout);
    }

    public Handout setDmOnly(UUID id, boolean dmOnly) {
        Handout handout = findById(id);
        handout.setDmOnly(dmOnly);
        return handoutRepository.save(handout);
    }

    public void delete(UUID id) {
        sceneRefCleaner.detachHandout(id);
        Handout handout = findById(id);
        try {
            Path filePath = filesDir.resolve(handout.getFileName());
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // log and continue — orphaned file is harmless
        }
        handoutRepository.delete(handout);
    }

    public byte[] getFileContent(UUID id) throws IOException {
        Handout handout = findById(id);
        Path filePath = filesDir.resolve(handout.getFileName());
        if (!Files.exists(filePath)) {
            throw new NotFoundException("Handout file not found: " + handout.getFileName());
        }
        return Files.readAllBytes(filePath);
    }

    private void storeFile(MultipartFile file, String fileName) throws IOException {
        Files.createDirectories(filesDir);
        Path target = filesDir.resolve(fileName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
