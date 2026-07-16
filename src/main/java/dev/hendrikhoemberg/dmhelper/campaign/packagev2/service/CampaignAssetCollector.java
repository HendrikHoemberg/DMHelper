package dev.hendrikhoemberg.dmhelper.campaign.packagev2.service;

import dev.hendrikhoemberg.dmhelper.campaign.packagev2.model.AssetDescriptor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CampaignAssetCollector {

    private final Map<String, AssetDescriptor> descriptors = new LinkedHashMap<>();
    private final Map<String, byte[]> sources = new LinkedHashMap<>();

    public void add(AssetDescriptor descriptor, byte[] data) {
        String key = descriptor.key();
        if (descriptors.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate asset key: " + key);
        }
        descriptors.put(key, descriptor);
        sources.put(key, data);
    }

    public List<AssetDescriptor> assetDescriptors() {
        return List.copyOf(descriptors.values());
    }

    public Map<String, byte[]> assetSources() {
        return Collections.unmodifiableMap(sources);
    }
}
