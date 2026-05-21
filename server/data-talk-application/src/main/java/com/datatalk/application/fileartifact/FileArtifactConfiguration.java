package com.datatalk.application.fileartifact;

import com.datatalk.application.stage.ActiveSessionDirProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Configuration
public class FileArtifactConfiguration {

    @Bean
    public SessionWorkdirRoot sessionWorkdirRoot(
            @Value("${datatalk.workdir.data-talk-root:#{systemProperties['user.home']}/.data-talk}") String dataTalkRoot
    ) {
        Path root = Paths.get(dataTalkRoot);
        return new SessionWorkdirRoot(root, root.resolve("opencode"));
    }

    @Bean
    @ConditionalOnMissingBean
    public ActiveSessionDirProvider defaultActiveSessionDirProvider() {
        return () -> Optional.empty();
    }
}
