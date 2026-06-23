package com.br.honeycombframework.governance;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Remove campos de controle de experimento antes de validação de contrato e envio HTTP.
 */
public final class DispatchInputSupport {

    private static final Set<String> CONTROL_KEYS = Set.of(
            "_mockDelayMs",
            "_mockConfidence",
            "_mockCost",
            "_mockTokens");

    private DispatchInputSupport() {
    }

    public static Map<String, Object> sanitizeForContractAndDispatch(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> clean = new LinkedHashMap<>(input);
        CONTROL_KEYS.forEach(clean::remove);
        return clean;
    }
}
