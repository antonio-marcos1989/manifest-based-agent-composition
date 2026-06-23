package com.br.honeycombframework.governance;

import com.br.honeycombframework.exception.ValidationException;
import com.br.honeycombframework.model.AgentManifest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentCredentialSupportTest {

    @Test
    void rejectsUnresolvedPostmanPlaceholderInAuthApiKey() {
        AgentManifest manifest = ollamaManifest();
        manifest.setAuthApiKey("{{ollamaApiKey}}");

        ValidationException ex = assertThrows(ValidationException.class,
                () -> AgentCredentialSupport.validateForRegister(manifest));

        assertEquals(true, ex.getMessage().contains("placeholder"));
    }

    @Test
    void rejectsEmptyAuthApiKeyForHttpsEndpoint() {
        AgentManifest manifest = ollamaManifest();
        manifest.setAuthApiKey("   ");

        assertThrows(ValidationException.class, () -> AgentCredentialSupport.validateForRegister(manifest));
    }

    @Test
    void acceptsLiteralAuthApiKey() {
        AgentManifest manifest = ollamaManifest();
        manifest.setAuthApiKey("sk-test-key");

        AgentCredentialSupport.validateForRegister(manifest);

        assertEquals("Bearer sk-test-key", AgentCredentialSupport.resolveAuthorizationHeader(manifest));
    }

    @Test
    void rejectsLegacyAuthorizationPlaceholder() {
        AgentManifest manifest = ollamaManifest();
        manifest.setHttpHeaders(Map.of(
                "Content-Type", "application/json",
                "Authorization", "Bearer {{ollamaApiKey}}"));

        assertThrows(ValidationException.class, () -> AgentCredentialSupport.validateForRegister(manifest));
    }

    @Test
    void localHttpEndpointDoesNotRequireAuth() {
        AgentManifest manifest = new AgentManifest();
        manifest.setName("local");
        manifest.setRole(AgentManifest.Role.COMPONENT);
        manifest.setType(AgentManifest.AgentType.GENERATIVE);
        manifest.setEndpointUrl("http://localhost:8080/agent");

        AgentCredentialSupport.validateForRegister(manifest);
    }

    private static AgentManifest ollamaManifest() {
        AgentManifest manifest = new AgentManifest();
        manifest.setName("ollama-agent");
        manifest.setRole(AgentManifest.Role.COMPONENT);
        manifest.setType(AgentManifest.AgentType.GENERATIVE);
        manifest.setEndpointUrl("https://ollama.com/api/chat");
        return manifest;
    }
}
