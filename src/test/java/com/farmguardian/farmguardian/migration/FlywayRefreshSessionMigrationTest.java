package com.farmguardian.farmguardian.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("V2 Refresh Session migration 계약")
class FlywayRefreshSessionMigrationTest {

    private static final Timestamp CREATED_AT = Timestamp.valueOf("2026-01-01 00:00:00");
    private static final Timestamp EVENT_AT = Timestamp.valueOf("2026-01-01 01:00:00");
    private static final Timestamp LATER_EVENT_AT = Timestamp.valueOf("2026-01-01 02:00:00");
    private static final Timestamp EXPIRES_AT = Timestamp.valueOf("2026-01-08 00:00:00");

    private static final List<String> EXPECTED_SESSION_COLUMNS = List.of(
            "refresh_session_id",
            "user_id",
            "client_uuid",
            "family_id",
            "created_at",
            "revoked_at",
            "reuse_detected_at",
            "active_slot",
            "version"
    );

    private static final List<String> EXPECTED_HISTORY_COLUMNS = List.of(
            "refresh_token_history_id",
            "refresh_session_id",
            "jti",
            "token_hash",
            "issued_at",
            "expires_at",
            "rotated_at",
            "replaced_by_jti",
            "reuse_detected_at",
            "current_slot"
    );

    @Test
    @DisplayName("V2는 구형 원문 Token을 이관하지 않고 Session과 Token 이력을 분리한다")
    void replaceLegacyRefreshTokenTableWithSessionAndHistoryTables() throws SQLException {
        String jdbcUrl = newIsolatedDatabaseUrl("flyway_refresh_v2_");
        Flyway v1 = configureFlyway(jdbcUrl, "1");
        assertThat(v1.migrate().migrationsExecuted).isEqualTo(1);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            long userId = insertUser(connection, "legacy-refresh@farmguardian.test");
            insertLegacyRefreshToken(connection, userId);
        }

        Flyway v2 = configureFlyway(jdbcUrl, "2");
        assertThat(v2.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(v2.validateWithResult().validationSuccessful).isTrue();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            assertThat(readPublicTableNames(connection))
                    .contains("refresh_sessions", "refresh_token_history")
                    .doesNotContain("refresh_tokens");
            assertThat(readColumns(connection, "refresh_sessions").keySet())
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_SESSION_COLUMNS);
            assertThat(readColumns(connection, "refresh_token_history").keySet())
                    .containsExactlyInAnyOrderElementsOf(EXPECTED_HISTORY_COLUMNS);

            assertThat(countRows(connection, "users")).isEqualTo(1);
            assertThat(countRows(connection, "refresh_sessions")).isZero();
            assertThat(countRows(connection, "refresh_token_history")).isZero();

            assertSessionColumns(connection);
            assertHistoryColumns(connection);

            assertThat(readPrimaryKeyColumns(connection, "refresh_sessions"))
                    .containsExactly("refresh_session_id");
            assertThat(readPrimaryKeyColumns(connection, "refresh_token_history"))
                    .containsExactly("refresh_token_history_id");

            assertThat(readIndexes(connection, "refresh_sessions"))
                    .contains(
                            new IndexDefinition(List.of("family_id"), true),
                            new IndexDefinition(List.of("user_id", "client_uuid", "active_slot"), true),
                            new IndexDefinition(List.of("user_id", "active_slot"), false),
                            new IndexDefinition(List.of("revoked_at"), false)
                    );
            assertThat(readIndexes(connection, "refresh_token_history"))
                    .contains(
                            new IndexDefinition(List.of("jti"), true),
                            new IndexDefinition(List.of("token_hash"), true),
                            new IndexDefinition(List.of("replaced_by_jti"), true),
                            new IndexDefinition(List.of("refresh_session_id", "current_slot"), true),
                            new IndexDefinition(List.of("refresh_session_id", "issued_at"), false),
                            new IndexDefinition(List.of("expires_at"), false),
                            new IndexDefinition(List.of("reuse_detected_at"), false)
                    );

