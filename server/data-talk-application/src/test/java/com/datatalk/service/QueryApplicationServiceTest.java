package com.datatalk.service;

import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.command.ExecuteSqlCommand;
import com.datatalk.entity.DbConnection;
import com.datatalk.entity.DbType;
import com.datatalk.domain.error.DataTalkException;
import com.datatalk.repository.DbConnectionRepository;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.valueobject.QueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QueryApplicationServiceTest {

    private DbConnectionRepository connectionRepository;
    private SqlExecutionRepository sqlExecutionRepository;
    private SqlStatementGuard statementGuard;
    private QueryApplicationService service;

    @BeforeEach
    void setUp() {
        connectionRepository = mock(DbConnectionRepository.class);
        sqlExecutionRepository = mock(SqlExecutionRepository.class);
        statementGuard = spy(new SqlStatementGuard());
        service = new QueryApplicationService(connectionRepository, sqlExecutionRepository, statementGuard);
    }

    @Test
    void rejectsNonSelectStatementsBeforeLookup() {
        var command = new ExecuteSqlCommand("conn-1", "UPDATE users SET name = 'x'");

        assertThatThrownBy(() -> service.executeQuery(command))
                .isInstanceOf(DataTalkException.class)
                .hasMessageContaining("SELECT / WITH");

        verifyNoInteractions(connectionRepository, sqlExecutionRepository);
    }

    @Test
    void validatesSelectBeforeQueryExecution() {
        var connection = new DbConnection(
                "conn-1",
                "Primary",
                DbType.H2,
                "localhost",
                3306,
                "demo",
                "user",
                Instant.parse("2026-04-20T00:00:00Z")
        );
        when(connectionRepository.findById("conn-1")).thenReturn(Optional.of(connection));
        when(sqlExecutionRepository.execute(connection, "SELECT 1"))
                .thenReturn(new QueryResult(List.of("c"), List.of(Map.of("c", 1)), 5L));

        var response = service.executeQuery(new ExecuteSqlCommand("conn-1", "SELECT 1"));

        assertThat(response.durationMs()).isEqualTo(5L);
        var order = inOrder(statementGuard, connectionRepository, sqlExecutionRepository);
        order.verify(statementGuard).assertSelectOnly("SELECT 1");
        order.verify(connectionRepository).findById("conn-1");
        order.verify(sqlExecutionRepository).execute(connection, "SELECT 1");
    }
}
