package com.br.honeycombframework.service;



import com.br.honeycombframework.governance.AgentCredentialSupport;
import com.br.honeycombframework.governance.RoleGovernancePolicy;
import com.br.honeycombframework.model.AgentManifest;
import com.br.honeycombframework.model.AlaSettings;
import com.br.honeycombframework.repository.AgentExecutionLogQuery;
import com.br.honeycombframework.repository.AgentManifestRepository;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;



@Service

public class AgentManifestService {



    private final AgentManifestRepository repository;

    private final AgentExecutionLogQuery executionLogQuery;



    public AgentManifestService(AgentManifestRepository repository, AgentExecutionLogQuery executionLogQuery) {

        this.repository = repository;
        this.executionLogQuery = executionLogQuery;

    }



    public Optional<AgentManifest> findById(String id) {

        return repository.findById(id)
                .map(this::normalizeManifest)
                .map(this::enrichLastExecutedAt);

    }



    public List<AgentManifest> findAll() {

        List<AgentManifest> agents = repository.findAll().stream().map(this::normalizeManifest).toList();
        enrichLastExecutedAt(agents);
        return agents;

    }



    public List<AgentManifest> findByCapability(String capability) {

        return repository.findByCapabilitiesContaining(capability).stream().map(this::normalizeManifest).toList();

    }



    public List<AgentManifest> findByActive(Boolean active) {

        return repository.findByActive(active).stream().map(this::normalizeManifest).toList();

    }



    public List<AgentManifest> findByRole(AgentManifest.Role role) {

        return repository.findByRole(role).stream().map(this::normalizeManifest).toList();

    }



    public AgentManifest register(AgentManifest manifest) {
        normalizeRole(manifest);
        RoleGovernancePolicy.normalizeGenerativeRoleTypes(manifest);
        RoleGovernancePolicy.normalizePayloadModeForType(manifest);
        manifest.setSystemPrompt(null);
        applyDefaultAlaSettings(manifest);
        AgentCredentialSupport.validateForRegister(manifest);
        return enrichLastExecutedAt(repository.save(manifest));
    }



    public AgentManifest toggleActive(String id) {

        AgentManifest agent = repository.findById(id)

                .orElseThrow(() -> new RuntimeException("Agente não encontrado"));



        agent.setActive(!agent.getActive());

        return enrichLastExecutedAt(normalizeManifest(repository.save(agent)));

    }



    public AgentManifest update(String id, AgentManifest updates) {
        AgentManifest agent = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Agente não encontrado"));

        if (updates.getName() != null) {
            agent.setName(updates.getName());
        }
        if (updates.getDescription() != null) {
            agent.setDescription(updates.getDescription());
        }
        if (updates.getRole() != null) {
            agent.setRole(updates.getRole().normalized());
        }
        if (updates.getEndpointUrl() != null) {
            agent.setEndpointUrl(updates.getEndpointUrl());
        }
        if (updates.getType() != null) {
            agent.setType(updates.getType());
        }
        if (updates.getActive() != null) {
            agent.setActive(updates.getActive());
        }
        if (updates.getCapabilities() != null) {
            agent.setCapabilities(updates.getCapabilities());
        }
        if (updates.getRequestPayloadMode() != null) {
            agent.setRequestPayloadMode(updates.getRequestPayloadMode());
        }
        if (updates.getDefaultModel() != null) {
            agent.setDefaultModel(updates.getDefaultModel());
        }
        if (updates.getForceJsonResponse() != null) {
            agent.setForceJsonResponse(updates.getForceJsonResponse());
        }
        if (updates.getAuthApiKey() != null && !updates.getAuthApiKey().isBlank()) {
            agent.setAuthApiKey(updates.getAuthApiKey());
        }
        if (updates.getHttpHeaders() != null) {
            agent.setHttpHeaders(updates.getHttpHeaders());
        }
        if (updates.getInputJsonSchema() != null) {
            agent.setInputJsonSchema(updates.getInputJsonSchema());
        }
        if (updates.getOutputJsonSchema() != null) {
            agent.setOutputJsonSchema(updates.getOutputJsonSchema());
        }
        if (updates.getAlaSettings() != null) {
            agent.setAlaSettings(updates.getAlaSettings());
        }

        applyDefaultAlaSettings(agent);
        AgentCredentialSupport.validateForRegister(agent);
        normalizeRole(agent);
        RoleGovernancePolicy.normalizeGenerativeRoleTypes(agent);
        RoleGovernancePolicy.normalizePayloadModeForType(agent);
        agent.setSystemPrompt(null);
        return enrichLastExecutedAt(repository.save(agent));
    }



    public void delete(String id) {

        repository.deleteById(id);

    }



    private AgentManifest normalizeManifest(AgentManifest manifest) {
        normalizeRole(manifest);
        RoleGovernancePolicy.normalizeGenerativeRoleTypes(manifest);
        RoleGovernancePolicy.normalizePayloadModeForType(manifest);
        return manifest;
    }

    private void normalizeRole(AgentManifest manifest) {
        if (manifest.getRole() != null) {
            manifest.setRole(manifest.getRole().normalized());
        }
    }

    private AgentManifest enrichLastExecutedAt(AgentManifest manifest) {
        if (manifest.getId() == null) {
            return manifest;
        }
        executionLogQuery.findLatestTimestampByAgentId(manifest.getId())
                .ifPresent(manifest::setLastExecutedAt);
        return manifest;
    }

    private void enrichLastExecutedAt(List<AgentManifest> agents) {
        List<String> ids = agents.stream()
                .map(AgentManifest::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<String, LocalDateTime> latestByAgent = executionLogQuery.findLatestTimestampByAgentIds(ids);
        for (AgentManifest agent : agents) {
            if (agent.getId() != null) {
                agent.setLastExecutedAt(latestByAgent.get(agent.getId()));
            }
        }
    }



    /**

     * Garante ALA mínimo por role — REFEREE exige maxErrorPercentage para dispatch.

     */

    void applyDefaultAlaSettings(AgentManifest manifest) {

        AlaSettings ala = manifest.getAlaSettings();

        if (ala == null) {

            ala = new AlaSettings();

            manifest.setAlaSettings(ala);

        }

        if (ala.getMaxLatencyMs() == null) {

            ala.setMaxLatencyMs(45_000);

        }

        if (ala.getMaxErrorPercentage() == null) {

            ala.setMaxErrorPercentage(defaultMaxErrorPercentage(manifest.getRole()));

        }

        if (ala.getEvaluationWindow() == null) {

            ala.setEvaluationWindow(10);

        }

        if (ala.getReliabilityThreshold() == null) {

            ala.setReliabilityThreshold(defaultReliabilityThreshold(manifest.getRole()));

        }

        if (ala.getStrictContract() == null) {

            ala.setStrictContract(false);

        }

    }



    private static double defaultMaxErrorPercentage(AgentManifest.Role role) {

        if (role == null) {

            return 30.0;

        }

        return switch (role) {

            case REFEREE -> 20.0;

            case OBSERVER -> 25.0;

            case HUNTER, COMPONENT -> 30.0;

        };

    }



    private static double defaultReliabilityThreshold(AgentManifest.Role role) {

        if (role == null) {

            return 0.7;

        }

        return switch (role) {

            case REFEREE -> 0.8;

            case OBSERVER -> 0.75;

            case HUNTER, COMPONENT -> 0.7;

        };

    }

}


