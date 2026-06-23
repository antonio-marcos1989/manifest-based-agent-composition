package com.br.honeycombframework.service;

import com.br.honeycombframework.model.AgentManifest;
import com.br.honeycombframework.repository.AgentExecutionLogQuery;
import com.br.honeycombframework.repository.AgentManifestRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentManifestServiceDefaultsTest {

    @Mock
    private AgentManifestRepository repository;

    @Mock
    private AgentExecutionLogQuery executionLogQuery;

    @InjectMocks
    private AgentManifestService service;

    @Test
    void registerAppliesRefereeAlaDefaults() {
        when(repository.save(any(AgentManifest.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentManifest manifest = new AgentManifest();
        manifest.setName("ref-test");
        manifest.setRole(AgentManifest.Role.REFEREE);
        manifest.setEndpointUrl("http://localhost");

        AgentManifest saved = service.register(manifest);

        assertNotNull(saved.getAlaSettings());
        assertEquals(20.0, saved.getAlaSettings().getMaxErrorPercentage());
        assertEquals(45_000, saved.getAlaSettings().getMaxLatencyMs());
        assertEquals(AgentManifest.AgentType.GENERATIVE, saved.getType());
    }

    @Test
    void registerForcesGenerativeTypeForObserverAndReferee() {
        when(repository.save(any(AgentManifest.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentManifest observer = new AgentManifest();
        observer.setName("observer-test");
        observer.setRole(AgentManifest.Role.OBSERVER);
        observer.setType(AgentManifest.AgentType.CLASSIFICATION);
        observer.setEndpointUrl("http://localhost");

        assertEquals(AgentManifest.AgentType.GENERATIVE, service.register(observer).getType());
    }

    @Test
    void registerForcesDirectJsonForClassification() {
        when(repository.save(any(AgentManifest.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentManifest manifest = new AgentManifest();
        manifest.setName("classifier");
        manifest.setRole(AgentManifest.Role.COMPONENT);
        manifest.setType(AgentManifest.AgentType.CLASSIFICATION);
        manifest.setRequestPayloadMode(AgentManifest.RequestPayloadMode.CHAT_MESSAGES);
        manifest.setEndpointUrl("http://localhost");

        AgentManifest saved = service.register(manifest);

        assertEquals(AgentManifest.RequestPayloadMode.DIRECT_JSON, saved.getRequestPayloadMode());
    }
}
