package com.datatalk.application.connection;

import com.datatalk.application.persistence.SessionDataContextRepository;
import com.datatalk.application.session.SessionDataContextService;
import org.springframework.stereotype.Service;

@Service
public class ConnectionContextRefreshService {

    private final SessionDataContextRepository contexts;
    private final SessionDataContextService sessionContexts;

    public ConnectionContextRefreshService(
        SessionDataContextRepository contexts,
        SessionDataContextService sessionContexts
    ) {
        this.contexts = contexts;
        this.sessionContexts = sessionContexts;
    }

    public void refreshByConnectionId(String connectionId) {
        contexts.findByConnectionId(connectionId)
            .forEach(record -> sessionContexts.validate(record.sessionId()));
    }
}
