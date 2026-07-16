package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

import org.springframework.stereotype.Component;

@Component
final class NoOpCampaignImportObserver implements CampaignImportObserver {

    @Override
    public void afterSection(String sectionName) {
    }

    @Override
    public void beforeDeferredSetter(String description) {
    }
}
