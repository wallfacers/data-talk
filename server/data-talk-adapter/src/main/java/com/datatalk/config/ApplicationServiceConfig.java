package com.datatalk.config;

import com.datatalk.application.sql.SqlStatementGuard;
import com.datatalk.repository.DbConnectionRepository;
import com.datatalk.repository.SqlExecutionRepository;
import com.datatalk.service.QueryApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationServiceConfig {

    @Bean
    public QueryApplicationService queryApplicationService(
            DbConnectionRepository connectionRepository,
            SqlExecutionRepository sqlExecutionRepository,
            SqlStatementGuard statementGuard) {
        return new QueryApplicationService(connectionRepository, sqlExecutionRepository, statementGuard);
    }
}
