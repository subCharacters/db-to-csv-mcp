package com.subcharacter.db_to_csv_mcp.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class QueryServiceTests {

    @Autowired
    private QueryService queryService;

    @Test
    void executeQueryAllowsWithClauseSelect() {
        QueryService.ConfiguredQueryRequest request = configuredRequest("""
                WITH price_list AS (SELECT id, name FROM items)
                SELECT name FROM price_list WHERE id = 1
                """);
        String result = queryService.executeQuery(request);

        assertThat(result).contains("NAME").contains("apple");
    }

    @Test
    void executeQueryAllowsSelectWithKeywordInsideLiteral() {
        QueryService.ConfiguredQueryRequest request =
                configuredRequest("SELECT 'drop table orders' AS text");
        String result = queryService.executeQuery(request);

        assertThat(result).contains("TEXT").contains("drop table orders");
    }

    @Test
    void executeQueryReturnsEmptyCsvWhenFilterRemovesRows() {
        QueryService.ConfiguredQueryRequest request =
                configuredRequest("SELECT status FROM orders WHERE status = 'UPDATE'");
        String result = queryService.executeQuery(request);

        assertThat(result).isEmpty();
    }

    @Test
    void executeQueryRejectsMutatingStatements() {
        QueryService.ConfiguredQueryRequest request =
                configuredRequest("UPDATE orders SET status = 'PAID'");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> queryService.executeQuery(request))
                .withMessageContaining("Only SELECT queries are allowed.");
    }

    @Test
    void executeQueryProvidesMeaningfulMessageOnDatabaseError() {
        QueryService.ConfiguredQueryRequest request =
                configuredRequest("SELECT * FROM missing_table");

        assertThatThrownBy(() -> queryService.executeQuery(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Database rejected the read-only query")
                .hasMessageContaining("missing_table");
    }

    @Test
    void validateReadOnlySqlAllowsMySqlQuotedIdentifiers() {
        assertThatNoException()
                .isThrownBy(() -> ReflectionTestUtils.invokeMethod(
                        queryService, "validateReadOnlySql", "SELECT `drop` FROM items"));
    }

    @Test
    void validateReadOnlySqlAllowsOracleQuotedLiteral() {
        assertThatNoException()
                .isThrownBy(() -> ReflectionTestUtils.invokeMethod(
                        queryService, "validateReadOnlySql", "SELECT q'[drop table orders]' AS txt FROM dual"));
    }

    @Test
    void executeQueryWithConnectionUsesProvidedCredentials() {
        QueryService.ExternalQueryRequest request = new QueryService.ExternalQueryRequest(
                "jdbc:h2:mem:demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
                "SELECT name FROM customers ORDER BY id",
                "sa",
                "",
                false,
                1
        );

        String result = queryService.executeQueryWithConnection(request);

        assertThat(result).contains("NAME").contains("Alice Kim");
    }

    @Test
    void executeQueryWithConnectionValidatesRequiredUrl() {
        QueryService.ExternalQueryRequest request = new QueryService.ExternalQueryRequest(
                " ",
                null,
                "SELECT 1",
                "sa",
                "",
                false,
                1
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> queryService.executeQueryWithConnection(request))
                .withMessageContaining("Database URL is required.");
    }

    @Test
    void executeQueryAppliesStringsOnlyQuoteMode() {
        QueryService.ConfiguredQueryRequest request = new QueryService.ConfiguredQueryRequest(
                "SELECT name, price FROM items WHERE id = 1",
                "sa",
                "",
                false,
                2
        );

        String result = queryService.executeQuery(request);

        assertThat(result).contains("\"apple\",100");
    }

    @Test
    void executeQueryAppliesAllValueQuoteMode() {
        QueryService.ConfiguredQueryRequest request = new QueryService.ConfiguredQueryRequest(
                "SELECT name, price FROM items WHERE id = 1",
                "sa",
                "",
                false,
                3
        );

        String result = queryService.executeQuery(request);

        assertThat(result).contains("\"apple\",\"100\"");
    }

    private QueryService.ConfiguredQueryRequest configuredRequest(String sql) {
        return new QueryService.ConfiguredQueryRequest(sql, "sa", "", false, 1);
    }
}
