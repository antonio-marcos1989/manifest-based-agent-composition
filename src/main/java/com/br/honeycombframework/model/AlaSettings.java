package com.br.honeycombframework.model;

import lombok.Data;
import org.springframework.data.annotation.PersistenceCreator;

@Data
public class AlaSettings {

    @PersistenceCreator
    public AlaSettings() {
    }

    private Integer maxLatencyMs;
    private Double maxErrorPercentage;
    private Integer evaluationWindow; // Quantos últimos logs analisar (ex: os últimos 10)
    private Double reliabilityThreshold;
    private Boolean strictContract;
    /** Confiança mínima reportada pelo agente (0–1). */
    private Double minConfidenceScore;
    /** Custo estimado máximo por invocação. */
    private Double maxEstimatedCost;
    /** Total máximo de tokens (input + output) por invocação. */
    private Integer maxTokensPerInvocation;
}
