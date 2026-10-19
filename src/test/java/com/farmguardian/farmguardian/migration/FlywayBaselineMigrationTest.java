package com.farmguardian.farmguardian.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlywayBaselineMigrationTest {

    private static final List<String> BASELINE_TABLES = List.of(
            "users",
            "devices",
            "fcm_tokens",
            "origin_images",
            "refresh_tokens"
    );

    @Test
    @DisplayName("V1 기준선은 빈 DB에 한 번만 적용되고 업무 데이터를 만들지 않는다")
    void migrateBaselineIntoIsolatedEmptyDatabase() throws SQLException {
        String jdbcUrl = newIsolatedDatabaseUrl("flyway_baseline_");
        Flyway flyway = configureFlyway(jdbcUrl);

        var firstMigration = flyway.migrate();

        assertThat(firstMigration.migrationsExecuted).isEqualTo(1);
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertThat(readPublicTableNames(connection))
                    .containsAll(BASELINE_TABLES)
                    .contains("flyway_schema_history");

            for (String table : BASELINE_TABLES) {
                assertThat(countRows(connection, table)).isZero();
            }
        }

        var secondMigration = flyway.migrate();

        assertThat(secondMigration.migrationsExecuted).isZero();
    }

    @Test
    @DisplayName("이력이 없는 비어 있지 않은 DB는 자동 baseline하지 않고 거부한다")
    void refuseNonEmptyDatabaseWithoutFlywayHistory() throws SQLException {
        String jdbcUrl = newIsolatedDatabaseUrl("flyway_non_empty_");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE preexisting_table (id BIGINT PRIMARY KEY)");
        }

        Flyway flyway = configureFlyway(jdbcUrl);

        assertThatThrownBy(flyway::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("non-empty schema");

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertThat(readPublicTableNames(connection))
                    .contains("preexisting_table")
                    .doesNotContain("flyway_schema_history")
                    .doesNotContainAnyElementsOf(BASELINE_TABLES);
        }
    }

    private Flyway configureFlyway(String jdbcUrl) {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .target("1")
                .load();
    }

    private String newIsolatedDatabaseUrl(String prefix) {
        String databaseName = prefix + UUID.randomUUID().toString().replace("-", "");
        return "jdbc:h2:mem:" + databaseName
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    private List<String> readPublicTableNames(Connection connection) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, "public", "%", new String[]{"TABLE"})) {
            var tableNames = new java.util.ArrayList<String>();
            while (tables.next()) {
                tableNames.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
            return tableNames;
        }
    }

    private long countRows(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }
}
