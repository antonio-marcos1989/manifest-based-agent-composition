package com.br.honeycombframework.controller;

import com.br.honeycombframework.model.ExecutionResponse;
import com.br.honeycombframework.service.AgentDispatcherService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/execute")
public class AgentExecutionController {

    private final AgentDispatcherService dispatcherService;

    public AgentExecutionController(AgentDispatcherService dispatcherService) {
        this.dispatcherService = dispatcherService;
    }

    @PostMapping("/{agentId}")
    public ExecutionResponse runAgent(@PathVariable String agentId, @RequestBody Map<String, Object> payload) {
        return dispatcherService.dispatch(agentId, payload);
    }
}
