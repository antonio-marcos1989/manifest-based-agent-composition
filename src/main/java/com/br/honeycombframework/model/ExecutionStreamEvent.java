package com.br.honeycombframework.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Optional SSE notification payload for agent dispatch events.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionStreamEvent {

    private EventType type;
    private String goalId;
    private String runId;
    private ExecutionPhase phase;
    private String taskId;
    private String taskTitle;
    private String agentId;
    private String agentName;
    private String agentRole;
    private Long latencyMs;
    private String agentStatus;
    private String message;
    private LocalDateTime timestamp;

    public enum EventType {
        STREAM_CONNECTED,
        AGENT_STARTED,
        AGENT_COMPLETED,
        AGENT_FAILED
    }
}
