package com.br.honeycombframework.governance;

import com.br.honeycombframework.model.AgentManifest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaContractValidatorTest {

    @Test
    void validatesInputAgainstJsonSchema() {
        AgentManifest agent = new AgentManifest();
        agent.setName("classifier");
        agent.setInputJsonSchema(Map.of(
                "type", "object",
                "required", java.util.List.of("prompt"),
                "properties", Map.of("prompt", Map.of("type", "string"))
        ));

        ContractValidationResult ok = JsonSchemaContractValidator.validateInput(
                agent, Map.of("prompt", "analisar fraude"));
        assertTrue(ok.isCompliant());

        ContractValidationResult fail = JsonSchemaContractValidator.validateInput(
                agent, Map.of("wrongField", "x"));
        assertFalse(fail.isCompliant());
    }

    @Test
    void validatesOutputJsonStringAgainstSchema() {
        AgentManifest agent = new AgentManifest();
        agent.setName("classifier");
        agent.setOutputJsonSchema(Map.of(
                "type", "object",
                "required", java.util.List.of("score", "label"),
                "properties", Map.of(
                        "score", Map.of("type", "number"),
                        "label", Map.of("type", "string")
                )
        ));

        String validJson = "{\"score\": 0.9, \"label\": \"fraud\"}";
        assertTrue(JsonSchemaContractValidator.validateOutput(agent, validJson).isCompliant());

        String invalidJson = "{\"score\": \"high\", \"label\": \"fraud\"}";
        assertFalse(JsonSchemaContractValidator.validateOutput(agent, invalidJson).isCompliant());
    }

    @Test
    void agentContractValidatorPrefersJsonSchemaOverLegacy() {
        AgentManifest agent = new AgentManifest();
        agent.setName("agent");
        agent.setInputJsonSchema(Map.of(
                "type", "object",
                "required", java.util.List.of("data"),
                "properties", Map.of("data", Map.of("type", "string"))
        ));
        agent.setInputContract(Map.of("otherField", "string"));

        ContractValidationResult result = AgentContractValidator.validateInput(
                agent, Map.of("data", "ok"));
        assertTrue(result.isCompliant());
    }
}
