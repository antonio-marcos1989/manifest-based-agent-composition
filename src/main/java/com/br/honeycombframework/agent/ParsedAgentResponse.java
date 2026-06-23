package com.br.honeycombframework.agent;

import com.br.honeycombframework.model.AgentInvocationMetrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedAgentResponse {

    private Object data;
    private String explanation;
    @Builder.Default
    private AgentInvocationMetrics metrics = new AgentInvocationMetrics();
}
