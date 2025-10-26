package com.subcharacter.db_to_csv_mcp.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class QueryService {

    private static final Pattern MUTATING_KEYWORDS =
            Pattern.compile("\\b(insert|update|delete|merge|alter|drop|truncate|create|replace|call)\\b");
    private static final String SELECT_ONLY_MESSAGE = "Only SELECT queries are allowed.";

    private final DataSourceProperties dataSourceProperties;

    public QueryService(DataSourceProperties dataSourceProperties) {
        this.dataSourceProperties = dataSourceProperties;
    }

    @Tool(
            name = "executeQuery",
            description = """
            읽기 전용 SQL을 실행하고 결과를 CSV로 반환합니다.
            매개변수:
            - sql: SELECT 전용 쿼리
            - username: 데이터베이스 사용자명
            - password: 데이터베이스 비밀번호
            - quoteHeaders: 헤더를 큰따옴표로 감쌀지 여부
            보안: INSERT/UPDATE/DELETE/DDL은 차단됩니다.
            """
    )
    public String executeQuery(String sql, String username, String password, boolean quoteHeaders) {
        validateReadOnlySql(sql);
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Database username is required.");
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(buildConfiguredDataSource(username, password));
        return executeWithTemplate(sql, quoteHeaders, jdbcTemplate);
    }

    @Tool(
            name = "executeQueryWithConnection",
            description = """
            읽기 전용 SQL을 외부 데이터베이스 연결 정보와 함께 실행하고 결과를 CSV로 반환합니다.
            매개변수:
            - url: JDBC 연결 문자열
            - driverClassName: JDBC 드라이버 클래스 (선택)
            - sql: SELECT 전용 쿼리
            - username: 데이터베이스 사용자명
            - password: 데이터베이스 비밀번호
            - quoteHeaders: 헤더를 큰따옴표로 감쌀지 여부
            """
    )
    public String executeQueryWithConnection(ExternalQueryRequest request) {
        validateReadOnlySql(request.sql());
        validateExternalConnection(request);
        DriverManagerDataSource dataSource = createDataSource(
                request.url(),
                request.driverClassName(),
                request.username(),
                request.password()
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return executeWithTemplate(request.sql(), request.quoteHeaders(), jdbcTemplate);
    }

    private void validateReadOnlySql(String sql) {
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL must not be blank.");
        }
        String sanitized = stripCommentsAndLiterals(sql);
        String trimmed = sanitized.stripLeading();
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException("SQL must not be blank.");
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("select") || normalized.startsWith("with"))) {
            throw new IllegalArgumentException(SELECT_ONLY_MESSAGE);
        }
        if (MUTATING_KEYWORDS.matcher(normalized).find()) {
            throw new IllegalArgumentException(SELECT_ONLY_MESSAGE);
        }
        ensureSingleStatement(normalized);
    }

    private String executeWithTemplate(String sql, boolean quoteHeaders, JdbcTemplate jdbcTemplate) {
        List<Map<String, Object>> rows;
        try {
            rows = executeReadOnlyQuery(sql, jdbcTemplate);
        } catch (DataAccessException e) {
            throw new IllegalStateException(
                    "Database rejected the read-only query: " + e.getMostSpecificCause().getMessage(), e);
        }
        if (rows.isEmpty()) {
            return "";
        }

        StringWriter out = new StringWriter();
        var headers = rows.get(0).keySet().toArray(new String[0]);
        CSVFormat dataFormat = CSVFormat.DEFAULT;
        CSVFormat headerFormat = quoteHeaders
                ? dataFormat.builder().setQuoteMode(QuoteMode.ALL).build()
                : dataFormat;
        String recordSeparator = dataFormat.getRecordSeparator() != null
                ? dataFormat.getRecordSeparator()
                : System.lineSeparator();
        out.append(headerFormat.format((Object[]) headers)).append(recordSeparator);

        try (CSVPrinter printer = new CSVPrinter(out, dataFormat)) {
            for (var row : rows) {
                Object[] vals = new Object[headers.length];
                for (int i = 0; i < headers.length; i++) {
                    vals[i] = row.get(headers[i]);
                }
                printer.printRecord(vals);
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV 생성 실패: " + e.getMessage(), e);
        }
        return out.toString();
    }

    private List<Map<String, Object>> executeReadOnlyQuery(String sql, JdbcTemplate jdbcTemplate) {
        ColumnMapRowMapper rowMapper = new ColumnMapRowMapper();
        return jdbcTemplate.execute((ConnectionCallback<List<Map<String, Object>>>) connection -> {
            ReadOnlySettings readOnlySettings = ReadOnlySettings.notApplied();
            try {
                readOnlySettings = enableReadOnly(connection);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    boolean hasResultSet = statement.execute();
                    if (!hasResultSet) {
                        throw new IllegalArgumentException(SELECT_ONLY_MESSAGE);
                    }
                    try (ResultSet resultSet = statement.getResultSet()) {
                        List<Map<String, Object>> results = new ArrayList<>();
                        int rowNum = 0;
                        while (resultSet.next()) {
                            results.add(rowMapper.mapRow(resultSet, rowNum++));
                        }
                        return results;
                    }
                }
            } catch (SQLException ex) {
                throw jdbcTemplate.getExceptionTranslator().translate("executeQuery", sql, ex);
            } finally {
                restoreReadOnly(connection, readOnlySettings);
            }
        });
    }

    private String stripCommentsAndLiterals(String sql) {
        StringBuilder builder = new StringBuilder(sql.length());
        int length = sql.length();
        for (int i = 0; i < length; i++) {
            char current = sql.charAt(i);
            if (current == '\'') {
                char quote = current;
                builder.append(' ');
                i++;
                while (i < length) {
                    char next = sql.charAt(i);
                    if (next == quote) {
                        if (i + 1 < length && sql.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (current == '"') {
                char quote = current;
                builder.append(' ');
                i++;
                while (i < length) {
                    char next = sql.charAt(i);
                    if (next == quote) {
                        if (i + 1 < length && sql.charAt(i + 1) == quote) {
                            i += 2;
                            continue;
                        }
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (current == '`') {
                builder.append(' ');
                i++;
                while (i < length) {
                    char next = sql.charAt(i);
                    if (next == '`') {
                        if (i + 1 < length && sql.charAt(i + 1) == '`') {
                            i += 2;
                            continue;
                        }
                        break;
                    }
                    i++;
                }
                continue;
            }
            if (current == '-' && i + 1 < length && sql.charAt(i + 1) == '-') {
                i += 2;
                while (i < length && sql.charAt(i) != '\n') {
                    i++;
                }
                builder.append('\n');
                continue;
            }
            if (current == '/' && i + 1 < length && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < length && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i++;
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private void ensureSingleStatement(String normalized) {
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex < 0) {
            return;
        }
        for (int i = semicolonIndex + 1; i < normalized.length(); i++) {
            if (!Character.isWhitespace(normalized.charAt(i))) {
                throw new IllegalArgumentException("Multiple SQL statements are not allowed.");
            }
        }
    }

    private void validateExternalConnection(ExternalQueryRequest request) {
        if (!StringUtils.hasText(request.url())) {
            throw new IllegalArgumentException("Database URL is required.");
        }
        if (!StringUtils.hasText(request.username())) {
            throw new IllegalArgumentException("Database username is required.");
        }
    }

    private ReadOnlySettings enableReadOnly(Connection connection) throws SQLException {
        boolean previousReadOnly = false;
        boolean previousKnown = false;
        try {
            previousReadOnly = connection.isReadOnly();
            previousKnown = true;
        } catch (SQLFeatureNotSupportedException ignored) {
            // 일부 드라이버는 현재 상태 조회를 지원하지 않는다.
        }
        try {
            connection.setReadOnly(true);
            if (previousKnown) {
                return new ReadOnlySettings(true, true, previousReadOnly);
            }
            return new ReadOnlySettings(true, false, false);
        } catch (SQLFeatureNotSupportedException ignored) {
            return ReadOnlySettings.notApplied();
        }
    }

    private void restoreReadOnly(Connection connection, ReadOnlySettings settings) {
        if (!settings.applied()) {
            return;
        }
        boolean target = settings.restorable() ? settings.previousReadOnly() : false;
        try {
            connection.setReadOnly(target);
        } catch (SQLException ignored) {
            // 복원 실패는 다음 사용 시 커넥션 풀에서 재설정된다.
        }
    }

    private DriverManagerDataSource buildConfiguredDataSource(String username, String password) {
        String url = dataSourceProperties.determineUrl();
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("Database URL is not configured.");
        }
        String driverClassName = dataSourceProperties.determineDriverClassName();
        return createDataSourceInternal(url, driverClassName, username, password);
    }

    private DriverManagerDataSource createDataSource(String url, String driverClassName, String username, String password) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("Database URL is required.");
        }
        return createDataSourceInternal(url, driverClassName, username, password);
    }

    private DriverManagerDataSource createDataSourceInternal(String url, String driverClassName, String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        if (StringUtils.hasText(driverClassName)) {
            dataSource.setDriverClassName(driverClassName);
        }
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    public record ExternalQueryRequest(
            String url,
            String driverClassName,
            String sql,
            String username,
            String password,
            boolean quoteHeaders
    ) {
    }

    private record ReadOnlySettings(boolean applied, boolean restorable, boolean previousReadOnly) {
        private static ReadOnlySettings notApplied() {
            return new ReadOnlySettings(false, false, false);
        }
    }
}
