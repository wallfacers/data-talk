package com.datatalk.chatdb.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DataSourcesConfig {

    /**
     * 根据操作系统解析本地数据目录路径
     * Windows: %APPDATA%\com.data-talk.app\db
     * macOS: ~/Library/Application Support/com.data-talk.app/db
     * Linux: ~/.local/share/com.data-talk.app/db
     */
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

    /**
     * H2 演示数据库数据源（主数据源，用于 Demo 查询）
     * 使用文件模式持久化数据，重启后数据保留。
     */
    @Primary
    @Bean
    public DataSource h2DataSource() {
        Path dataDir = resolveDataDir();
        Path h2File = dataDir.resolve("datatalk-db");
        String jdbcUrl = "jdbc:h2:file:" + h2File.toAbsolutePath() + ";DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL";

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    @Primary
    @Bean
    public JdbcTemplate jdbcTemplate(@Qualifier("h2DataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * SQLite 元数据库数据源（用于存储连接配置、会话等）
     */
    @Bean
    @ConfigurationProperties("spring.sqlite-datasource")
    public DataSourceProperties sqliteDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource sqliteDataSource(
            @Qualifier("sqliteDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    public JdbcTemplate sqliteJdbcTemplate(
            @Qualifier("sqliteDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
