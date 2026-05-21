package com.datatalk.infra.fileartifact;

import com.datatalk.application.fileartifact.ArtifactWatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WatcherConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public ArtifactWatcher artifactWatcher() {
        return new MethvinArtifactWatcher();
    }
}
