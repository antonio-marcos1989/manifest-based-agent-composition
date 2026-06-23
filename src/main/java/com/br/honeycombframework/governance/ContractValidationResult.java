package com.br.honeycombframework.governance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractValidationResult {

    private boolean compliant;
    @Builder.Default
    private List<String> violations = new ArrayList<>();

    public static ContractValidationResult ok() {
        return ContractValidationResult.builder().compliant(true).build();
    }

    public static ContractValidationResult fail(String violation) {
        return ContractValidationResult.builder()
                .compliant(false)
                .violations(List.of(violation))
                .build();
    }

    public static ContractValidationResult merge(ContractValidationResult a, ContractValidationResult b) {
        List<String> violations = new ArrayList<>();
        if (a.getViolations() != null) {
            violations.addAll(a.getViolations());
        }
        if (b.getViolations() != null) {
            violations.addAll(b.getViolations());
        }
        return ContractValidationResult.builder()
                .compliant(a.isCompliant() && b.isCompliant())
                .violations(violations)
                .build();
    }
}
