package com.br.honeycombframework.governance;

import com.br.honeycombframework.model.AgentInvocationMetrics;
import com.br.honeycombframework.model.AgentManifest;
import com.br.honeycombframework.model.AlaSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Avalia conformidade ALA por invocação em múltiplas dimensões declaradas no manifesto.
 */
public final class AlaComplianceEvaluator {

    private AlaComplianceEvaluator() {
    }

    public static AlaComplianceResult evaluate(AgentManifest agent, AgentInvocationMetrics metrics) {
        List<String> violations = new ArrayList<>();
        AgentInvocationMetrics.ErrorType primaryError = AgentInvocationMetrics.ErrorType.NONE;

        Integer maxLatencyMs = RoleGovernancePolicy.effectiveMaxLatencyMs(agent);
        if (maxLatencyMs != null && metrics.getLatencyMs() != null && metrics.getLatencyMs() > maxLatencyMs) {
            violations.add("LATENCY: observed " + metrics.getLatencyMs() + "ms > limit " + maxLatencyMs + "ms");
            primaryError = pickPrimary(primaryError, AgentInvocationMetrics.ErrorType.ALA_LATENCY);
        }

        AlaSettings ala = agent.getAlaSettings();
        if (ala != null) {
            Double minConfidence = RoleGovernancePolicy.effectiveMinConfidenceScore(agent);
            if (minConfidence != null && metrics.getConfidenceScore() != null
                    && metrics.getConfidenceScore() < minConfidence) {
                violations.add("CONFIDENCE: observed " + metrics.getConfidenceScore()
                        + " < min " + minConfidence);
                primaryError = pickPrimary(primaryError, AgentInvocationMetrics.ErrorType.ALA_CONFIDENCE);
            }

            Double maxCost = RoleGovernancePolicy.effectiveMaxEstimatedCost(agent);
            if (maxCost != null && metrics.getEstimatedCost() != null
                    && metrics.getEstimatedCost() > maxCost) {
                violations.add("COST: observed " + metrics.getEstimatedCost()
                        + " > max " + maxCost);
                primaryError = pickPrimary(primaryError, AgentInvocationMetrics.ErrorType.ALA_COST);
            }

            Integer maxTokens = RoleGovernancePolicy.effectiveMaxTokensPerInvocation(agent);
            if (maxTokens != null && metrics.getTokensTotal() != null
                    && metrics.getTokensTotal() > maxTokens) {
                violations.add("TOKENS: observed " + metrics.getTokensTotal()
                        + " > max " + maxTokens);
                primaryError = pickPrimary(primaryError, AgentInvocationMetrics.ErrorType.ALA_TOKENS);
            }
        }

        boolean compliant = violations.isEmpty();
        String status = compliant ? "SUCCESS" : "ALA_VIOLATION";
        return new AlaComplianceResult(compliant, status, primaryError, violations);
    }

    private static AgentInvocationMetrics.ErrorType pickPrimary(
            AgentInvocationMetrics.ErrorType current,
            AgentInvocationMetrics.ErrorType candidate) {
        return current == AgentInvocationMetrics.ErrorType.NONE ? candidate : current;
    }

    public record AlaComplianceResult(
            boolean compliant,
            String status,
            AgentInvocationMetrics.ErrorType primaryError,
            List<String> violations) {
    }
}
