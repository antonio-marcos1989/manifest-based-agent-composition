package com.br.honeycombframework.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "execution_logs")
public class AgentExecutionLog {

    @PersistenceCreator
    public AgentExecutionLog() {
    }

    @Id
    private String id;
    private String goalId;
    private String executionRunId;
    private String taskId;
    private String agentId;
    private String agentName;
    private String agentRole;

    private Long latencyMs;
    private Integer httpStatusCode;
    private Boolean alaCompliant;
    private String status;
    private AgentInvocationMetrics.ErrorType errorType;

    private Integer tokensInput;
    private Integer tokensOutput;
    private Integer tokensTotal;
    private Double estimatedCost;
    private String modelId;
    private Double confidenceScore;
    private Double candidateScore;
    private Long outputSizeBytes;

    private Long inputPayloadSizeBytes;
    private String agentExplanation;
    private LocalDateTime timestamp;
}
