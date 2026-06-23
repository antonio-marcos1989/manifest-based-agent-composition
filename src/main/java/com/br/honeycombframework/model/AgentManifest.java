package com.br.honeycombframework.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Document(collection = "manifests")
public class AgentManifest {

    @PersistenceCreator
    public AgentManifest() {
    }

    @Id
    private String id;

    @NotBlank(message = "name é obrigatório.")
    private String name;

    private String description;

    @NotNull(message = "role é obrigatório.")
    private Role role;

    @NotBlank(message = "endpointUrl é obrigatório.")
    private String endpointUrl;
    private AgentType type;
    private Boolean active = true;
    private List<String> capabilities;
    /** Modo de construção do payload para APIs de agente. */
    private RequestPayloadMode requestPayloadMode = RequestPayloadMode.DIRECT_JSON;
    /** Modelo padrão para agentes chat-based (opcional). */
    private String defaultModel;
    /** Prompt de sistema padrão para agentes chat-based (opcional). */
    private String systemPrompt;
    /** Se true, envia format=json quando estiver em modo CHAT_MESSAGES. */
    private Boolean forceJsonResponse = false;
    /**
     * Token/API key do agente (campo dedicado para validação no registro).
     * Aceita valor literal ou referência {@code ${OLLAMA_API_KEY}} resolvida no dispatch.
     */
    private String authApiKey;

    /** Headers HTTP adicionais por agente (ex.: Content-Type). Authorization legado ainda suportado. */
    private Map<String, String> httpHeaders;

    /** Contrato legado: campo → tipo (string, number, object, ...). */
    private Map<String, String> inputContract;
    private Map<String, String> outputContract;

    /**
     * JSON Schema (draft-07) para validação estrutural do input.
     * Ex.: {"type":"object","required":["prompt"],"properties":{"prompt":{"type":"string"}}}
     */
    private Map<String, Object> inputJsonSchema;

    /**
     * JSON Schema (draft-07) para validação estrutural do output.
     */
    private Map<String, Object> outputJsonSchema;

    private AlaSettings alaSettings;

    /** Última execução registrada (enriquecido na leitura; não persistido). */
    @Transient
    private LocalDateTime lastExecutedAt;

    public enum AgentType {
        GENERATIVE,
        CLASSIFICATION,
        REGRESSION
    }

    public enum Role {
        /** @deprecated use {@link #COMPONENT} */
        HUNTER,
        /** Observability and explainability agent. */
        OBSERVER,
        /** Agent executor component. */
        COMPONENT,
        REFEREE;

        @JsonCreator
        public static Role fromValue(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            if ("XAI".equalsIgnoreCase(value)) {
                return OBSERVER; // legacy alias
            }
            return valueOf(value);
        }

        public String displayName() {
            return switch (this) {
                case HUNTER, COMPONENT -> "Component";
                case OBSERVER -> "Observer";
                case REFEREE -> "Referee";
            };
        }

        public Role normalized() {
            return this == HUNTER ? COMPONENT : this;
        }
    }

    public enum RequestPayloadMode {
        DIRECT_JSON,
        CHAT_MESSAGES
    }
}