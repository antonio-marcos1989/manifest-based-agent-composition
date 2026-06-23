package com.br.honeycombframework.governance;

import com.br.honeycombframework.model.AgentManifest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPersonaSupportTest {

    @Test
    void resolveSystemInstructionFromDescriptionAndCapabilities() {
        AgentManifest agent = new AgentManifest();
        agent.setDescription("Analisa code smells em Java.");
        agent.setCapabilities(List.of("code-smell-detection", "analysis"));

        String instruction = AgentPersonaSupport.resolveSystemInstruction(agent);

        assertTrue(instruction.contains("Analisa code smells"));
        assertTrue(instruction.contains("code-smell-detection"));
    }
}
