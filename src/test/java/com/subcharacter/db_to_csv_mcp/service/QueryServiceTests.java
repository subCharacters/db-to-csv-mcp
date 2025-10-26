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
        String result = queryService.executeQuery("""
                WITH price_list AS (SELECT id, name FROM items)
                SELECT name FROM price_list WHERE id = 1
                """, "sa", "", false);

        assertThat(result).contains("NAME").contains("apple");
    }

    @Test
    void executeQueryAllowsSelectWithKeywordInsideLiteral() {
        String result = queryService.executeQuery("SELECT 'drop table orders' AS text", "sa", "", false);

        assertThat(result).contains("TEXT").contains("drop table orders");
    }

    @Test
    void executeQueryReturnsEmptyCsvWhenFilterRemovesRows() {
        String result = queryService.executeQuery(
                "SELECT status FROM orders WHERE status = 'UPDATE'", "sa", "", false);

        assertThat(result).isEmpty();
    }

    @Test
    void executeQueryRejectsMutatingStatements() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> queryService.executeQuery("UPDATE orders SET status = 'PAID'", "sa", "", false))
                .withMessageContaining("Only SELECT queries are allowed.");
    }

    @Test
    void executeQueryProvidesMeaningfulMessageOnDatabaseError() {
        assertThatThrownBy(() -> queryService.executeQuery("SELECT * FROM missing_table", "sa", "", false))
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
}
