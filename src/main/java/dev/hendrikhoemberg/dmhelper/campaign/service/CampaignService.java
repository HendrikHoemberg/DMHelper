package dev.hendrikhoemberg.dmhelper.campaign.service;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.data.CampaignRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CampaignService {

    private final CampaignRepository repository;
    private final ObjectMapper objectMapper;

    public CampaignService(CampaignRepository repository) {
        this.repository = repository;
        this.objectMapper = JsonMapper.builder()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public Campaign create(String name, String description) {
        Campaign campaign = new Campaign();
        campaign.setName(name);
        campaign.setDescription(description);
        return repository.save(campaign);
    }

    @Transactional(readOnly = true)
    public List<Campaign> findAll() {
        return repository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Campaign findById(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + id));
    }

    public Campaign update(UUID id, String name, String description) {
        Campaign campaign = findById(id);
        campaign.setName(name);
        campaign.setDescription(description);
        return repository.save(campaign);
    }

    public void delete(UUID id) {
        Campaign campaign = findById(id);
        repository.delete(campaign);
    }

    @Transactional(readOnly = true)
    public String exportToJson(UUID id) {
        Campaign campaign = findById(id);
        CampaignExportDto dto = CampaignExportDto.from(campaign);
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new RuntimeException("Failed to export campaign", e);
        }
    }

    public Campaign importFromJson(String json) {
        CampaignExportDto dto;
        try {
            dto = objectMapper.readValue(json, CampaignExportDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse import JSON: " + e.getMessage(), e);
        }

        if (dto.formatVersion() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported formatVersion: " + dto.formatVersion() + ". Expected: 1");
        }

        if (dto.campaign() == null || dto.campaign().name() == null || dto.campaign().name().isBlank()) {
            throw new IllegalArgumentException("Campaign name is required");
        }

        return create(dto.campaign().name(), dto.campaign().description());
    }
}
