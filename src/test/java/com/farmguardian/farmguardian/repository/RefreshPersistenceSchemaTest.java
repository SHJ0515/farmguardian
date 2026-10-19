package com.farmguardian.farmguardian.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("Refresh 영속성 스키마 보안 계약 테스트")
class RefreshPersistenceSchemaTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Refresh 테이블과 해시 컬럼은 존재하며 Token 원문 컬럼은 포함하지 않는다")
    void refreshPersistenceSchemaContainsExpectedTablesAndNoRawTokenColumn() throws SQLException {
        Set<String> refreshTables = new HashSet<>();
        Set<String> historyColumns = new HashSet<>();
        List<String> rawTokenColumns = new ArrayList<>();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet columns = metadata.getColumns(
                    connection.getCatalog(), null, "refresh%", "%")) {
                while (columns.next()) {
                    String tableName = columns.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                    String columnName = columns.getString("COLUMN_NAME").toLowerCase(Locale.ROOT);
                    refreshTables.add(tableName);
                    if (tableName.equals("refresh_token_history")) {
                        historyColumns.add(columnName);
                    }
                    if (columnName.equals("token")) {
                        rawTokenColumns.add(tableName + "." + columnName);
                    }
                }
            }
        }

        assertThat(refreshTables)
                .as("검사 대상 Refresh 테이블이 실제로 존재해야 한다")
                .contains("refresh_sessions", "refresh_token_history");
        assertThat(historyColumns)
                .as("Refresh Token 이력에는 해시 저장 컬럼이 존재해야 한다")
                .contains("token_hash");
        assertThat(rawTokenColumns)
                .as("Refresh Token 원문 Credential은 DB schema에 저장하지 않아야 한다")
                .isEmpty();
    }
}
