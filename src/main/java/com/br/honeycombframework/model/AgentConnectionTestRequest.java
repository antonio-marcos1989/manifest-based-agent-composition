package com.br.honeycombframework.model;

import lombok.Data;

@Data
public class AgentConnectionTestRequest {
    private AgentManifest manifest;
    /** Quando informado, reutiliza authApiKey/httpHeaders do agent já salvo se o payload não trouxer credencial. */
    private String mergeCredentialsFromId;
}
