package com.br.honeycombframework.governance;

import com.br.honeycombframework.model.AgentManifest;

import java.util.stream.Collectors;

/**
 * Monta a instrução de sistema do agente a partir de descrição e capabilities.
 */
public final class AgentPersonaSupport {

    private AgentPersonaSupport() {
    }

    public static String resolveSystemInstruction(AgentManifest agent) {
        if (agent == null) {
            return defaultInstruction();
        }

        StringBuilder persona = new StringBuilder();

        if (agent.getDescription() != null && !agent.getDescription().isBlank()) {
            persona.append(agent.getDescription().trim());
        }

        if (agent.getCapabilities() != null && !agent.getCapabilities().isEmpty()) {
            String capabilities = agent.getCapabilities().stream()
                    .filter(cap -> cap != null && !cap.isBlank())
                    .map(String::trim)
                    .collect(Collectors.joining(", "));
            if (!capabilities.isBlank()) {
                if (!persona.isEmpty()) {
                    persona.append("\n\n");
                }
                persona.append("Capabilities: ").append(capabilities);
            }
        }

        if (!persona.isEmpty()) {
            return persona.toString();
        }

        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            return agent.getSystemPrompt().trim();
        }

        return defaultInstruction();
    }

    private static String defaultInstruction() {
        return "Você é um agente Honeycomb. Responda conforme solicitado.";
    }
}
