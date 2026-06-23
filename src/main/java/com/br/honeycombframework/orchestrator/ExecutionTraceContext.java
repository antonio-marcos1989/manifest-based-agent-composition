package com.br.honeycombframework.orchestrator;

import com.br.honeycombframework.model.ExecutionPhase;

import java.util.Map;

/**
 * Contexto da execução corrente (run) propagado por thread para métricas, logs e SSE.
 */
public final class ExecutionTraceContext {

    private static final ThreadLocal<String> RUN_ID = new ThreadLocal<>();
    private static final ThreadLocal<ExecutionPhase> PHASE = new ThreadLocal<>();
    private static final ThreadLocal<String> TASK_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TASK_TITLE = new ThreadLocal<>();
    private static final ThreadLocal<Map<String, Object>> EXECUTION_INPUT = new ThreadLocal<>();

    private ExecutionTraceContext() {
    }

    public static void setRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            RUN_ID.remove();
        } else {
            RUN_ID.set(runId);
        }
    }

    public static String getRunId() {
        return RUN_ID.get();
    }

    public static void setPhaseContext(ExecutionPhase phase, String taskId, String taskTitle) {
        if (phase == null) {
            PHASE.remove();
        } else {
            PHASE.set(phase);
        }
        if (taskId == null || taskId.isBlank()) {
            TASK_ID.remove();
        } else {
            TASK_ID.set(taskId);
        }
        if (taskTitle == null || taskTitle.isBlank()) {
            TASK_TITLE.remove();
        } else {
            TASK_TITLE.set(taskTitle);
        }
    }

    public static ExecutionPhase getPhase() {
        return PHASE.get();
    }

    public static String getTaskId() {
        return TASK_ID.get();
    }

    public static String getTaskTitle() {
        return TASK_TITLE.get();
    }

    public static void setExecutionInput(Map<String, Object> executionInput) {
        if (executionInput == null || executionInput.isEmpty()) {
            EXECUTION_INPUT.remove();
        } else {
            EXECUTION_INPUT.set(executionInput);
        }
    }

    public static Map<String, Object> getExecutionInput() {
        return EXECUTION_INPUT.get();
    }

    public static void clear() {
        RUN_ID.remove();
        PHASE.remove();
        TASK_ID.remove();
        TASK_TITLE.remove();
        EXECUTION_INPUT.remove();
    }
}
