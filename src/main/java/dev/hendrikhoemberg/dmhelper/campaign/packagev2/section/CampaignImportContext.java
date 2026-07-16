package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

import dev.hendrikhoemberg.dmhelper.campaign.data.Campaign;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignContentType;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.key.CampaignPackageKeyService;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.ContentReference;
import dev.hendrikhoemberg.dmhelper.campaign.packagev2.preview.PendingCampaignImport;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CampaignImportContext {

    private Campaign campaign;
    private boolean campaignSet;
    private final UUID campaignId;
    private final CampaignPackageKeyService keyService;
    private final PendingCampaignImport pending;
    private final Map<String, Object> entities = new LinkedHashMap<>();
    private final List<DeferredTask> deferred = new ArrayList<>();

    public CampaignImportContext(UUID campaignId, CampaignPackageKeyService keyService, PendingCampaignImport pending) {
        this.campaignId = campaignId;
        this.keyService = keyService;
        this.pending = pending;
    }

    public Campaign campaign() {
        if (!campaignSet) {
            throw new IllegalStateException("Campaign not yet set");
        }
        return campaign;
    }

    public PendingCampaignImport pending() {
        return pending;
    }

    public void setCampaign(Campaign campaign) {
        if (campaignSet) {
            throw new IllegalStateException("Campaign already set");
        }
        this.campaign = campaign;
        this.campaignSet = true;
    }

    public void register(CampaignContentType type, String key, Object entity, UUID entityId) {
        String mapKey = type.name() + ":" + key;
        if (entities.containsKey(mapKey)) {
            throw new IllegalArgumentException("Duplicate entity: " + mapKey);
        }
        entities.put(mapKey, entity);
        keyService.bindImported(campaignId, type, entityId, key);
    }

    @SuppressWarnings("unchecked")
    public <T> T require(ContentReference reference, CampaignContentType expectedType, Class<T> javaType) {
        if (reference == null) {
            throw new IllegalArgumentException("Reference must not be null");
        }
        if (reference.scope() == ContentReference.Scope.CATALOG) {
            throw new IllegalArgumentException(
                    "Catalog references must be resolved by the calling adapter");
        }
        if (reference.type() != expectedType) {
            throw new IllegalArgumentException(
                    "Expected type " + expectedType + " but reference has type " + reference.type());
        }
        String mapKey = reference.type().name() + ":" + reference.key();
        Object entity = entities.get(mapKey);
        if (entity == null) {
            throw new IllegalStateException("No registered entity for reference " + mapKey);
        }
        if (!javaType.isInstance(entity)) {
            throw new IllegalArgumentException(
                    "Entity is not of expected type " + javaType.getSimpleName());
        }
        return (T) entity;
    }

    public Path requireAsset(String assetKey) {
        Path path = pending.result().assetsByKey().get(assetKey);
        if (path == null) {
            throw new IllegalStateException("Asset not found: " + assetKey);
        }
        return path;
    }

    public void defer(String description, Runnable setter) {
        deferred.add(new DeferredTask(description, setter));
    }

    public void runDeferred() {
        for (var task : deferred) {
            task.setter.run();
        }
        deferred.clear();
    }

    private record DeferredTask(String description, Runnable setter) {}
}
