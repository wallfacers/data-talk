package com.datatalk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class JdbcConfig {

    @Primary
    @Bean
    public JdbcTemplate demoJdbcTemplate(DataSource demoDataSource) {
        return new JdbcTemplate(demoDataSource);
    }

    @Bean("sqliteJdbcTemplate")
    public JdbcTemplate sqliteJdbcTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("sqliteDataSource") DataSource sqliteDataSource) {
        return new JdbcTemplate(sqliteDataSource);
    }
}
