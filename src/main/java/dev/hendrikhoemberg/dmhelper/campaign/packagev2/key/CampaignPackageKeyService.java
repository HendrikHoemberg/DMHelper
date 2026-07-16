package dev.hendrikhoemberg.dmhelper.campaign.packagev2.key;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Transactional
public class CampaignPackageKeyService {

    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,99}$");

    private final CampaignPackageKeyRepository repository;

    public CampaignPackageKeyService(CampaignPackageKeyRepository repository) {
        this.repository = repository;
    }

    public String getOrCreate(UUID campaignId, CampaignContentType type, UUID entityId, String displayName) {
        String entityTypeStr = type.name();
        Optional<CampaignPackageKey> existing = repository.findByCampaignIdAndEntityTypeAndEntityId(
                campaignId, entityTypeStr, entityId);
        if (existing.isPresent()) {
            return existing.get().getPackageKey();
        }
        String key = PackageKeyGenerator.generate(type, displayName, entityId.toString());
        CampaignPackageKey row = new CampaignPackageKey(campaignId, type, entityId, key);
        repository.save(row);
        return key;
    }

    public void bindImported(UUID campaignId, CampaignContentType type, UUID entityId, String packageKey) {
        if (!KEY_PATTERN.matcher(packageKey).matches()) {
            throw new IllegalArgumentException("Invalid package key format: " + packageKey);
        }
        String entityTypeStr = type.name();
        Optional<CampaignPackageKey> existing = repository.findByCampaignIdAndEntityTypeAndEntityId(
                campaignId, entityTypeStr, entityId);
        if (existing.isPresent()) {
            if (existing.get().getPackageKey().equals(packageKey)) {
                return;
            }
            throw new IllegalArgumentException(
                    "Entity already bound to key '" + existing.get().getPackageKey() + "'");
        }
        if (repository.existsByCampaignIdAndEntityTypeAndPackageKey(campaignId, entityTypeStr, packageKey)) {
            throw new IllegalArgumentException(
                    "Key '" + packageKey + "' already used for type " + entityTypeStr);
        }
        CampaignPackageKey row = new CampaignPackageKey(campaignId, type, entityId, packageKey);
        repository.save(row);
    }

    public void deleteBindings(UUID campaignId, CampaignContentType type, java.util.Collection<UUID> entityIds) {
        if (!entityIds.isEmpty())
            repository.deleteByCampaignIdAndEntityTypeAndEntityIdIn(campaignId, type.name(), entityIds);
    }

    @Transactional(readOnly = true)
    public Optional<String> find(UUID campaignId, CampaignContentType type, UUID entityId) {
        return repository.findByCampaignIdAndEntityTypeAndEntityId(
                        campaignId, type.name(), entityId)
                .map(CampaignPackageKey::getPackageKey);
    }
}
