package com.br.honeycombframework.controller;

import com.br.honeycombframework.exception.ValidationException;
import com.br.honeycombframework.model.AgentConnectionTestRequest;
import com.br.honeycombframework.model.AgentConnectionTestResult;
import com.br.honeycombframework.model.AgentManifest;
import com.br.honeycombframework.model.AgentMetricsSummary;
import com.br.honeycombframework.model.AgentExecutionLog;
import com.br.honeycombframework.service.AgentManifestService;
import com.br.honeycombframework.service.AgentMetricsService;
import com.br.honeycombframework.service.AgentDispatcherService;
import com.br.honeycombframework.repository.AgentExecutionLogQuery;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/v1/manifests")
public class AgentManifestController {

    private final AgentManifestService service;
    private final AgentMetricsService agentMetricsService;
    private final AgentExecutionLogQuery executionLogQuery;
    private final AgentDispatcherService dispatcherService;

    public AgentManifestController(
            AgentManifestService service,
            AgentMetricsService agentMetricsService,
            AgentExecutionLogQuery executionLogQuery,
            AgentDispatcherService dispatcherService) {
        this.service = service;
        this.agentMetricsService = agentMetricsService;
        this.executionLogQuery = executionLogQuery;
        this.dispatcherService = dispatcherService;
    }

    @PostMapping("/test-connection")
    public AgentConnectionTestResult testConnection(@RequestBody AgentConnectionTestRequest request) {
        AgentManifest manifest = request.getManifest();
        if (manifest == null) {
            throw new ValidationException("manifest é obrigatório.");
        }
        return dispatcherService.testConnection(manifest, request.getMergeCredentialsFromId());
    }

    @PostMapping
    public ResponseEntity<AgentManifest> register(@Valid @RequestBody AgentManifest manifest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.register(manifest));
    }

    @GetMapping
    public List<AgentManifest> getAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentManifest> getById(@PathVariable String id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/search-capability")
    public List<AgentManifest> getByCapability(@RequestParam String capability) {
        return service.findByCapability(capability);
    }

    @GetMapping("/search-active")
    public List<AgentManifest> getByActive(@RequestParam Boolean active) {
        return service.findByActive(active);
    }

    @GetMapping("/search-role")
    public List<AgentManifest> getByRole(@RequestParam AgentManifest.Role role) {
        return service.findByRole(role);
    }

    @GetMapping("/{id}/metrics")
    public AgentMetricsSummary getAgentMetrics(
            @PathVariable String id,
            @RequestParam(required = false) Integer window) {
        return agentMetricsService.summarizeAgent(id, window);
    }

    @GetMapping("/{id}/invocations")
    public List<AgentExecutionLog> getRecentInvocations(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit) {
        return executionLogQuery.findRecentByAgentId(id, Math.min(limit, 200));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgentManifest> update(@PathVariable String id, @Valid @RequestBody AgentManifest manifest) {
        try {
            return ResponseEntity.ok(service.update(id, manifest));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<AgentManifest> toggleActive(@PathVariable String id) {
        try {
            return ResponseEntity.ok(service.toggleActive(id));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (service.findById(id).isPresent()) {
            service.delete(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}