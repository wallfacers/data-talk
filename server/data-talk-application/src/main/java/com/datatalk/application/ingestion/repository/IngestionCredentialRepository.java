package com.datatalk.application.ingestion.repository;

import com.datatalk.domain.ingestion.IngestionCredential;

import java.util.List;
import java.util.Optional;

public interface IngestionCredentialRepository {
    void save(IngestionCredential credential);
    Optional<IngestionCredential> findById(String id);
    Optional<IngestionCredential> findByName(String name);
    List<IngestionCredential> findAll();
    void deleteById(String id);
    int countReferencingJobs(String credentialId);
    void nullifyCredentialOnJobs(String credentialId);
}
