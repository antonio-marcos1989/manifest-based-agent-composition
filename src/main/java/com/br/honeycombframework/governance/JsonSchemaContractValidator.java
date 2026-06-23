package com.br.honeycombframework.governance;

import com.br.honeycombframework.model.AgentManifest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Valida payloads contra JSON Schema (draft-07) definido no manifesto do agente.
 */
public final class JsonSchemaContractValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonSchemaFactory SCHEMA_FACTORY =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    private JsonSchemaContractValidator() {
    }

    public static boolean hasInputSchema(AgentManifest agent) {
        return agent.getInputJsonSchema() != null && !agent.getInputJsonSchema().isEmpty();
    }

    public static boolean hasOutputSchema(AgentManifest agent) {
        return agent.getOutputJsonSchema() != null && !agent.getOutputJsonSchema().isEmpty();
    }

    public static ContractValidationResult validateInput(AgentManifest agent, Map<String, Object> input) {
        return validate(agent.getName(), "input", agent.getInputJsonSchema(), input);
    }

    public static ContractValidationResult validateOutput(AgentManifest agent, Object output) {
        return validate(agent.getName(), "output", agent.getOutputJsonSchema(), toJsonNode(output));
    }

    private static ContractValidationResult validate(
            String agentName,
            String direction,
            Map<String, Object> schemaMap,
            Object payload) {

        try {
            JsonSchema schema = compile(schemaMap);
            JsonNode dataNode = toJsonNode(payload);

            if (dataNode == null || dataNode.isNull()) {
                return ContractValidationResult.fail(
                        agentName + ": payload " + direction + " ausente para validação JSON Schema.");
            }

            Set<ValidationMessage> errors = schema.validate(dataNode);
            if (errors.isEmpty()) {
                return ContractValidationResult.ok();
            }

            List<String> violations = errors.stream()
                    .map(msg -> agentName + " [" + direction + " JSON Schema]: " + formatMessage(msg))
                    .collect(Collectors.toCollection(ArrayList::new));

            return ContractValidationResult.builder()
                    .compliant(false)
                    .violations(violations)
                    .build();
        } catch (IllegalArgumentException e) {
            return ContractValidationResult.fail(
                    agentName + ": JSON Schema de " + direction + " inválido — " + e.getMessage());
        } catch (Exception e) {
            return ContractValidationResult.fail(
                    agentName + ": falha ao validar " + direction + " com JSON Schema — " + e.getMessage());
        }
    }

    private static JsonSchema compile(Map<String, Object> schemaMap) {
        if (schemaMap == null || schemaMap.isEmpty()) {
            throw new IllegalArgumentException("schema vazio");
        }
        if (!schemaMap.containsKey("type") && !schemaMap.containsKey("$schema")) {
            throw new IllegalArgumentException("schema deve definir ao menos 'type' ou '$schema'");
        }
        JsonNode schemaNode = MAPPER.valueToTree(schemaMap);
        return SCHEMA_FACTORY.getSchema(schemaNode);
    }

    private static JsonNode toJsonNode(Object payload) {
        if (payload == null) {
            return MAPPER.nullNode();
        }
        if (payload instanceof JsonNode node) {
            return node;
        }
        if (payload instanceof String str) {
            try {
                return MAPPER.readTree(str);
            } catch (Exception e) {
                return MAPPER.createObjectNode().put("response", str);
            }
        }
        return MAPPER.valueToTree(payload);
    }

    private static String formatMessage(ValidationMessage message) {
        String path = message.getInstanceLocation() != null
                ? message.getInstanceLocation().toString()
                : "";
        if (path.isBlank()) {
            return message.getMessage();
        }
        return path + " — " + message.getMessage();
    }
}
