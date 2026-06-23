package com.br.honeycombframework.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

class AgentResponseParserTest {

    @Test
    void parsesEnvelopeWithMetricsAndExplanation() {
        String raw = """
                {
                  "data": {"answer": "42"},
                  "explanation": "O modelo inferiu a resposta com base nos dados de vendas.",
                  "metrics": {
                    "tokensInput": 120,
                    "tokensOutput": 45,
                    "model": "llama3",
                    "confidence": 0.91,
                    "estimatedCost": 0.002
                  }
                }
                """;

        ParsedAgentResponse parsed = AgentResponseParser.parse(raw);

        assertNotNull(parsed.getData());
        assertEquals("O modelo inferiu a resposta com base nos dados de vendas.", parsed.getExplanation());
        assertEquals(120, parsed.getMetrics().getTokensInput());
        assertEquals(45, parsed.getMetrics().getTokensOutput());
        assertEquals("llama3", parsed.getMetrics().getModelId());
        assertEquals(0.91, parsed.getMetrics().getConfidenceScore());
        assertEquals(0.002, parsed.getMetrics().getEstimatedCost());
    }

    @Test
    void plainTextResponseUsesBodyAsData() {
        ParsedAgentResponse parsed = AgentResponseParser.parse("resposta simples");
        assertEquals("resposta simples", parsed.getData());
    }

    @Test
    void parsesOllamaChatContentJsonString() {
        String raw = """
                {
                  "model": "gpt-oss:20b-cloud",
                  "message": {
                    "role": "assistant",
                    "content": "{\\"smells\\":[{\\"type\\":\\"Long Method\\"}]}"
                  },
                  "prompt_eval_count": 100,
                  "eval_count": 200
                }
                """;

        ParsedAgentResponse parsed = AgentResponseParser.parse(raw);

        assertNotNull(parsed.getData());
        assertFalse(parsed.getData() instanceof JsonNode);
        assertTrue(parsed.getData() instanceof Map);
        assertEquals(100, parsed.getMetrics().getTokensInput());
        assertEquals(200, parsed.getMetrics().getTokensOutput());
    }

    @Test
    void fallsBackToOllamaThinkingWhenContentEmpty() {
        String raw = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "",
                    "thinking": "{\\"smells\\":[{\\"type\\":\\"Long Parameter List\\"}]}"
                  }
                }
                """;

        ParsedAgentResponse parsed = AgentResponseParser.parse(raw);

        assertNotNull(parsed.getData());
        assertFalse(parsed.getData() instanceof JsonNode);
        assertTrue(parsed.getData() instanceof Map);
        assertTrue(parsed.getData().toString().contains("Long Parameter List"));
    }

    @Test
    void extractsJsonFromThinkingTraceWhenContentEmpty() {
        String raw = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "",
                    "thinking": "Analyzing code... Final answer: {\\"smells\\":[{\\"type\\":\\"Long Method\\"}]}"
                  },
                  "eval_count": 500
                }
                """;

        ParsedAgentResponse parsed = AgentResponseParser.parse(raw);

        assertNotNull(parsed.getData());
        assertTrue(parsed.getData().toString().contains("Long Method"));
        assertEquals(500, AgentResponseParser.ollamaEvalCount(raw));
    }

    @Test
    void normalizesPrefixedJsonFragment() {
        String raw = """
                {
                  "message": {
                    "role": "assistant",
                    "content": "\\"smells\\":[{\\"type\\":\\"Long Parameter List\\"}]}"
                  }
                }
                """;

        ParsedAgentResponse parsed = AgentResponseParser.parse(raw);

        assertNotNull(parsed.getData());
        assertTrue(parsed.getData().toString().contains("Long Parameter List"));
    }

    @Test
    void emptyOllamaMessageShellIsBlankOutput() {
        String raw = """
                {
                  "model": "gpt-oss:20b-cloud",
                  "message": {
                    "role": "assistant",
                    "content": ""
                  }
                }
                """;

        ParsedAgentResponse parsed = AgentResponseParser.parse(raw);

        assertTrue(parsed.getData() == null || AgentResponseParser.isBlankAgentOutput(parsed.getData()));
        assertTrue(AgentResponseParser.isBlankAgentOutput(Map.of("role", "assistant", "content", "")));
    }

    @Test
    void normalizePayloadConvertsNestedStructures() {
        @SuppressWarnings("unchecked")
        Map<String, Object> normalized = (Map<String, Object>) AgentResponseParser.normalizePayload(
                Map.of("nested", Map.of("value", 1)));

        assertEquals(1, ((Map<?, ?>) normalized.get("nested")).get("value"));
    }
}
