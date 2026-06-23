package com.br.honeycombframework.governance;

import com.br.honeycombframework.exception.ValidationException;
import com.br.honeycombframework.model.AgentManifest;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valida e resolve credenciais de agentes ({@code authApiKey} ou header Authorization legado).
 */
public final class AgentCredentialSupport {

    private static final Pattern ENV_TOKEN = Pattern.compile("^\\$\\{([A-Z0-9_]+)(?::([^}]*))?}$");

    private AgentCredentialSupport() {
    }

    public static void validateForRegister(AgentManifest manifest) {
        if (manifest.getType() != AgentManifest.AgentType.GENERATIVE) {
            return;
        }
        if (!requiresAuth(manifest)) {
            return;
        }

        String apiKey = manifest.getAuthApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            assertResolvableCredential(apiKey.trim(), "authApiKey");
            return;
        }

        String authorization = readAuthorizationHeader(manifest);
        if (authorization != null && !authorization.isBlank()) {
            assertResolvableCredential(stripBearerPrefix(authorization.trim()), "httpHeaders.Authorization");
            return;
        }

        throw new ValidationException(
                "authApiKey é obrigatório para agentes GENERATIVE com endpoint externo (HTTPS/Ollama). "
                        + "Preencha ollamaApiKey no Postman ou use ${NOME_VARIAVEL} no servidor.");
    }

    public static String resolveAuthorizationHeader(AgentManifest agent) {
        if (agent.getAuthApiKey() != null && !agent.getAuthApiKey().isBlank()) {
            return "Bearer " + resolve(agent.getAuthApiKey().trim());
        }
        String authorization = readAuthorizationHeader(agent);
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "Bearer " + resolve(stripBearerPrefix(trimmed));
        }
        return resolve(trimmed);
    }

    public static String resolve(String value) {
        if (value == null) {
            return "";
        }
        Matcher matcher = ENV_TOKEN.matcher(value.trim());
        if (!matcher.matches()) {
            return value;
        }
        String envName = matcher.group(1);
        String defaultValue = matcher.group(2);
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue != null ? defaultValue : "";
    }

    private static boolean requiresAuth(AgentManifest manifest) {
        String endpoint = manifest.getEndpointUrl();
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }
        return endpoint.startsWith("https://") || endpoint.contains("ollama.com");
    }

    private static void assertResolvableCredential(String value, String field) {
        if (value.contains("{{") || value.contains("}}")) {
            throw new ValidationException(field + " contém placeholder não resolvido (ex.: {{ollamaApiKey}}). "
                    + "Preencha a variável no Postman antes de registrar o manifest.");
        }
        if (value.isBlank()) {
            throw new ValidationException(field + " não pode estar vazio.");
        }

        Matcher matcher = ENV_TOKEN.matcher(value);
        if (!matcher.matches()) {
            return;
        }

        String envName = matcher.group(1);
        String defaultValue = matcher.group(2);
        String envValue = System.getenv(envName);
        if ((envValue == null || envValue.isBlank()) && (defaultValue == null || defaultValue.isBlank())) {
            throw new ValidationException(field + ": variável de ambiente " + envName + " não definida no servidor.");
        }
    }

    private static String readAuthorizationHeader(AgentManifest manifest) {
        Map<String, String> headers = manifest.getHttpHeaders();
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && "authorization".equalsIgnoreCase(entry.getKey().trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String stripBearerPrefix(String value) {
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }
}
