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
}
