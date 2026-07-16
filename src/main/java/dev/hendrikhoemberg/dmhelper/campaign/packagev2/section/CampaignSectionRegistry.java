package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

public class CampaignSectionRegistry {

    private final List<CampaignSectionExporter> exporters;
    private final List<CampaignSectionImporter> importers;

    public CampaignSectionRegistry(
            List<? extends CampaignSectionExporter> exporters,
            List<? extends CampaignSectionImporter> importers) {
        this.exporters = validateAndSortExporters(List.copyOf(exporters));
        this.importers = validateAndSortImporters(List.copyOf(importers));
    }

    public List<CampaignSectionExporter> exporters() {
        return exporters;
    }

    public List<CampaignSectionImporter> importers() {
        return importers;
    }

    private static List<CampaignSectionExporter> validateAndSortExporters(
            List<? extends CampaignSectionExporter> items) {
        var names = new HashSet<String>();
        var orders = new HashSet<Integer>();
        for (var item : items) {
            if (!names.add(item.sectionName())) {
                throw new IllegalArgumentException(
                        "Duplicate section name among exporters: " + item.sectionName());
            }
            if (!orders.add(item.order())) {
                throw new IllegalArgumentException(
                        "Duplicate order among exporters: " + item.order());
            }
        }
        var sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(CampaignSectionExporter::order));
        return List.copyOf(sorted);
    }

    private static List<CampaignSectionImporter> validateAndSortImporters(
            List<? extends CampaignSectionImporter> items) {
        var names = new HashSet<String>();
        var orders = new HashSet<Integer>();
        for (var item : items) {
            if (!names.add(item.sectionName())) {
                throw new IllegalArgumentException(
                        "Duplicate section name among importers: " + item.sectionName());
            }
            if (!orders.add(item.order())) {
                throw new IllegalArgumentException(
                        "Duplicate order among importers: " + item.order());
            }
        }
        var sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(CampaignSectionImporter::order));
        return List.copyOf(sorted);
    }
}
