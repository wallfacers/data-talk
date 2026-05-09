package com.datatalk.application.dashboard;

import com.datatalk.application.fileartifact.SessionWorkdirRoot;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
public class DashboardConfiguration {

    @Bean
    public Path dashboardsBaseDir(SessionWorkdirRoot root) throws IOException {
        Path dir = root.dashboardsRoot();
        Files.createDirectories(dir);
        return dir;
    }
}
