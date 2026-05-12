package com.datatalk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.datatalk.application.diagnostics.DiagnosticsThresholdProperties;
import com.datatalk.application.ingestion.IngestionConfig;

@SpringBootApplication(scanBasePackages = "com.datatalk")
@EnableConfigurationProperties({DiagnosticsThresholdProperties.class, IngestionConfig.class})
public class DataTalkApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataTalkApplication.class, args);
    }
}
