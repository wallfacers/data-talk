package com.datatalk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.datatalk.application.diagnostics.DiagnosticsThresholdProperties;

@SpringBootApplication(scanBasePackages = "com.datatalk")
@EnableConfigurationProperties({DiagnosticsThresholdProperties.class})
public class DataTalkApplication {

    public static void main(String[] args) {
        // Parallel-warm 30+ bundled JDBC drivers BEFORE Spring boot starts.
        // Each driver's <clinit> (registers itself via DriverManager,
        // sometimes loads native libs / resource bundles) is otherwise run
        // sequentially when the first HikariPool calls DriverManager — costing
        // ~9s on the main thread.  Loading them via ServiceLoader in parallel
        // overlaps the clinit work across cores; the subsequent serial
        // DriverManager init becomes a fast no-op.
        // See openspec/changes/backend-startup-fast-path/design.md.
        Thread warmup = new Thread(() -> {
            try {
                java.util.ServiceLoader.load(java.sql.Driver.class)
                    .stream()
                    .parallel()
                    .forEach(p -> {
                        try { p.get(); } catch (Throwable ignored) { /* skip bad driver */ }
                    });
            } catch (Throwable ignored) {
                // best-effort: a failure here merely loses the warmup, the
                // pool will retry through the normal slow path.
            }
        }, "jdbc-driver-warmup");
        warmup.setDaemon(true);
        warmup.start();

        SpringApplication.run(DataTalkApplication.class, args);
    }
}
