package com.br.honeycombframework.service;

import com.br.honeycombframework.model.ExecutionStreamEvent;
import com.br.honeycombframework.model.ExecutionStreamEvent.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class ExecutionNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionNotificationService.class);
    private static final long SSE_TIMEOUT_MS = 30L * 60 * 1000;

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribeToRun(String goalId, String runId) {
        SseEmitter emitter = createEmitter();
        register(keyRun(runId), emitter);
        register(keyGoal(goalId), emitter);
        sendSafe(emitter, ExecutionStreamEvent.builder()
                .type(EventType.STREAM_CONNECTED)
                .goalId(goalId)
                .runId(runId)
                .message("Conectado ao stream de eventos da execução.")
                .timestamp(java.time.LocalDateTime.now())
                .build());
        return emitter;
    }

    public SseEmitter subscribeToGoal(String goalId) {
        SseEmitter emitter = createEmitter();
        register(keyGoal(goalId), emitter);
        sendSafe(emitter, ExecutionStreamEvent.builder()
                .type(EventType.STREAM_CONNECTED)
                .goalId(goalId)
                .message("Conectado ao stream de eventos do goal.")
                .timestamp(java.time.LocalDateTime.now())
                .build());
        return emitter;
    }

    public void publish(ExecutionStreamEvent event) {
        if (event.getRunId() != null) {
            broadcast(keyRun(event.getRunId()), event);
        }
        if (event.getGoalId() != null) {
            broadcast(keyGoal(event.getGoalId()), event);
        }
    }

    private SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitter.onCompletion(() -> log.debug("SSE completed"));
        emitter.onTimeout(() -> log.debug("SSE timeout"));
        emitter.onError(e -> log.debug("SSE error: {}", e.getMessage()));
        return emitter;
    }

    private void register(String key, SseEmitter emitter) {
        subscribers.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(e -> remove(key, emitter));
    }

    private void broadcast(String key, ExecutionStreamEvent event) {
        List<SseEmitter> list = subscribers.get(key);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            sendSafe(emitter, event);
        }
    }

    private void sendSafe(SseEmitter emitter, ExecutionStreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name("notification")
                    .data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.trace("SSE send failed, removing emitter: {}", e.getMessage());
            subscribers.values().forEach(list -> list.remove(emitter));
        }
    }

    private void remove(String key, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = subscribers.get(key);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                subscribers.remove(key);
            }
        }
    }

    private static String keyRun(String runId) {
        return "run:" + runId;
    }

    private static String keyGoal(String goalId) {
        return "goal:" + goalId;
    }
}
