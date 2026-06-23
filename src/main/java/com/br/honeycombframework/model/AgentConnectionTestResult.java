package com.br.honeycombframework.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentConnectionTestResult {
    private boolean success;
    private long latencyMs;
    private Integer httpStatusCode;
    private String message;
    private String responsePreview;
    private String modelUsed;
}
