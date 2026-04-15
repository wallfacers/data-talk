package com.datatalk.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DataSourcesConfig {

    private static Path resolveDataDir() {
        String os = System.getProperty("os.name").toLowerCase();
        Path dir;
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            dir = Path.of(appData != null ? appData : System.getProperty("user.home"))
                    .resolve("com.data-talk.app")
                    .resolve("db");
        } else if (os.contains("mac") || os.contains("os x")) {
            dir = Path.of(System.getProperty("user.home"))
                    .resolve("Library")
                    .resolve("Application Support")
                    .resolve("com.data-talk.app")
                    .resolve("db");
        } else {
            String xdg = System.getenv("XDG_DATA_HOME");
            if (xdg != null && !xdg.isBlank()) {
                dir = Path.of(xdg).resolve("com.data-talk.app").resolve("db");
            } else {
                dir = Path.of(System.getProperty("user.home"))
                        .resolve(".local")
                        .resolve("share")
                        .resolve("com.data-talk.app")
                        .resolve("db");
            }
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create data directory: " + dir, e);
        }
        return dir;
    }

    @Primary
    @Bean(name = "demoDataSource")
    public HikariDataSource demoDataSource() {
        Path dataDir = resolveDataDir();
        Path h2File = dataDir.resolve("datatalk-db");
        String jdbcUrl = "jdbc:h2:file:" + h2File.toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.h2.Driver");
        config.setUsername("sa");
        config.setPassword("");
        return new HikariDataSource(config);
    }

    @Bean
    @ConfigurationProperties("spring.sqlite-datasource")
    public DataSourceProperties sqliteDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "sqliteDataSource")
    public HikariDataSource sqliteDataSource(DataSourceProperties sqliteDataSourceProperties) {
        return sqliteDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
