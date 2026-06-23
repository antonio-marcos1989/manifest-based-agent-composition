package com.br.honeycombframework.governance;

import com.br.honeycombframework.model.AgentManifest;

/**
 * Ajusta limites de ALA e validações pré-execução conforme o papel do agente na colmeia.
 */
public final class RoleGovernancePolicy {

    private static final int DEFAULT_EVALUATION_WINDOW = 10;

    private RoleGovernancePolicy() {
    }

    public static void validateBeforeDispatch(AgentManifest agent) {
        if (agent.getRole() == AgentManifest.Role.REFEREE
                && (agent.getAlaSettings() == null || agent.getAlaSettings().getMaxErrorPercentage() == null)) {
            throw new RuntimeException(
                    "Agentes REFEREE exigem alaSettings com maxErrorPercentage definido.");
        }

        if (agent.getRole() == AgentManifest.Role.OBSERVER
                && agent.getAlaSettings() != null
                && Boolean.TRUE.equals(agent.getAlaSettings().getStrictContract())
                && (!hasInputContractDefinition(agent) || !hasOutputContractDefinition(agent))) {
            throw new RuntimeException(
                    "Agentes OBSERVER com strictContract exigem inputJsonSchema/outputJsonSchema ou inputContract/outputContract.");
        }
    }

    /**
     * OBSERVER e REFEREE operam via LLM (observabilidade/auditoria) — tipo sempre GENERATIVE.
     */
    public static void normalizeGenerativeRoleTypes(AgentManifest agent) {
        if (agent.getRole() == null) {
            return;
        }
        AgentManifest.Role role = agent.getRole().normalized();
        if (role != AgentManifest.Role.OBSERVER && role != AgentManifest.Role.REFEREE) {
            return;
        }
        agent.setType(AgentManifest.AgentType.GENERATIVE);
    }

    /**
     * Generativo usa Chat (LLM); Classificação/Regressão usam JSON direto.
     */
    public static void normalizePayloadModeForType(AgentManifest agent) {
        if (agent.getType() == null) {
            return;
        }
        agent.setRequestPayloadMode(switch (agent.getType()) {
            case GENERATIVE -> AgentManifest.RequestPayloadMode.CHAT_MESSAGES;
            case CLASSIFICATION, REGRESSION -> AgentManifest.RequestPayloadMode.DIRECT_JSON;
        });
    }

    public static int effectiveEvaluationWindow(AgentManifest agent) {
        int base = agent.getAlaSettings() != null && agent.getAlaSettings().getEvaluationWindow() != null
                ? agent.getAlaSettings().getEvaluationWindow()
                : DEFAULT_EVALUATION_WINDOW;

        if (agent.getRole() == null) {
            return base;
        }

        return switch (agent.getRole()) {
            case REFEREE -> Math.min(base, 5);
            case HUNTER -> base * 2;
            case OBSERVER, COMPONENT -> base;
        };
    }

    public static double effectiveMaxErrorPercentage(AgentManifest agent) {
        if (agent.getAlaSettings() == null || agent.getAlaSettings().getMaxErrorPercentage() == null) {
            return Double.MAX_VALUE;
        }

        double base = agent.getAlaSettings().getMaxErrorPercentage();
        if (agent.getRole() == null) {
            return base;
        }

        return switch (agent.getRole()) {
            case REFEREE -> base * 0.5;
            case HUNTER -> base * 1.5;
            case OBSERVER -> base * 0.75;
            case COMPONENT -> base;
        };
    }

    private static boolean hasInputContractDefinition(AgentManifest agent) {
        return JsonSchemaContractValidator.hasInputSchema(agent)
                || (agent.getInputContract() != null && !agent.getInputContract().isEmpty());
    }

    private static boolean hasOutputContractDefinition(AgentManifest agent) {
        return JsonSchemaContractValidator.hasOutputSchema(agent)
                || (agent.getOutputContract() != null && !agent.getOutputContract().isEmpty());
    }

    public static Integer effectiveMaxLatencyMs(AgentManifest agent) {
        if (agent.getAlaSettings() == null || agent.getAlaSettings().getMaxLatencyMs() == null) {
            return null;
        }

        int base = agent.getAlaSettings().getMaxLatencyMs();
        if (agent.getRole() == null) {
            return base;
        }

        return switch (agent.getRole()) {
            case REFEREE -> (int) (base * 0.8);
            case HUNTER -> (int) (base * 1.5);
            case OBSERVER, COMPONENT -> base;
        };
    }

    public static Double effectiveMinConfidenceScore(AgentManifest agent) {
        if (agent.getAlaSettings() == null || agent.getAlaSettings().getMinConfidenceScore() == null) {
            return null;
        }
        double base = agent.getAlaSettings().getMinConfidenceScore();
        if (agent.getRole() == null) {
            return base;
        }
        return switch (agent.getRole()) {
            case REFEREE -> Math.min(1.0, base + 0.05);
            case OBSERVER -> Math.min(1.0, base + 0.02);
            case HUNTER, COMPONENT -> base;
        };
    }

    public static Double effectiveMaxEstimatedCost(AgentManifest agent) {
        if (agent.getAlaSettings() == null) {
            return null;
        }
        return agent.getAlaSettings().getMaxEstimatedCost();
    }

    public static Integer effectiveMaxTokensPerInvocation(AgentManifest agent) {
        if (agent.getAlaSettings() == null || agent.getAlaSettings().getMaxTokensPerInvocation() == null) {
            return null;
        }
        return agent.getAlaSettings().getMaxTokensPerInvocation();
    }

    public static Double effectiveReliabilityThreshold(AgentManifest agent) {
        if (agent.getAlaSettings() == null) {
            return null;
        }
        return agent.getAlaSettings().getReliabilityThreshold();
    }
}
