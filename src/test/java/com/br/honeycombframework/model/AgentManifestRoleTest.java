package com.br.honeycombframework.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentManifestRoleTest {

    @Test
    void fromValue_mapsLegacyXaiToObserver() {
        assertEquals(AgentManifest.Role.OBSERVER, AgentManifest.Role.fromValue("XAI"));
        assertEquals(AgentManifest.Role.OBSERVER, AgentManifest.Role.fromValue("xai"));
        assertEquals(AgentManifest.Role.OBSERVER, AgentManifest.Role.fromValue("OBSERVER"));
    }

    @Test
    void displayName_returnsObserverForObserverRole() {
        assertEquals("Observer", AgentManifest.Role.OBSERVER.displayName());
    }
}
