package com.br.honeycombframework.repository;

import com.br.honeycombframework.model.AgentManifest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentManifestRepository extends MongoRepository<AgentManifest, String> {

    @Query("{ 'capabilities': ?0 }")
    List<AgentManifest> findByCapabilitiesContaining(String capability);

    @Query("{ 'active': ?0 }")
    List<AgentManifest> findByActive(Boolean active);

    @Query("{ 'role': ?0 }")
    List<AgentManifest> findByRole(AgentManifest.Role role);
}
