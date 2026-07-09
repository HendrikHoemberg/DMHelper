package dev.hendrikhoemberg.dmhelper.common.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

@Component
public class DataModelMigration {

    private static final Logger log = LoggerFactory.getLogger(DataModelMigration.class);

    @PersistenceContext
    private EntityManager em;

    @PostConstruct
    @Transactional
    public void migrateRuleSectionBody() {
        try {
            int updated = em.createNativeQuery(
                    "UPDATE rule_section SET body = description WHERE body IS NULL AND description IS NOT NULL")
                    .executeUpdate();
            if (updated > 0) {
                log.info("Migrated {} rule_section rows: description -> body", updated);
            }
        } catch (Exception e) {
            log.debug("RuleSection migration skipped (column may not exist on fresh install): {}", e.getMessage());
        }
    }

    @PostConstruct
    @Transactional
    public void migrateStatBlockCampaign() {
        try {
            int updated = em.createNativeQuery(
                    "UPDATE stat_block SET campaign_id_fk = campaign_id WHERE campaign_id_fk IS NULL AND campaign_id IS NOT NULL")
                    .executeUpdate();
            if (updated > 0) {
                log.info("Migrated {} stat_block rows: campaign_id -> campaign_id_fk", updated);
            }
        } catch (Exception e) {
            log.debug("StatBlock campaign migration skipped: {}", e.getMessage());
        }
    }
}
