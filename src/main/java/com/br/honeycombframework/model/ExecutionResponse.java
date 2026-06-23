package com.br.honeycombframework.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecutionResponse {
    private String agentId;
    private Object data;
    private long latencyMs;
    private boolean alaCompliant;
    private String status;
    private Integer httpStatusCode;
    private AgentInvocationMetrics metrics;
    /** Explicação legível da resposta (do agente ou gerada pelo Observer). */
    private String agentExplanation;
    /** Dimensões ALA infringidas nesta invocação (vazio se compliant). */
    private java.util.List<String> alaViolations;
    /** Violações de contrato de saída (quando aplicável). */
    private java.util.List<String> contractViolations;
}
