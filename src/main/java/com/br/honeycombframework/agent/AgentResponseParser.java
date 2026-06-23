package com.br.honeycombframework.agent;

import com.br.honeycombframework.model.AgentInvocationMetrics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentResponseParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");

    private AgentResponseParser() {
    }

    public static ParsedAgentResponse parse(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return ParsedAgentResponse.builder()
                    .data(null)
                    .build();
        }

        String trimmed = rawBody.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            AgentInvocationMetrics metrics = new AgentInvocationMetrics();
            metrics.setOutputSizeBytes((long) trimmed.length());
            return ParsedAgentResponse.builder()
                    .data(trimmed)
                    .metrics(metrics)
                    .build();
        }

        try {
            JsonNode root = MAPPER.readTree(extractJson(trimmed));
            Object extracted = extractData(root);
            Object data = extracted != null && !isBlankAgentOutput(extracted)
                    ? normalizePayload(extracted)
                    : null;
            String explanation = textOrNull(root.path("explanation"));
            if (explanation == null) {
                explanation = textOrNull(root.path("llmExplanation"));
            }
            if (explanation == null) {
                explanation = textOrNull(root.path("reasoning"));
            }

            AgentInvocationMetrics metrics = parseMetricsNode(root.path("metrics"));
            mergeRootMetricHints(root, metrics);

            return ParsedAgentResponse.builder()
                    .data(data)
                    .explanation(explanation)
                    .metrics(metrics)
                    .build();
        } catch (Exception e) {
            return ParsedAgentResponse.builder()
                    .data(trimmed)
                    .metrics(metricsWithError(AgentInvocationMetrics.ErrorType.PARSE))
                    .build();
        }
    }

    private static Object extractData(JsonNode root) {
        if (root.has("choices") && root.get("choices").isArray() && !root.get("choices").isEmpty()) {
            JsonNode firstChoice = root.get("choices").get(0);
            if (firstChoice.has("message") && firstChoice.get("message").isObject()) {
                Object fromChoice = extractMessagePayload(firstChoice.get("message"));
                if (fromChoice != null && !isBlankPayload(fromChoice)) {
                    return fromChoice;
                }
            }
        }
        if (root.has("message") && root.get("message").isObject()) {
            Object fromMessage = extractMessagePayload(root.get("message"));
            if (fromMessage != null && !isBlankPayload(fromMessage)) {
                return fromMessage;
            }
        }
        Object fromRootThinking = unwrapJsonText(textOrNull(root.path("thinking")));
        if (fromRootThinking != null && !isBlankPayload(fromRootThinking)) {
            return fromRootThinking;
        }
        if (root.has("response")) {
            Object fromResponse = unwrapJsonText(textOrNull(root.path("response")));
            if (fromResponse != null && !isBlankPayload(fromResponse)) {
                return fromResponse;
            }
        }
        if (root.has("data")) {
            JsonNode dataNode = root.get("data");
            if (dataNode.isTextual()) {
                return unwrapJsonText(dataNode.asText());
            }
            return normalizePayload(dataNode);
        }
        if (root.has("result")) {
            return normalizePayload(root.get("result"));
        }
        if (root.has("output")) {
            return normalizePayload(root.get("output"));
        }
        if (root.has("approved")) {
            return normalizePayload(root);
        }
        return null;
    }

    /**
     * Ollama Chat (incl. modelos com {@code thinking}): prioriza {@code content}, depois {@code thinking}.
     */
    private static Object extractMessagePayload(JsonNode message) {
        Object content = normalizeJsonFragment(textOrNull(message.path("content")));
        if (content != null && !isBlankPayload(content)) {
            return content;
        }
        Object thinking = extractJsonFromReasoning(textOrNull(message.path("thinking")));
        if (thinking != null && !isBlankPayload(thinking)) {
            return thinking;
        }
        return extractJsonFromReasoning(textOrNull(message.path("reasoning")));
    }

    /** gpt-oss com prefill pode devolver JSON sem a chave inicial `{`. */
    private static Object normalizeJsonFragment(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[") && trimmed.contains("\"")) {
            trimmed = "{" + trimmed;
        }
        return unwrapJsonText(trimmed);
    }

    /** Extrai o último objeto JSON em traces de raciocínio (gpt-oss / thinking). */
    static Object extractJsonFromReasoning(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Object direct = unwrapJsonText(text.trim());
        if (direct != null && !isBlankPayload(direct)) {
            return direct;
        }
        String jsonBlock = extractLastJsonObject(text);
        return jsonBlock != null ? unwrapJsonText(jsonBlock) : null;
    }

    static String extractLastJsonObject(String text) {
        int lastClose = text.lastIndexOf('}');
        if (lastClose < 0) {
            return null;
        }
        int depth = 0;
        for (int i = lastClose; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == '}') {
                depth++;
            } else if (c == '{') {
                depth--;
                if (depth == 0) {
                    return text.substring(i, lastClose + 1);
                }
            }
        }
        return null;
    }

    /** Tokens gerados reportados pelo Ollama (útil para retry em gpt-oss). */
    public static int ollamaEvalCount(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = MAPPER.readTree(rawBody.trim());
            if (root.has("eval_count") && root.get("eval_count").isNumber()) {
                return root.get("eval_count").asInt(0);
            }
        } catch (Exception ignored) {
            // ignore
        }
        return 0;
    }

    /**
     * Detecta envelope vazio do Ollama ({@code role}+{@code content} em branco) ou payload sem texto utilizável.
     */
    public static boolean isBlankAgentOutput(Object payload) {
        if (payload == null) {
            return true;
        }
        if (payload instanceof String text) {
            if (text.isBlank()) {
                return true;
            }
            String trimmed = text.trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    return isBlankAgentOutput(MAPPER.readTree(trimmed));
                } catch (Exception ignored) {
                    return false;
                }
            }
            return false;
        }
        if (payload instanceof JsonNode node) {
            if (node.isTextual()) {
                return node.asText().isBlank();
            }
            if (node.has("message") && node.get("message").isObject()) {
                Object fromMessage = extractMessagePayload(node.get("message"));
                if (fromMessage != null && !isBlankPayload(fromMessage)) {
                    return false;
                }
                return true;
            }
            if (node.has("choices") && node.get("choices").isArray() && !node.get("choices").isEmpty()) {
                return isBlankAgentOutput(node.get("choices").get(0));
            }
            String rootThinking = textOrNull(node.path("thinking"));
            if (rootThinking != null) {
                return false;
            }
            String rootResponse = textOrNull(node.path("response"));
            if (rootResponse != null) {
                return false;
            }
            if (node.has("role") && node.has("content")) {
                String content = textOrNull(node.path("content"));
                String thinking = textOrNull(node.path("thinking"));
                String reasoning = textOrNull(node.path("reasoning"));
                return content == null && thinking == null && reasoning == null;
            }
            if (node.has("content") && node.size() == 1) {
                return textOrNull(node.path("content")) == null;
            }
            return false;
        }
        if (payload instanceof Map<?, ?> map) {
            if (map.containsKey("role") && map.containsKey("content")) {
                Object content = map.get("content");
                Object thinking = map.get("thinking");
                Object reasoning = map.get("reasoning");
                return isBlankAgentOutput(content)
                        && isBlankAgentOutput(thinking)
                        && isBlankAgentOutput(reasoning);
            }
            if (map.size() == 1 && map.containsKey("content")) {
                return isBlankAgentOutput(map.get("content"));
            }
            return map.isEmpty();
        }
        String asText = String.valueOf(payload);
        return asText.isBlank() || "null".equals(asText);
    }

    private static Object unwrapJsonText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed;
        }
        try {
            return normalizePayload(MAPPER.readTree(trimmed));
        } catch (Exception ignored) {
            return trimmed;
        }
    }

    /** Converte {@link JsonNode} em Map/List/primitivos serializáveis (MongoDB-safe). */
    public static Object normalizePayload(Object value) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof JsonNode node) {
            return normalizeJsonNode(node);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, entry) -> normalized.put(String.valueOf(key), normalizePayload(entry)));
            return normalized;
        }
        if (value instanceof List<?> list) {
            List<Object> normalized = new ArrayList<>(list.size());
            for (Object item : list) {
                normalized.add(normalizePayload(item));
            }
            return normalized;
        }
        return value;
    }

    private static Object normalizeJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isFloatingPointNumber()) {
            return node.asDouble();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(child -> list.add(normalizeJsonNode(child)));
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> map.put(entry.getKey(), normalizeJsonNode(entry.getValue())));
            return map;
        }
        return node.asText();
    }

    private static boolean isBlankPayload(Object payload) {
        if (payload == null) {
            return true;
        }
        if (payload instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    /** Serializa {@code data} para parsing de JSON do REFEREE/OBSERVER. */
    public static String toParseableJson(Object data) {
        if (data == null) {
            return "";
        }
        if (data instanceof String s) {
            return s;
        }
        if (data instanceof JsonNode node) {
            try {
                return MAPPER.writeValueAsString(normalizeJsonNode(node));
            } catch (Exception e) {
                return node.toString();
            }
        }
        try {
            return MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            return String.valueOf(data);
        }
    }

    private static AgentInvocationMetrics parseMetricsNode(JsonNode metricsNode) {
        AgentInvocationMetrics metrics = new AgentInvocationMetrics();
        if (metricsNode == null || metricsNode.isMissingNode() || !metricsNode.isObject()) {
            return metrics;
        }
        metrics.setTokensInput(intOrNull(metricsNode.path("tokensInput")));
        metrics.setTokensOutput(intOrNull(metricsNode.path("tokensOutput")));
        metrics.setTokensTotal(intOrNull(metricsNode.path("tokensTotal")));
        metrics.setEstimatedCost(doubleOrNull(metricsNode.path("estimatedCost")));
        metrics.setModelId(textOrNull(metricsNode.path("modelId")));
        if (metrics.getModelId() == null) {
            metrics.setModelId(textOrNull(metricsNode.path("model")));
        }
        metrics.setModelVersion(textOrNull(metricsNode.path("modelVersion")));
        metrics.setConfidenceScore(doubleOrNull(metricsNode.path("confidenceScore")));
        if (metrics.getConfidenceScore() == null) {
            metrics.setConfidenceScore(doubleOrNull(metricsNode.path("confidence")));
        }
        metrics.setCandidateScore(doubleOrNull(metricsNode.path("candidateScore")));
        metrics.setOutputSizeBytes(longOrNull(metricsNode.path("outputSizeBytes")));
        metrics.setRetryCount(intOrNull(metricsNode.path("retryCount")));
        return metrics;
    }

    private static void mergeRootMetricHints(JsonNode root, AgentInvocationMetrics metrics) {
        if (metrics.getConfidenceScore() == null) {
            metrics.setConfidenceScore(doubleOrNull(root.path("confidenceScore")));
        }
        if (metrics.getCandidateScore() == null) {
            metrics.setCandidateScore(doubleOrNull(root.path("candidateScore")));
        }
        if (metrics.getTokensInput() == null) {
            metrics.setTokensInput(intOrNull(root.path("prompt_eval_count")));
        }
        if (metrics.getTokensOutput() == null) {
            metrics.setTokensOutput(intOrNull(root.path("eval_count")));
        }
        if (metrics.getTokensTotal() == null
                && metrics.getTokensInput() != null
                && metrics.getTokensOutput() != null) {
            metrics.setTokensTotal(metrics.getTokensInput() + metrics.getTokensOutput());
        }
        if (metrics.getModelId() == null) {
            metrics.setModelId(textOrNull(root.path("model")));
        }
    }

    private static String extractJson(String raw) {
        if (raw.startsWith("```")) {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return raw.substring(start, end + 1);
            }
        }
        Matcher matcher = JSON_BLOCK.matcher(raw);
        if (matcher.find()) {
            return matcher.group();
        }
        return raw;
    }

    private static Integer intOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private static Long longOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asLong() : null;
    }

    private static Double doubleOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asDouble() : null;
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText(null);
        return text != null && !text.isBlank() ? text : null;
    }

    private static AgentInvocationMetrics metricsWithError(AgentInvocationMetrics.ErrorType errorType) {
        AgentInvocationMetrics metrics = new AgentInvocationMetrics();
        metrics.setErrorType(errorType);
        return metrics;
    }
}