            assertForeignKeyCascades(
                    connection, "refresh_sessions", "user_id", "users");
            assertForeignKeyCascades(
                    connection, "refresh_token_history", "refresh_session_id", "refresh_sessions");
        }

        assertThat(v2.migrate().migrationsExecuted).isZero();
    }

    @Test
    @DisplayName("Session 제약조건은 활성 중복과 잘못된 lifecycle을 거부한다")
    void enforceRefreshSessionAggregateConstraints() throws SQLException {
        String jdbcUrl = newIsolatedDatabaseUrl("flyway_refresh_session_constraints_");
        Flyway flyway = configureFlyway(jdbcUrl, "2");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            long userId = insertUser(connection, "session-owner@farmguardian.test");
            String clientUuid = "client-a";
            SessionRow active = activeSession(userId, clientUuid);
            long activeSessionId = insertSession(connection, active);
            insertHistory(connection, activeHistory(activeSessionId, 'a'));

            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, clientUuid)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, "client-b").withFamilyId(active.familyId())))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, "client-c").withoutActiveSlot()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, "client-d").withRevokedAt(EVENT_AT)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, "client-e").withReuseDetectedAt(EVENT_AT)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, "client-f").withVersion(-1L)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, "client-g").withFamilyId("short-family-id")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, " ")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, "client-h").withActiveSlot(0)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertSession(
                    connection, activeSession(userId, "client-i")
                            .asRevoked(LATER_EVENT_AT, EVENT_AT)))
                    .isInstanceOf(SQLException.class);

            revokeSession(connection, activeSessionId, false);
            long replacementSessionId = insertSession(
                    connection, activeSession(userId, clientUuid));
            assertThat(replacementSessionId).isPositive();
            revokeSession(connection, replacementSessionId, true);

            deleteUser(connection, userId);
            assertThat(countRows(connection, "refresh_sessions")).isZero();
            assertThat(countRows(connection, "refresh_token_history")).isZero();
        }
    }

    @Test
    @DisplayName("Token 이력 제약조건은 중복·잘못된 회전을 거부하고 cascade를 보장한다")
    void enforceRefreshTokenHistoryConstraintsAndCascade() throws SQLException {
        String jdbcUrl = newIsolatedDatabaseUrl("flyway_refresh_history_constraints_");
        Flyway flyway = configureFlyway(jdbcUrl, "2");
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);

        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "")) {
            long userId = insertUser(connection, "history-owner@farmguardian.test");
            long sessionA = insertSession(connection, activeSession(userId, "client-a"));
            long sessionB = insertSession(connection, activeSession(userId, "client-b"));
            long sessionC = insertSession(connection, activeSession(userId, "client-c"));
            long sessionD = insertSession(connection, activeSession(userId, "client-d"));
            long sessionE = insertSession(connection, activeSession(userId, "client-e"));
            long sessionF = insertSession(connection, activeSession(userId, "client-f"));

            HistoryRow tokenA = activeHistory(sessionA, 'a');
            HistoryRow tokenB = activeHistory(sessionB, 'b');
            insertHistory(connection, tokenA);
            insertHistory(connection, tokenB);

            assertThatThrownBy(() -> insertHistory(
                    connection, activeHistory(sessionA, 'c')))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertHistory(
                    connection, activeHistory(sessionC, 'd').withTokenHash(tokenA.tokenHash())))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertHistory(
                    connection, activeHistory(sessionD, 'e').withJti(tokenA.jti())))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertHistory(
                    connection, activeHistory(sessionE, 'f').withExpiresAt(CREATED_AT)))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertHistory(
                    connection, activeHistory(sessionE, '0').withoutCurrentSlot()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertHistory(
                    connection, activeHistory(sessionC, '4').withJti("short-jti")))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertHistory(
                    connection, activeHistory(sessionD, '5').withTokenHash("c".repeat(63))))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertHistory(
                    connection, activeHistory(sessionE, '6').withCurrentSlot(0)))
                    .isInstanceOf(SQLException.class);

            HistoryRow selfReplacement = activeHistory(sessionF, '1');
            insertHistory(connection, selfReplacement);
            assertThatThrownBy(() -> rotateHistory(
                    connection, selfReplacement.jti(), selfReplacement.jti()))
                    .isInstanceOf(SQLException.class);

            HistoryRow successor = activeHistory(sessionA, '2');
            rotateHistory(connection, tokenA.jti(), successor.jti());
            insertHistory(connection, successor);

            assertThat(readHistoryValue(connection, tokenA.jti(), "replaced_by_jti"))
                    .isEqualTo(successor.jti());
            assertThat(countHistoryRows(connection, sessionA)).isEqualTo(2);

            assertThatThrownBy(() -> markHistoryReused(connection, successor.jti()))
                    .isInstanceOf(SQLException.class);
            HistoryRow secondSuccessor = activeHistory(sessionA, '7');
            rotateHistory(connection, successor.jti(), secondSuccessor.jti());
            insertHistory(connection, secondSuccessor);
            assertThat(countHistoryRows(connection, sessionA)).isEqualTo(3);

            markHistoryReused(connection, tokenA.jti());
            revokeSession(connection, sessionA, true);
            String historyReuseDetectedAt = readHistoryValue(
                    connection, tokenA.jti(), "reuse_detected_at");
            String sessionReuseDetectedAt = readSessionValue(
                    connection, sessionA, "reuse_detected_at");
            assertThat(historyReuseDetectedAt)
                    .isNotNull()
                    .isEqualTo(sessionReuseDetectedAt);
            assertThat(readSessionValue(connection, sessionA, "active_slot")).isNull();
            assertThat(readSessionValue(connection, sessionA, "version")).isEqualTo("1");

            assertThatThrownBy(() -> rotateHistory(
                    connection, tokenB.jti(), "short-replacement"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> rotateHistory(
                    connection, tokenB.jti(), successor.jti()))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> insertHistory(
                    connection, activeHistory(Long.MAX_VALUE, '3')))
                    .isInstanceOf(SQLException.class);

            deleteSession(connection, sessionA);
            assertThat(countRows(connection, "users")).isEqualTo(1);
            assertThat(countHistoryRows(connection, sessionA)).isZero();
            assertThat(countRows(connection, "refresh_sessions")).isEqualTo(5);

            deleteUser(connection, userId);
            assertThat(countRows(connection, "refresh_sessions")).isZero();
            assertThat(countRows(connection, "refresh_token_history")).isZero();
        }
    }

    private void assertSessionColumns(Connection connection) throws SQLException {
        assertColumn(connection, "refresh_sessions", "refresh_session_id", false, Types.BIGINT, null);
        assertColumn(connection, "refresh_sessions", "user_id", false, Types.BIGINT, null);
        assertColumn(connection, "refresh_sessions", "client_uuid", false, Types.VARCHAR, 255);
        assertColumn(connection, "refresh_sessions", "family_id", false, Types.VARCHAR, 36);
        assertColumn(connection, "refresh_sessions", "created_at", false, Types.TIMESTAMP, null);
        assertColumn(connection, "refresh_sessions", "revoked_at", true, Types.TIMESTAMP, null);
        assertColumn(connection, "refresh_sessions", "reuse_detected_at", true, Types.TIMESTAMP, null);
        assertColumn(connection, "refresh_sessions", "active_slot", true, Types.TINYINT, null);
        assertColumn(connection, "refresh_sessions", "version", false, Types.BIGINT, null);
    }

    private void assertHistoryColumns(Connection connection) throws SQLException {
        assertColumn(
                connection, "refresh_token_history", "refresh_token_history_id", false, Types.BIGINT, null);
        assertColumn(connection, "refresh_token_history", "refresh_session_id", false, Types.BIGINT, null);
        assertColumn(connection, "refresh_token_history", "jti", false, Types.VARCHAR, 36);
        assertColumn(connection, "refresh_token_history", "token_hash", false, Types.VARCHAR, 64);
        assertColumn(connection, "refresh_token_history", "issued_at", false, Types.TIMESTAMP, null);
        assertColumn(connection, "refresh_token_history", "expires_at", false, Types.TIMESTAMP, null);
        assertColumn(connection, "refresh_token_history", "rotated_at", true, Types.TIMESTAMP, null);
        assertColumn(connection, "refresh_token_history", "replaced_by_jti", true, Types.VARCHAR, 36);
        assertColumn(connection, "refresh_token_history", "reuse_detected_at", true, Types.TIMESTAMP, null);
        assertColumn(connection, "refresh_token_history", "current_slot", true, Types.TINYINT, null);
    }

    private Flyway configureFlyway(String jdbcUrl, String targetVersion) {
        return Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .validateMigrationNaming(true)
                .target(targetVersion)
                .load();
    }

    private String newIsolatedDatabaseUrl(String prefix) {
        return "jdbc:h2:mem:" + prefix + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    private long insertUser(Connection connection, String email) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO users (email) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private void insertLegacyRefreshToken(Connection connection, long userId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO refresh_tokens (client_uuid, token, user_id) VALUES (?, ?, ?)")) {
            statement.setString(1, "legacy-client");
            statement.setString(2, "legacy-raw-refresh-token");
            statement.setLong(3, userId);
            statement.executeUpdate();
        }
    }

    private SessionRow activeSession(long userId, String clientUuid) {
        return new SessionRow(
                userId,
                clientUuid,
                UUID.randomUUID().toString(),
                CREATED_AT,
                null,
                null,
                1,
                0L
        );
    }

    private long insertSession(Connection connection, SessionRow row) throws SQLException {
        String sql = """
                INSERT INTO refresh_sessions (
                    user_id, client_uuid, family_id, created_at,
                    revoked_at, reuse_detected_at, active_slot, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, row.userId());
            statement.setString(2, row.clientUuid());
            statement.setString(3, row.familyId());
            statement.setTimestamp(4, row.createdAt());
            statement.setTimestamp(5, row.revokedAt());
            statement.setTimestamp(6, row.reuseDetectedAt());
            statement.setObject(7, row.activeSlot());
            statement.setLong(8, row.version());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                assertThat(keys.next()).isTrue();
                return keys.getLong(1);
            }
        }
    }

    private void revokeSession(Connection connection, long sessionId, boolean reuseDetected) throws SQLException {
        String sql = """
                UPDATE refresh_sessions
                SET active_slot = NULL,
                    revoked_at = ?,
                    reuse_detected_at = ?,
                    version = version + 1
                WHERE refresh_session_id = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            Timestamp revokedAt = reuseDetected ? LATER_EVENT_AT : EVENT_AT;
            statement.setTimestamp(1, revokedAt);
            statement.setTimestamp(2, reuseDetected ? revokedAt : null);
            statement.setLong(3, sessionId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private HistoryRow activeHistory(long sessionId, char hashCharacter) {
        return new HistoryRow(
                sessionId,
                UUID.randomUUID().toString(),
                String.valueOf(hashCharacter).repeat(64),
                CREATED_AT,
                EXPIRES_AT,
                null,
                null,
                null,
                1
        );
    }

    private void insertHistory(Connection connection, HistoryRow row) throws SQLException {
        String sql = """
                INSERT INTO refresh_token_history (
                    refresh_session_id, jti, token_hash, issued_at, expires_at,
                    rotated_at, replaced_by_jti, reuse_detected_at, current_slot
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, row.sessionId());
            statement.setString(2, row.jti());
            statement.setString(3, row.tokenHash());
            statement.setTimestamp(4, row.issuedAt());
            statement.setTimestamp(5, row.expiresAt());
            statement.setTimestamp(6, row.rotatedAt());
            statement.setString(7, row.replacedByJti());
            statement.setTimestamp(8, row.reuseDetectedAt());
            statement.setObject(9, row.currentSlot());
            statement.executeUpdate();
        }
    }

    private void rotateHistory(Connection connection, String jti, String replacementJti) throws SQLException {
        String sql = """
                UPDATE refresh_token_history
                SET current_slot = NULL,
                    rotated_at = ?,
                    replaced_by_jti = ?
                WHERE jti = ?
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, EVENT_AT);
            statement.setString(2, replacementJti);
            statement.setString(3, jti);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void markHistoryReused(Connection connection, String jti) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE refresh_token_history
                SET reuse_detected_at = ?
                WHERE jti = ?
                """)) {
            statement.setTimestamp(1, LATER_EVENT_AT);
            statement.setString(2, jti);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private String readHistoryValue(Connection connection, String jti, String column) throws SQLException {
        String sql = "SELECT " + column + " FROM refresh_token_history WHERE jti = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, jti);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private String readSessionValue(Connection connection, long sessionId, String column) throws SQLException {
        String sql = "SELECT " + column + " FROM refresh_sessions WHERE refresh_session_id = ?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, sessionId);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private void deleteSession(Connection connection, long sessionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "DELETE FROM refresh_sessions WHERE refresh_session_id = ?")) {
            statement.setLong(1, sessionId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void deleteUser(Connection connection, long userId) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM users WHERE user_id = ?")) {
            statement.setLong(1, userId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private long countHistoryRows(Connection connection, long sessionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM refresh_token_history WHERE refresh_session_id = ?")) {
            statement.setLong(1, sessionId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private List<String> readPublicTableNames(Connection connection) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(
                null, "public", "%", new String[]{"TABLE"})) {
            List<String> names = new ArrayList<>();
            while (tables.next()) {
                names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
            return names;
        }
    }

    private Map<String, ColumnDefinition> readColumns(Connection connection, String table) throws SQLException {
        Map<String, ColumnDefinition> columns = new LinkedHashMap<>();
        try (ResultSet result = connection.getMetaData().getColumns(null, "public", table, "%")) {
            while (result.next()) {
                String name = result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                columns.put(name, new ColumnDefinition(
                        result.getInt("NULLABLE") == DatabaseMetaData.columnNullable,
                        result.getInt("DATA_TYPE"),
                        result.getInt("COLUMN_SIZE")
                ));
            }
        }
        return columns;
    }

    private void assertColumn(
            Connection connection,
            String table,
            String column,
            boolean nullable,
            int expectedJdbcType,
            Integer expectedSize) throws SQLException {
        ColumnDefinition definition = readColumns(connection, table).get(column);
        assertThat(definition).as("%s.%s", table, column).isNotNull();
        assertThat(definition.nullable()).as("%s.%s nullable", table, column).isEqualTo(nullable);
        assertThat(definition.jdbcType()).as("%s.%s JDBC type", table, column).isEqualTo(expectedJdbcType);
        if (expectedSize != null) {
            assertThat(definition.size()).as("%s.%s size", table, column).isEqualTo(expectedSize);
        }
    }

    private List<String> readPrimaryKeyColumns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet keys = connection.getMetaData().getPrimaryKeys(null, "public", table)) {
            while (keys.next()) {
                columns.add(keys.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private List<IndexDefinition> readIndexes(Connection connection, String table) throws SQLException {
        Map<String, MutableIndex> indexes = new LinkedHashMap<>();
        try (ResultSet result = connection.getMetaData().getIndexInfo(null, "public", table, false, false)) {
            while (result.next()) {
                String indexName = result.getString("INDEX_NAME");
                String columnName = result.getString("COLUMN_NAME");
                if (indexName == null || columnName == null) {
                    continue;
                }
                indexes.computeIfAbsent(
                                indexName.toLowerCase(Locale.ROOT),
                                ignored -> new MutableIndex(!getBoolean(result, "NON_UNIQUE")))
                        .columns()
                        .add(columnName.toLowerCase(Locale.ROOT));
            }
        }
        return indexes.values().stream()
                .map(index -> new IndexDefinition(List.copyOf(index.columns()), index.unique()))
                .toList();
    }

    private boolean getBoolean(ResultSet result, String column) {
        try {
            return result.getBoolean(column);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void assertForeignKeyCascades(
            Connection connection, String table, String foreignKeyColumn, String parentTable) throws SQLException {
        List<ForeignKeyDefinition> foreignKeys = new ArrayList<>();
        try (ResultSet keys = connection.getMetaData().getImportedKeys(null, "public", table)) {
            while (keys.next()) {
                foreignKeys.add(new ForeignKeyDefinition(
                        keys.getString("FKCOLUMN_NAME").toLowerCase(Locale.ROOT),
                        keys.getString("PKTABLE_NAME").toLowerCase(Locale.ROOT),
                        keys.getShort("DELETE_RULE")
                ));
            }
        }
        assertThat(foreignKeys).contains(new ForeignKeyDefinition(
                foreignKeyColumn,
                parentTable,
                (short) DatabaseMetaData.importedKeyCascade
        ));
    }

    private long countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private record ColumnDefinition(boolean nullable, int jdbcType, int size) {
    }

    private record IndexDefinition(List<String> columns, boolean unique) {
    }

    private record MutableIndex(List<String> columns, boolean unique) {
        private MutableIndex(boolean unique) {
            this(new ArrayList<>(), unique);
        }
    }

    private record ForeignKeyDefinition(String column, String parentTable, short deleteRule) {
    }

    private record SessionRow(
            long userId,
            String clientUuid,
            String familyId,
            Timestamp createdAt,
            Timestamp revokedAt,
            Timestamp reuseDetectedAt,
            Integer activeSlot,
            long version) {

        private SessionRow withFamilyId(String newFamilyId) {
            return new SessionRow(
                    userId, clientUuid, newFamilyId, createdAt,
                    revokedAt, reuseDetectedAt, activeSlot, version);
        }

        private SessionRow withoutActiveSlot() {
            return new SessionRow(
                    userId, clientUuid, familyId, createdAt,
                    revokedAt, reuseDetectedAt, null, version);
        }

        private SessionRow withRevokedAt(Timestamp newRevokedAt) {
            return new SessionRow(
                    userId, clientUuid, familyId, createdAt,
                    newRevokedAt, reuseDetectedAt, activeSlot, version);
        }

        private SessionRow withReuseDetectedAt(Timestamp newReuseDetectedAt) {
            return new SessionRow(
                    userId, clientUuid, familyId, createdAt,
                    revokedAt, newReuseDetectedAt, activeSlot, version);
        }

        private SessionRow withVersion(long newVersion) {
            return new SessionRow(
                    userId, clientUuid, familyId, createdAt,
                    revokedAt, reuseDetectedAt, activeSlot, newVersion);
        }

        private SessionRow withActiveSlot(Integer newActiveSlot) {
            return new SessionRow(
                    userId, clientUuid, familyId, createdAt,
                    revokedAt, reuseDetectedAt, newActiveSlot, version);
        }

        private SessionRow asRevoked(Timestamp newRevokedAt, Timestamp newReuseDetectedAt) {
            return new SessionRow(
                    userId, clientUuid, familyId, createdAt,
                    newRevokedAt, newReuseDetectedAt, null, version);
        }
    }

    private record HistoryRow(
            long sessionId,
            String jti,
            String tokenHash,
            Timestamp issuedAt,
            Timestamp expiresAt,
            Timestamp rotatedAt,
            String replacedByJti,
            Timestamp reuseDetectedAt,
            Integer currentSlot) {

        private HistoryRow withJti(String newJti) {
            return new HistoryRow(
                    sessionId, newJti, tokenHash, issuedAt, expiresAt,
                    rotatedAt, replacedByJti, reuseDetectedAt, currentSlot);
        }

        private HistoryRow withTokenHash(String newTokenHash) {
            return new HistoryRow(
                    sessionId, jti, newTokenHash, issuedAt, expiresAt,
                    rotatedAt, replacedByJti, reuseDetectedAt, currentSlot);
        }

        private HistoryRow withExpiresAt(Timestamp newExpiresAt) {
            return new HistoryRow(
                    sessionId, jti, tokenHash, issuedAt, newExpiresAt,
                    rotatedAt, replacedByJti, reuseDetectedAt, currentSlot);
        }

        private HistoryRow withoutCurrentSlot() {
            return new HistoryRow(
                    sessionId, jti, tokenHash, issuedAt, expiresAt,
                    rotatedAt, replacedByJti, reuseDetectedAt, null);
        }

        private HistoryRow withCurrentSlot(Integer newCurrentSlot) {
            return new HistoryRow(
                    sessionId, jti, tokenHash, issuedAt, expiresAt,
                    rotatedAt, replacedByJti, reuseDetectedAt, newCurrentSlot);
        }
    }
}
