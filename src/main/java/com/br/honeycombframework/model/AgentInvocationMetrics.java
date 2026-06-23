package com.br.honeycombframework.model;

import lombok.Data;
import org.springframework.data.annotation.PersistenceCreator;

/**
 * Métricas de uma invocação HTTP a um agente (medidas pelo framework + reportadas pelo agente).
 */
@Data
public class AgentInvocationMetrics {

    @PersistenceCreator
    public AgentInvocationMetrics() {
    }

    private Long latencyMs;
    private Integer httpStatusCode;
    private Boolean alaCompliant;
    private String status;
    private ErrorType errorType;

    private Integer tokensInput;
    private Integer tokensOutput;
    private Integer tokensTotal;
    private Double estimatedCost;
    private String modelId;
    private String modelVersion;

    private Double confidenceScore;
    private Double candidateScore;
    private Long outputSizeBytes;
    private Integer retryCount;

    private Long contractCheckDurationMs;
    private Double intentAlignmentScore;

    public enum ErrorType {
        NONE,
        NETWORK,
        TIMEOUT,
        HTTP_ERROR,
        PARSE,
        ALA_LATENCY
    }
}
