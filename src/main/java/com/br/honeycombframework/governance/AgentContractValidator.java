package com.br.honeycombframework.governance;

import com.br.honeycombframework.model.AgentManifest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Valida contratos de agentes: JSON Schema (prioritário) ou mapa legado campo→tipo.
 */
public final class AgentContractValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AgentContractValidator() {
    }

    public static ContractValidationResult validateInput(AgentManifest agent, Map<String, Object> input) {
        if (JsonSchemaContractValidator.hasInputSchema(agent)) {
            return JsonSchemaContractValidator.validateInput(agent, input);
        }
        return validateLegacyInput(agent, input);
    }

    public static ContractValidationResult validateOutput(AgentManifest agent, Object output) {
        if (JsonSchemaContractValidator.hasOutputSchema(agent)) {
            return JsonSchemaContractValidator.validateOutput(agent, output);
        }
        return validateLegacyOutput(agent, output);
    }

    private static ContractValidationResult validateLegacyInput(AgentManifest agent, Map<String, Object> input) {
        if (agent.getInputContract() == null || agent.getInputContract().isEmpty()) {
            return ContractValidationResult.ok();
        }
        if (input == null || input.isEmpty()) {
            return ContractValidationResult.fail(
                    "Input vazio para agente " + agent.getName() + " com inputContract definido.");
        }
        return validateLegacyFields(agent.getName(), "input", agent.getInputContract(), input);
    }

    private static ContractValidationResult validateLegacyOutput(AgentManifest agent, Object output) {
        if (agent.getOutputContract() == null || agent.getOutputContract().isEmpty()) {
            return ContractValidationResult.ok();
        }
        Map<String, Object> outputMap = toMap(output);
        if (outputMap.isEmpty()) {
            return ContractValidationResult.fail(
                    "Output vazio para agente " + agent.getName() + " com outputContract definido.");
        }
        return validateLegacyFields(agent.getName(), "output", agent.getOutputContract(), outputMap);
    }

    private static ContractValidationResult validateLegacyFields(
            String agentName,
            String direction,
            Map<String, String> contract,
            Map<String, Object> payload) {

        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : contract.entrySet()) {
            String field = entry.getKey();
            String expectedType = entry.getValue() != null ? entry.getValue().toLowerCase() : "any";

            if (!payload.containsKey(field)) {
                violations.add(agentName + ": campo obrigatório ausente no " + direction + " -> " + field);
                continue;
            }

            Object value = payload.get(field);
            if (value == null && !"optional".equals(expectedType)) {
                violations.add(agentName + ": campo nulo no " + direction + " -> " + field);
                continue;
            }

            if (value != null && !"any".equals(expectedType) && !"optional".equals(expectedType)
                    && !matchesType(value, expectedType)) {
                violations.add(agentName + ": tipo inválido no " + direction + " -> " + field
                        + " (esperado " + expectedType + ", recebido " + value.getClass().getSimpleName() + ")");
            }
        }

        if (violations.isEmpty()) {
            return ContractValidationResult.ok();
        }
        return ContractValidationResult.builder().compliant(false).violations(violations).build();
    }

    private static boolean matchesType(Object value, String expectedType) {
        return switch (expectedType) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map;
            case "array", "list" -> value instanceof List;
            default -> true;
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object output) {
        if (output == null) {
            return Map.of();
        }
        if (output instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        if (output instanceof String str) {
            try {
                return MAPPER.readValue(str, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return Map.of("response", str);
            }
        }
        return Map.of("response", output);
    }
}
