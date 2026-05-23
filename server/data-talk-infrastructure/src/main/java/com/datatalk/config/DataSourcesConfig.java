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
import java.util.Properties;

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

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties demoDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Primary
    @Bean(name = "demoDataSource")
    public HikariDataSource demoDataSource(DataSourceProperties demoDataSourceProperties) {
        String url = demoDataSourceProperties.determineUrl();
        if (url != null && url.contains("placeholder")) {
            // Production default: use file-based H2 when URL is the placeholder
            Path dataDir = resolveDataDir();
            Path h2File = dataDir.resolve("datatalk-db");
            url = "jdbc:h2:file:" + h2File.toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";
        }
        // Use dataSourceClassName instead of jdbcUrl so HikariCP creates the
        // H2 JdbcDataSource directly, bypassing DriverManager.getConnection()
        // which would otherwise trigger SPI scan of all 30+ bundled JDBC
        // drivers (Trino/GaussDB/SQLServer/etc.) and cost ~10s at startup.
        HikariConfig config = new HikariConfig();
        config.setDataSourceClassName("org.h2.jdbcx.JdbcDataSource");
        Properties props = new Properties();
        props.setProperty("URL", url);
        config.setDataSourceProperties(props);
        config.setUsername(demoDataSourceProperties.determineUsername());
        config.setPassword(demoDataSourceProperties.determinePassword());
        return new HikariDataSource(config);
    }

    @Bean
    @ConfigurationProperties("spring.sqlite-datasource")
    public DataSourceProperties sqliteDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "sqliteDataSource")
    public HikariDataSource sqliteDataSource(DataSourceProperties sqliteDataSourceProperties) {
        // Stays on URL-based init — SQLiteDataSource doesn't auto-create the
        // parent directory, and the URL path is relative.  Trying to bypass
        // DriverManager here doesn't help anyway: SQLite's static initializer
        // is what triggers DriverManager init, regardless of how we open the
        // connection.  See openspec/changes/backend-startup-fast-path.
        return sqliteDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
