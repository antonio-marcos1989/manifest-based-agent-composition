package com.br.honeycombframework.model;

/**
 * Fases observáveis do pipeline — expostas para ferramentas visuais de monitorização.
 */
public enum ExecutionPhase {
    RESET,
    FEASIBILITY_ASSESSMENT,
    PLAN_READINESS,
    EXECUTION_STARTED,
    TASK_WORKERS,
    TASK_REFEREE_AUDIT,
    GOAL_REFEREE_AUDIT,
    PIPELINE_COMPLETED
}
