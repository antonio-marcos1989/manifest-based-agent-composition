package com.br.honeycombframework.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetricsSummary {

    private String agentId;
    private String agentName;
    private long totalInvocations;
    private long successCount;
    private long errorCount;
    private double successRate;
    private double avgLatencyMs;
    private long p95LatencyMs;
    private long totalTokensInput;
    private long totalTokensOutput;
    private double totalEstimatedCost;
    private Double avgConfidenceScore;
}
