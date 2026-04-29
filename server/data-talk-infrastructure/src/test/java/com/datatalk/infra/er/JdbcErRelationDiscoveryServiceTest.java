package com.datatalk.infra.er;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRecord;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.domain.er.ErGraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcErRelationDiscoveryServiceTest {

    private static final String DB =
        "mem:er-discover-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
    private static final String CONNECTION_ID = "conn-er";

    private JdbcErRelationDiscoveryService discovery;

    @BeforeAll
    static void seedDatabase() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:h2:" + DB, "sa", "");
             var st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS line_items");
            st.execute("DROP TABLE IF EXISTS orders");
            st.execute("DROP TABLE IF EXISTS users");
            st.execute("CREATE TABLE users (id BIGINT PRIMARY KEY, email VARCHAR(255))");
            st.execute("""
                CREATE TABLE orders (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT REFERENCES users(id),
                    amount DECIMAL(10,2)
                )
                """);
            st.execute("""
                CREATE TABLE line_items (
                    id BIGINT PRIMARY KEY,
                    order_id BIGINT REFERENCES orders(id),
                    product VARCHAR(120)
                )
                """);
        }
    }

    @BeforeEach
    void setUp() {
        var repo = mock(ConnectionRepository.class);
        var conn = mock(ConnectionService.class);
        when(repo.findById(CONNECTION_ID)).thenReturn(Optional.of(new ConnectionRecord(
            CONNECTION_ID, "ER discover test", "h2", "local", 0, DB, "sa",
            new byte[0], null, 0L, 3000, null, null
        )));
        when(conn.decryptPassword(CONNECTION_ID)).thenReturn("");
        discovery = new JdbcErRelationDiscoveryService(repo, conn);
    }

    @Test
    void discoverWithDepthZeroReturnsOnlySeedTables() {
        ErGraph g = discovery.discover(CONNECTION_ID, List.of("orders"), 0);

        assertThat(g.nodes()).extracting("name").containsExactly("orders");
        assertThat(g.edges()).isEmpty();
    }

    @Test
    void discoverWithDepthOneIncludesDirectNeighbors() {
        ErGraph g = discovery.discover(CONNECTION_ID, List.of("orders"), 1);

        assertThat(g.nodes()).extracting("name")
            .containsExactlyInAnyOrder("orders", "users", "line_items");
        assertThat(g.edges()).hasSize(2);
        assertThat(g.edges()).extracting("sourceTable", "sourceColumn", "targetTable", "targetColumn")
            .contains(
                tuple("orders", "user_id", "users", "id"),
                tuple("line_items", "order_id", "orders", "id")
            );
    }

    @Test
    void columnsCarryPrimaryKeyFlag() {
        ErGraph g = discovery.discover(CONNECTION_ID, List.of("users"), 0);

        var users = g.nodes().getFirst();
        assertThat(users.columns()).extracting("name", "isPrimaryKey")
            .contains(tuple("id", true), tuple("email", false));
    }

    @Test
    void summaryIsEnglishAndStructured() {
        ErGraph g = discovery.discover(CONNECTION_ID, List.of("orders"), 1);

        assertThat(g.summary()).matches(".*\\d+ tables? / \\d+ edges?.*");
        assertThat(g.summary()).doesNotContain("中文");
    }
}
