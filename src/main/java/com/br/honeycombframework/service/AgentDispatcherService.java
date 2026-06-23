package com.br.honeycombframework.service;

import com.br.honeycombframework.agent.AgentResponseParser;
import com.br.honeycombframework.agent.ParsedAgentResponse;
import com.br.honeycombframework.governance.AgentCredentialSupport;
import com.br.honeycombframework.governance.AgentPersonaSupport;
import com.br.honeycombframework.governance.RoleGovernancePolicy;
import com.br.honeycombframework.model.AgentConnectionTestResult;
import com.br.honeycombframework.model.AgentExecutionLog;
import com.br.honeycombframework.model.AgentInvocationMetrics;
import com.br.honeycombframework.model.AgentManifest;
import com.br.honeycombframework.model.ExecutionResponse;
import com.br.honeycombframework.model.ExecutionStreamEvent;
import com.br.honeycombframework.model.ExecutionStreamEvent.EventType;
import com.br.honeycombframework.repository.AgentExecutionLogQuery;
import com.br.honeycombframework.repository.AgentExecutionLogRepository;
import com.br.honeycombframework.repository.AgentManifestRepository;
import com.br.honeycombframework.orchestrator.ExecutionTraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentDispatcherService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AgentManifestService manifestService;
    private final AgentManifestRepository manifestRepository;
    private final AgentExecutionLogRepository logRepository;
    private final AgentExecutionLogQuery executionLogQuery;
    private final ExecutionNotificationService notificationService;
    private final RestClient restClient;

    public ExecutionResponse dispatch(String agentId, Map<String, Object> inputData) {
        return dispatch(agentId, inputData, null, null);
    }

    /**
     * Invoca o endpoint do agente, extrai métricas/explicação do envelope e persiste execution_logs.
     */
    public ExecutionResponse dispatch(
            String agentId,
            Map<String, Object> inputData,
            String goalId,
            String taskId) {

        AgentManifest agent = manifestService.findById(agentId)
                .orElseThrow(() -> new RuntimeException("Agente não encontrado no catálogo."));

        if (agent.getActive() != null && !agent.getActive()) {
            throw new RuntimeException("O Agente '" + agent.getName() + "' está desativado por violação de políticas.");
        }

        RoleGovernancePolicy.validateBeforeDispatch(agent);

        publishAgentEvent(EventType.AGENT_STARTED, agent, goalId, taskId, null, null,
                "Invocando agente " + agent.getName() + "…");

        long startTime = System.currentTimeMillis();
        long inputSize = estimatePayloadSize(inputData);

        try {
            AgentHttpExchange exchange = invokeAgentHttp(agent, inputData, GptOssTuning.initial());
            if (shouldRetryGptOssBlank(agent, exchange)) {
                exchange = invokeAgentHttp(agent, inputData, GptOssTuning.retry());
            }

            long latency = System.currentTimeMillis() - startTime;
            int httpStatus = exchange.httpStatus();
            String rawBody = exchange.rawBody();
            ParsedAgentResponse parsed = exchange.parsed();

            AgentInvocationMetrics metrics = mergeMetrics(
                    parsed.getMetrics(), latency, httpStatus, agent, rawBody);

            boolean alaOk = isAlaCompliant(agent, metrics);
            metrics.setAlaCompliant(alaOk);
            metrics.setStatus(alaOk ? "SUCCESS" : "ALA_LATENCY_VIOLATION");
            if (!alaOk) {
                metrics.setErrorType(AgentInvocationMetrics.ErrorType.ALA_LATENCY);
            } else {
                metrics.setErrorType(AgentInvocationMetrics.ErrorType.NONE);
            }

            saveLog(agent, goalId, taskId, metrics, parsed.getExplanation(), inputSize);

            publishAgentEvent(EventType.AGENT_COMPLETED, agent, goalId, taskId, latency, "SUCCESS",
                    "Agente " + agent.getName() + " concluído (" + latency + " ms)");

            ExecutionResponse response = ExecutionResponse.builder()
                    .agentId(agent.getId())
                    .data(resolveDispatchData(parsed.getData(), rawBody))
                    .latencyMs(latency)
                    .alaCompliant(alaOk)
                    .status(metrics.getStatus())
                    .httpStatusCode(httpStatus)
                    .metrics(metrics)
                    .agentExplanation(parsed.getExplanation())
                    .build();

            checkAgentHealth(agentId);
            return response;
        } catch (RestClientResponseException e) {
            long latency = System.currentTimeMillis() - startTime;
            AgentInvocationMetrics metrics = new AgentInvocationMetrics();
            metrics.setLatencyMs(latency);
            metrics.setHttpStatusCode(e.getStatusCode().value());
            metrics.setAlaCompliant(false);
            metrics.setStatus("HTTP_ERROR");
            metrics.setErrorType(AgentInvocationMetrics.ErrorType.HTTP_ERROR);
            saveLog(agent, goalId, taskId, metrics, null, inputSize);
            publishAgentEvent(EventType.AGENT_FAILED, agent, goalId, taskId, latency, "HTTP_ERROR",
                    "Erro HTTP " + e.getStatusCode().value() + " do agente " + agent.getName());
            throw new RuntimeException("Erro HTTP " + e.getStatusCode().value() + " do agente "
                    + agent.getName() + ": " + e.getMessage());
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            AgentInvocationMetrics metrics = new AgentInvocationMetrics();
            metrics.setLatencyMs(latency);
            metrics.setAlaCompliant(false);
            metrics.setStatus("COMMUNICATION_ERROR");
            metrics.setErrorType(AgentInvocationMetrics.ErrorType.NETWORK);
            saveLog(agent, goalId, taskId, metrics, null, inputSize);
            publishAgentEvent(EventType.AGENT_FAILED, agent, goalId, taskId, latency, "NETWORK",
                    "Falha na comunicação com o agente " + agent.getName());
            throw new RuntimeException("Falha crítica na comunicação com o agente: " + e.getMessage());
        }
    }

    private AgentInvocationMetrics mergeMetrics(
            AgentInvocationMetrics fromAgent,
            long latencyMs,
            int httpStatus,
            AgentManifest agent,
            String rawBody) {
        AgentInvocationMetrics metrics = fromAgent != null ? fromAgent : new AgentInvocationMetrics();
        metrics.setLatencyMs(latencyMs);
        metrics.setHttpStatusCode(httpStatus);
        if (metrics.getOutputSizeBytes() == null && rawBody != null) {
            metrics.setOutputSizeBytes((long) rawBody.length());
        }
        if (metrics.getTokensTotal() == null
                && metrics.getTokensInput() != null
                && metrics.getTokensOutput() != null) {
            metrics.setTokensTotal(metrics.getTokensInput() + metrics.getTokensOutput());
        }
        if (metrics.getModelId() == null && agent.getName() != null) {
            metrics.setModelId(agent.getName());
        }
        return metrics;
    }

    private boolean isAlaCompliant(AgentManifest agent, AgentInvocationMetrics metrics) {
        Integer maxLatencyMs = RoleGovernancePolicy.effectiveMaxLatencyMs(agent);
        if (maxLatencyMs == null || metrics.getLatencyMs() == null) {
            return true;
        }
        return metrics.getLatencyMs() <= maxLatencyMs;
    }

    private void checkAgentHealth(String agentId) {
        AgentManifest agent = manifestRepository.findById(agentId).orElse(null);
        if (agent == null || agent.getAlaSettings() == null || agent.getAlaSettings().getMaxErrorPercentage() == null) {
            return;
        }

        int window = RoleGovernancePolicy.effectiveEvaluationWindow(agent);
        double maxErrorPercentage = RoleGovernancePolicy.effectiveMaxErrorPercentage(agent);

        List<AgentExecutionLog> recentLogs = executionLogQuery.findRecentByAgentId(agentId, window);

        if (recentLogs.size() < window) {
            return;
        }

        long violations = recentLogs.stream().filter(log -> !Boolean.TRUE.equals(log.getAlaCompliant())).count();
        double errorRate = ((double) violations / recentLogs.size()) * 100;

        if (errorRate >= maxErrorPercentage) {
            agent.setActive(false);
            manifestRepository.save(agent);
        }
    }

    private void saveLog(
            AgentManifest agent,
            String goalId,
            String taskId,
            AgentInvocationMetrics metrics,
            String explanation,
            long inputPayloadSize) {
        AgentExecutionLog log = new AgentExecutionLog();
        log.setGoalId(goalId);
        log.setExecutionRunId(ExecutionTraceContext.getRunId());
        log.setTaskId(taskId);
        log.setAgentId(agent.getId());
        log.setAgentName(agent.getName());
        log.setAgentRole(agent.getRole() != null ? agent.getRole().name() : null);
        log.setLatencyMs(metrics.getLatencyMs());
        log.setHttpStatusCode(metrics.getHttpStatusCode());
        log.setAlaCompliant(metrics.getAlaCompliant());
        log.setStatus(metrics.getStatus());
        log.setErrorType(metrics.getErrorType());
        log.setTokensInput(metrics.getTokensInput());
        log.setTokensOutput(metrics.getTokensOutput());
        log.setTokensTotal(metrics.getTokensTotal());
        log.setEstimatedCost(metrics.getEstimatedCost());
        log.setModelId(metrics.getModelId());
        log.setConfidenceScore(metrics.getConfidenceScore());
        log.setCandidateScore(metrics.getCandidateScore());
        log.setOutputSizeBytes(metrics.getOutputSizeBytes());
        log.setInputPayloadSizeBytes(inputPayloadSize);
        log.setAgentExplanation(explanation);
        log.setTimestamp(LocalDateTime.now());
        logRepository.save(log);
    }

    private void publishAgentEvent(
            EventType type,
            AgentManifest agent,
            String goalId,
            String taskId,
            Long latencyMs,
            String agentStatus,
            String message) {
        String runId = ExecutionTraceContext.getRunId();
        if (runId == null || runId.isBlank()) {
            return;
        }
        notificationService.publish(ExecutionStreamEvent.builder()
                .type(type)
                .goalId(goalId)
                .runId(runId)
                .phase(ExecutionTraceContext.getPhase())
                .taskId(taskId != null ? taskId : ExecutionTraceContext.getTaskId())
                .taskTitle(ExecutionTraceContext.getTaskTitle())
                .agentId(agent.getId())
                .agentName(agent.getName())
                .agentRole(agent.getRole() != null ? agent.getRole().name() : null)
                .latencyMs(latencyMs)
                .agentStatus(agentStatus)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build());
    }

    private long estimatePayloadSize(Map<String, Object> inputData) {
        if (inputData == null || inputData.isEmpty()) {
            return 0;
        }
        try {
            return MAPPER.writeValueAsBytes(inputData).length;
        } catch (Exception e) {
            return inputData.toString().length();
        }
    }

    private static String decodeResponseBody(byte[] body) {
        if (body == null || body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private AgentHttpExchange invokeAgentHttp(
            AgentManifest agent,
            Map<String, Object> inputData,
            GptOssTuning gptOssTuning) {
        Map<String, Object> requestBody = buildRequestBody(agent, inputData, gptOssTuning);
        ResponseEntity<byte[]> httpResponse = restClient.post()
                .uri(agent.getEndpointUrl())
                .headers(headers -> applyHeaders(agent, headers))
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM)
                .body(requestBody)
                .retrieve()
                .toEntity(byte[].class);

        String rawBody = decodeResponseBody(httpResponse.getBody());
        ParsedAgentResponse parsed = AgentResponseParser.parse(rawBody);
        return new AgentHttpExchange(httpResponse.getStatusCode().value(), rawBody, parsed);
    }

    private boolean shouldRetryGptOssBlank(AgentManifest agent, AgentHttpExchange exchange) {
        if (!usesGptOssModel(agent)) {
            return false;
        }
        Object data = resolveDispatchData(exchange.parsed().getData(), exchange.rawBody());
        if (!AgentResponseParser.isBlankAgentOutput(data)) {
            return false;
        }
        return AgentResponseParser.ollamaEvalCount(exchange.rawBody()) > 0;
    }

    private record AgentHttpExchange(int httpStatus, String rawBody, ParsedAgentResponse parsed) {
    }

    private record GptOssTuning(String thinkLevel, boolean jsonPrefill) {
        static GptOssTuning initial() {
            return new GptOssTuning("low", false);
        }

        static GptOssTuning retry() {
            return new GptOssTuning("medium", true);
        }
    }

    private static Object resolveDispatchData(Object data, String rawBody) {
        if (data != null && !AgentResponseParser.isBlankAgentOutput(data)) {
            return AgentResponseParser.normalizePayload(data);
        }
        if (rawBody != null && !rawBody.isBlank()) {
            ParsedAgentResponse reparsed = AgentResponseParser.parse(rawBody);
            if (reparsed.getData() != null && !AgentResponseParser.isBlankAgentOutput(reparsed.getData())) {
                return AgentResponseParser.normalizePayload(reparsed.getData());
            }
        }
        return null;
    }

    private void applyHeaders(AgentManifest agent, HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (headers.getAccept().isEmpty()) {
            headers.setAccept(List.of(
                    MediaType.APPLICATION_JSON,
                    MediaType.TEXT_PLAIN,
                    MediaType.APPLICATION_OCTET_STREAM));
        }
        if (agent.getHttpHeaders() != null) {
            agent.getHttpHeaders().forEach((key, value) -> {
                if (key != null && !"authorization".equalsIgnoreCase(key.trim())) {
                    headers.set(key, AgentCredentialSupport.resolve(value));
                }
            });
        }
        String authorization = AgentCredentialSupport.resolveAuthorizationHeader(agent);
        if (authorization != null && !authorization.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
    }

    private Map<String, Object> buildRequestBody(AgentManifest agent, Map<String, Object> inputData) {
        return buildRequestBody(agent, inputData, GptOssTuning.initial());
    }

    private Map<String, Object> buildRequestBody(
            AgentManifest agent,
            Map<String, Object> inputData,
            GptOssTuning gptOssTuning) {
        if (agent.getRequestPayloadMode() != AgentManifest.RequestPayloadMode.CHAT_MESSAGES) {
            return inputData != null ? inputData : Map.of();
        }

        if (inputData != null && inputData.containsKey("messages")) {
            return inputData;
        }

        String model = agent.getDefaultModel();
        if (inputData != null && inputData.get("model") != null) {
            model = String.valueOf(inputData.get("model"));
        }

        String systemInstruction = resolveSystemInstruction(agent, inputData);

        Map<String, Object> payload = new LinkedHashMap<>();
        if (model != null && !model.isBlank()) {
            payload.put("model", model);
        }
        payload.put("stream", false);

        if (usesGptOssModel(agent)) {
            payload.put("think", gptOssTuning.thinkLevel());
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemInstruction));

        String userContent = toJson(inputData == null ? Map.of() : stripControlKeys(inputData));
        messages.add(Map.of("role", "user", "content", userContent));

        if (usesGptOssModel(agent) && gptOssTuning.jsonPrefill()
                && Boolean.TRUE.equals(agent.getForceJsonResponse())) {
            messages.add(Map.of("role", "assistant", "content", "{"));
        }

        payload.put("messages", messages);

        if (Boolean.TRUE.equals(agent.getForceJsonResponse()) && supportsOllamaStructuredFormat(agent)) {
            payload.put("format", "json");
        }

        return payload;
    }

    private String resolveSystemInstruction(AgentManifest agent, Map<String, Object> inputData) {
        String systemInstruction = inputData != null && inputData.get("instruction") != null
                ? String.valueOf(inputData.get("instruction"))
                : AgentPersonaSupport.resolveSystemInstruction(agent);
        if (usesGptOssModel(agent) && Boolean.TRUE.equals(agent.getForceJsonResponse())) {
            systemInstruction = systemInstruction
                    + "\n\nIMPORTANTE: a resposta final deve ser JSON valido no campo content (nao em thinking). "
                    + "Sem markdown. Comece com { e termine com }.";
        }
        return systemInstruction;
    }

    private static boolean usesGptOssModel(AgentManifest agent) {
        String model = agent.getDefaultModel();
        return model != null && model.toLowerCase(Locale.ROOT).contains("gpt-oss");
    }

    private static boolean supportsOllamaStructuredFormat(AgentManifest agent) {
        return !usesGptOssModel(agent);
    }

    private Map<String, Object> stripControlKeys(Map<String, Object> input) {
        Map<String, Object> clean = new java.util.LinkedHashMap<>(input);
        clean.remove("model");
        clean.remove("messages");
        clean.remove("stream");
        clean.remove("format");
        return clean;
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * Ping leve ao endpoint configurado no manifest (sem persistir logs nem exigir agent salvo).
     */
    public AgentConnectionTestResult testConnection(AgentManifest agent, String mergeCredentialsFromId) {
        if (agent.getEndpointUrl() == null || agent.getEndpointUrl().isBlank()) {
            throw new com.br.honeycombframework.exception.ValidationException("endpointUrl é obrigatório para testar a conexão.");
        }

        mergeStoredCredentials(agent, mergeCredentialsFromId);

        if (agent.getType() == null) {
            agent.setType(AgentManifest.AgentType.GENERATIVE);
        }
        if (agent.getName() == null || agent.getName().isBlank()) {
            agent.setName("connection-test");
        }
        if (agent.getRole() == null) {
            agent.setRole(AgentManifest.Role.COMPONENT);
        }
        if (agent.getRequestPayloadMode() == null) {
            agent.setRequestPayloadMode(
                    agent.getType() == AgentManifest.AgentType.GENERATIVE
                            ? AgentManifest.RequestPayloadMode.CHAT_MESSAGES
                            : AgentManifest.RequestPayloadMode.DIRECT_JSON);
        }

        if (agent.getRequestPayloadMode() == AgentManifest.RequestPayloadMode.CHAT_MESSAGES
                && (agent.getDefaultModel() == null || agent.getDefaultModel().isBlank())) {
            throw new com.br.honeycombframework.exception.ValidationException(
                    "defaultModel é obrigatório para testar conexão em modo Chat.");
        }

        AgentCredentialSupport.validateForRegister(agent);
        manifestService.applyDefaultAlaSettings(agent);

        Map<String, Object> testInput = Map.of("message", "Honeycomb connection test");

        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> requestBody = buildRequestBody(agent, testInput);
            ResponseEntity<byte[]> httpResponse = restClient.post()
                    .uri(agent.getEndpointUrl())
                    .headers(headers -> applyHeaders(agent, headers))
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM)
                    .body(requestBody)
                    .retrieve()
                    .toEntity(byte[].class);

            long latency = System.currentTimeMillis() - startTime;
            int httpStatus = httpResponse.getStatusCode().value();
            String rawBody = decodeResponseBody(httpResponse.getBody());

            return AgentConnectionTestResult.builder()
                    .success(httpStatus >= 200 && httpStatus < 300)
                    .latencyMs(latency)
                    .httpStatusCode(httpStatus)
                    .message(httpStatus >= 200 && httpStatus < 300
                            ? "Conexão estabelecida com sucesso."
                            : "Endpoint respondeu com status HTTP " + httpStatus + ".")
                    .responsePreview(truncatePreview(rawBody))
                    .modelUsed(agent.getDefaultModel())
                    .build();
        } catch (RestClientResponseException e) {
            long latency = System.currentTimeMillis() - startTime;
            String body = decodeResponseBody(e.getResponseBodyAsByteArray());
            return AgentConnectionTestResult.builder()
                    .success(false)
                    .latencyMs(latency)
                    .httpStatusCode(e.getStatusCode().value())
                    .message("Erro HTTP " + e.getStatusCode().value() + " ao contactar o endpoint.")
                    .responsePreview(truncatePreview(body))
                    .modelUsed(agent.getDefaultModel())
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            return AgentConnectionTestResult.builder()
                    .success(false)
                    .latencyMs(latency)
                    .message("Falha na comunicação: " + e.getMessage())
                    .modelUsed(agent.getDefaultModel())
                    .build();
        }
    }

    private void mergeStoredCredentials(AgentManifest agent, String mergeCredentialsFromId) {
        if (mergeCredentialsFromId == null || mergeCredentialsFromId.isBlank()) {
            return;
        }
        manifestService.findById(mergeCredentialsFromId).ifPresent(stored -> {
            if (agent.getAuthApiKey() == null || agent.getAuthApiKey().isBlank()) {
                agent.setAuthApiKey(stored.getAuthApiKey());
            }
            if (agent.getHttpHeaders() == null || agent.getHttpHeaders().isEmpty()) {
                agent.setHttpHeaders(stored.getHttpHeaders());
            }
        });
    }

    private static String truncatePreview(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = raw.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 280 ? normalized : normalized.substring(0, 280) + "…";
    }

}
