package com.br.honeycombframework.service;

import com.br.honeycombframework.exception.ResourceNotFoundException;
import com.br.honeycombframework.model.AgentExecutionLog;
import com.br.honeycombframework.model.AgentManifest;
import com.br.honeycombframework.model.AgentMetricsSummary;
import com.br.honeycombframework.repository.AgentExecutionLogQuery;
import com.br.honeycombframework.repository.AgentManifestRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentMetricsService {

    private static final int DEFAULT_WINDOW = 100;

    private final AgentManifestRepository manifestRepository;
    private final AgentExecutionLogQuery executionLogQuery;

    public AgentMetricsService(
            AgentManifestRepository manifestRepository,
            AgentExecutionLogQuery executionLogQuery) {
        this.manifestRepository = manifestRepository;
        this.executionLogQuery = executionLogQuery;
    }

    public AgentMetricsSummary summarizeAgent(String agentId, Integer window) {
        AgentManifest agent = manifestRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agente não encontrado."));

        int size = window != null && window > 0 ? window : DEFAULT_WINDOW;
        List<AgentExecutionLog> logs = executionLogQuery.findRecentByAgentId(agentId, size);

        if (logs.isEmpty()) {
            return AgentMetricsSummary.builder()
                    .agentId(agentId)
                    .agentName(agent.getName())
                    .build();
        }

        long success = logs.stream().filter(l -> Boolean.TRUE.equals(l.getAlaCompliant())).count();
        long errors = logs.size() - success;

        List<Long> latencies = logs.stream()
                .map(AgentExecutionLog::getLatencyMs)
                .filter(v -> v != null && v > 0)
                .sorted()
                .toList();

        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95 = latencies.isEmpty() ? 0 : latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);

        long tokensIn = logs.stream().mapToLong(l -> l.getTokensInput() != null ? l.getTokensInput() : 0).sum();
        long tokensOut = logs.stream().mapToLong(l -> l.getTokensOutput() != null ? l.getTokensOutput() : 0).sum();
        double cost = logs.stream().mapToDouble(l -> l.getEstimatedCost() != null ? l.getEstimatedCost() : 0).sum();

        double avgConfidence = logs.stream()
                .map(AgentExecutionLog::getConfidenceScore)
                .filter(v -> v != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(Double.NaN);

        return AgentMetricsSummary.builder()
                .agentId(agentId)
                .agentName(agent.getName())
                .totalInvocations(logs.size())
                .successCount(success)
                .errorCount(errors)
                .successRate(logs.isEmpty() ? 0 : (double) success / logs.size())
                .avgLatencyMs(avgLatency)
                .p95LatencyMs(p95)
                .totalTokensInput(tokensIn)
                .totalTokensOutput(tokensOut)
                .totalEstimatedCost(cost)
                .avgConfidenceScore(Double.isNaN(avgConfidence) ? null : avgConfidence)
                .build();
    }
}
