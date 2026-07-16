package dev.hendrikhoemberg.dmhelper.campaign.packagev2.section;

/**
 * Observes transactional import boundaries. The production implementation is intentionally
 * inert; the hook makes rollback guarantees testable without teaching domain adapters about
 * fault injection.
 */
public interface CampaignImportObserver {

    void afterSection(String sectionName);

    void beforeDeferredSetter(String description);
}
