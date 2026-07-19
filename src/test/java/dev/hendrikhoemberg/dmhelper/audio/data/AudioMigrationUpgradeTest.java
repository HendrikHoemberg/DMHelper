package dev.hendrikhoemberg.dmhelper.audio.data;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AudioMigrationUpgradeTest {

    @Test
    void upgradesPopulatedV14DatabaseThroughV17WithoutChangingCampaignData() throws Exception {
        String url = "jdbc:h2:mem:audio-v14-upgrade;DB_CLOSE_DELAY=-1";
        Flyway.configure()
                .dataSource(url, "sa", "")
                .target(MigrationVersion.fromVersion("14"))
                .load()
                .migrate();

        UUID campaignId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var insert = connection.prepareStatement("""
                     insert into campaign
                         (id, name, description, created_at, milestone_leveling, settings)
                     values (?, ?, ?, ?, ?, ?)
                     """)) {
            insert.setObject(1, campaignId);
            insert.setString(2, "V14 survivor");
            insert.setString(3, "Must remain unchanged");
            insert.setObject(4, Instant.parse("2026-01-02T03:04:05Z"));
            insert.setBoolean(5, false);
            insert.setString(6, "{}");
            insert.executeUpdate();
        }

        Flyway.configure().dataSource(url, "sa", "").load().migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
             var campaign = connection.prepareStatement("""
                     select name, description, default_audio_cue_id
                     from campaign where id = ?
                     """);
             var columns = connection.prepareStatement("""
                     select count(*) from information_schema.columns
                     where table_name = 'SESSION_AUDIO_STATE'
                       and column_name in ('ACCEPTED_SOURCE_KIND', 'ACCEPTED_SOURCE_ID',
                                           'ACCEPTED_SOURCE_LABEL', 'PENDING_SOURCE_KIND',
                                           'PENDING_SOURCE_ID', 'PENDING_SOURCE_LABEL',
                                           'VICTORY_SOURCE_ID', 'VICTORY_SOURCE_LABEL')
                     """)) {
            campaign.setObject(1, campaignId);
            try (var result = campaign.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("name")).isEqualTo("V14 survivor");
                assertThat(result.getString("description")).isEqualTo("Must remain unchanged");
                assertThat(result.getObject("default_audio_cue_id")).isNull();
            }
            try (var result = columns.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(8);
            }
        }
    }
}
