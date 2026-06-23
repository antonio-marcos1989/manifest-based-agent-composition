package com.br.honeycombframework.governance;

import com.br.honeycombframework.model.AgentInvocationMetrics;
import com.br.honeycombframework.model.AgentManifest;
import com.br.honeycombframework.model.AlaSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlaComplianceEvaluatorTest {

    @Test
    void flagsLatencyConfidenceCostAndTokens() {
        AgentManifest agent = new AgentManifest();
        AlaSettings ala = new AlaSettings();
        ala.setMaxLatencyMs(1000);
        ala.setMinConfidenceScore(0.9);
        ala.setMaxEstimatedCost(0.01);
        ala.setMaxTokensPerInvocation(50);
        agent.setAlaSettings(ala);
        agent.setRole(AgentManifest.Role.COMPONENT);

        AgentInvocationMetrics metrics = new AgentInvocationMetrics();
        metrics.setLatencyMs(1500L);
        metrics.setConfidenceScore(0.5);
        metrics.setEstimatedCost(0.05);
        metrics.setTokensTotal(120);

        AlaComplianceEvaluator.AlaComplianceResult result = AlaComplianceEvaluator.evaluate(agent, metrics);
        assertFalse(result.compliant());
        assertEquals(4, result.violations().size());
        assertTrue(result.violations().stream().anyMatch(v -> v.startsWith("LATENCY:")));
        assertTrue(result.violations().stream().anyMatch(v -> v.startsWith("CONFIDENCE:")));
        assertTrue(result.violations().stream().anyMatch(v -> v.startsWith("COST:")));
        assertTrue(result.violations().stream().anyMatch(v -> v.startsWith("TOKENS:")));
    }
}
