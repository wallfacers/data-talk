package com.datatalk.repository;

import com.datatalk.entity.Session;
import java.util.List;
import java.util.Optional;

/**
 * 会话仓储接口
 */
public interface SessionRepository {

    void save(Session session);

    Optional<Session> findById(String id);

    List<Session> findByProjectId(String projectId);

    void deleteById(String id);
}
