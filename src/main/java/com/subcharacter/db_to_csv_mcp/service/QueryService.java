package com.subcharacter.db_to_csv_mcp.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

@Service
public class QueryService {

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
        String normalized = sql == null ? "" : sql.trim().toLowerCase();
        if (!(normalized.startsWith("select")
                && !normalized.matches(".*\\b(insert|update|delete|merge|alter|drop|truncate)\\b.*"))) {
            throw new IllegalArgumentException("Only SELECT queries are allowed.");
        }
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Database username is required.");
        }

        JdbcTemplate jdbcTemplate = new JdbcTemplate(buildDataSource(username, password));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        if (rows.isEmpty()) return ""; // 빈 결과 → 빈 CSV

        // CSV 생성
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
                for (int i = 0; i < headers.length; i++) vals[i] = row.get(headers[i]);
                printer.printRecord(vals);
            }
        } catch (Exception e) {
            throw new RuntimeException("CSV 생성 실패: " + e.getMessage(), e);
        }
        return out.toString();
    }

    private DriverManagerDataSource buildDataSource(String username, String password) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        String driverClassName = dataSourceProperties.determineDriverClassName();
        if (StringUtils.hasText(driverClassName)) {
            dataSource.setDriverClassName(driverClassName);
        }
        String url = dataSourceProperties.determineUrl();
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("Database URL is not configured.");
        }
        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }
}
