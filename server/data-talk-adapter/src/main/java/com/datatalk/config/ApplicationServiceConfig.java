package com.datatalk.config;

import com.datatalk.application.connection.ConnectionService;
import com.datatalk.application.persistence.ConnectionRepository;
import com.datatalk.application.session.SessionDataContextService;
import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.application.sql.TableContextAutoResolver;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.service.QueryApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationServiceConfig {

    @Bean
    public QueryApplicationService queryApplicationService(
            ConnectionRepository connectionRepository,
            ConnectionService connectionService,
            SessionDataContextService sessionDataContextService,
            SqlExecutionRepository sqlExecutionRepository,
            SqlStatementGuard statementGuard,
            TableContextAutoResolver tableContextAutoResolver) {
        return new QueryApplicationService(
            connectionRepository,
            connectionService,
            sessionDataContextService,
            sqlExecutionRepository,
            statementGuard,
            tableContextAutoResolver
        );
    }
}
