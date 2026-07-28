package dev.hendrikhoemberg.dmhelper.config;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

class V26MigrationTest {

    @Test
    void v26BackfillsCombatantTokenPlacements() throws Exception {
        String url = "jdbc:h2:mem:flyway-v26-test;DB_CLOSE_DELAY=-1";

        // Step 1: Migrate to V25
        Flyway.configure()
                .dataSource(url, "sa", "")
                .target(MigrationVersion.fromVersion("25"))
                .load()
                .migrate();

        // Step 2: Insert test data (campaign → game_map → encounter → token → combatant)
        try (var conn = DriverManager.getConnection(url, "sa", "");
             var st = conn.createStatement()) {

            st.execute("""
                insert into campaign (id, name, created_at, milestone_leveling, settings)
                values ('00000000-0000-0000-0000-000000000001', 'Test Campaign',
                        '2026-01-01 00:00:00', false, '{}')
                """);

            st.execute("""
                insert into game_map (id, campaign_id, name, grid_width, grid_height, cell_size_px,
                                       show_grid, sort_order, version, grid_type, movement_mode)
                values ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
                        'Test Map', 30, 20, 48, false, 0, 0, 'SQUARE', 'WALK')
                """);

            st.execute("""
                insert into encounter (id, campaign_id, map_id, name, status,
                                        active_turn_index, lair_action_triggered, round,
                                        log_sequence, combat_phase)
                values ('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
                        '20000000-0000-0000-0000-000000000001', 'Test Encounter', 'PLANNED',
                        -1, false, 0, 0, 'SETUP')
                """);

            st.execute("""
                insert into token (id, map_id, name, kind, positionx, positiony,
                                    size_cols, size_rows, color, hidden, current_hp, max_hp, dead)
                values ('10000000-0000-0000-0000-000000000001', '20000000-0000-0000-0000-000000000001',
                        'Goblin', 'MONSTER', 144, 96, 1, 1, '#55aa55', false, 3, 7, false)
                """);

            st.execute("""
                insert into combatant (id, encounter_id, name, max_hp, current_hp, kind,
                                        sort_order, token_id,
                                        concentration_check_pending, defeated, group_leader, hidden,
                                        legendary_actions_max, legendary_actions_used,
                                        legendary_resistances_max, legendary_resistances_used,
                                        temp_hp, tie_breaker)
                values ('30000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001',
                        'Goblin', 7, 3, 'MONSTER', 1, '10000000-0000-0000-0000-000000000001',
                        false, false, false, false,
                        0, 0, 0, 0, 0, 0)
                """);

            // Combatant without token_id - should NOT get a placement
            st.execute("""
                insert into combatant (id, encounter_id, name, max_hp, current_hp, kind,
                                        sort_order, token_id,
                                        concentration_check_pending, defeated, group_leader, hidden,
                                        legendary_actions_max, legendary_actions_used,
                                        legendary_resistances_max, legendary_resistances_used,
                                        temp_hp, tie_breaker)
                values ('30000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000001',
                        'No Token Goblin', 5, 5, 'MONSTER', 2, null,
                        false, false, false, false,
                        0, 0, 0, 0, 0, 0)
                """);
        }

        // Step 3: Run V26 migration
        Flyway.configure()
                .dataSource(url, "sa", "")
                .target(MigrationVersion.fromVersion("26"))
                .load()
                .migrate();

        // Step 4: Verify results
        try (var conn = DriverManager.getConnection(url, "sa", "");
             var st = conn.createStatement()) {

            // Placement was created with correct geometry
            var rs = st.executeQuery("""
                select count(*) from encounter_token_placement
                where combatant_id = '30000000-0000-0000-0000-000000000001'
                  and position_x = 144 and position_y = 96
                """);
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);

            // Combatant without token_id should NOT have a placement
            var noTokenCount = st.executeQuery(
                "select count(*) from encounter_token_placement where combatant_id = '30000000-0000-0000-0000-000000000002'");
            assertThat(noTokenCount.next()).isTrue();
            assertThat(noTokenCount.getInt(1)).isZero();

            // Legacy columns still exist (removed in V27)
            var combatantCols = st.executeQuery(
                "select count(*) from information_schema.columns " +
                "where table_name = 'COMBATANT' and column_name = 'TOKEN_ID'");
            assertThat(combatantCols.next()).isTrue();
            assertThat(combatantCols.getInt(1)).isEqualTo(1);

            var tokenCols = st.executeQuery(
                "select count(*) from information_schema.columns " +
                "where table_name = 'TOKEN' and column_name = 'CURRENT_HP'");
            assertThat(tokenCols.next()).isTrue();
            assertThat(tokenCols.getInt(1)).isEqualTo(1);
        }

        // Step 5: Run V27 migration
        Flyway.configure()
                .dataSource(url, "sa", "")
                .load()
                .migrate();

        // Step 6: Verify V27 removed legacy columns
        try (var conn = DriverManager.getConnection(url, "sa", "");
             var st = conn.createStatement()) {

            var combatantTokenCol = st.executeQuery(
                "select count(*) from information_schema.columns " +
                "where table_name = 'COMBATANT' and column_name = 'TOKEN_ID'");
            assertThat(combatantTokenCol.next()).isTrue();
            assertThat(combatantTokenCol.getInt(1)).isZero();

            var tokenHpCol = st.executeQuery(
                "select count(*) from information_schema.columns " +
                "where table_name = 'TOKEN' and column_name = 'CURRENT_HP'");
            assertThat(tokenHpCol.next()).isTrue();
            assertThat(tokenHpCol.getInt(1)).isZero();

            var tokenMaxHpCol = st.executeQuery(
                "select count(*) from information_schema.columns " +
                "where table_name = 'TOKEN' and column_name = 'MAX_HP'");
            assertThat(tokenMaxHpCol.next()).isTrue();
            assertThat(tokenMaxHpCol.getInt(1)).isZero();

            var tokenDeadCol = st.executeQuery(
                "select count(*) from information_schema.columns " +
                "where table_name = 'TOKEN' and column_name = 'DEAD'");
            assertThat(tokenDeadCol.next()).isTrue();
            assertThat(tokenDeadCol.getInt(1)).isZero();
        }
    }
}
