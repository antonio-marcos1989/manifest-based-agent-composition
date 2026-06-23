package com.br.honeycombframework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "honeycomb.xai")
public class XaiExplanationProperties {

    /** Gera explicação via Observer quando o agente LLM não envia campo explanation. */
    private boolean autoExplainLlm = true;

    public boolean isAutoExplainLlm() {
        return autoExplainLlm;
    }

    public void setAutoExplainLlm(boolean autoExplainLlm) {
        this.autoExplainLlm = autoExplainLlm;
    }
}
