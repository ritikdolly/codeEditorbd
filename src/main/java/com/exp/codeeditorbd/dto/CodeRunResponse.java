package com.exp.codeeditorbd.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CodeRunResponse {
    private String status; // COMPILATION_ERROR, RUNTIME_ERROR, SUCCESS
    private String output;
    private String error;
    private List<TestCaseResult> testCaseResults;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TestCaseResult {
        private String input;
        private String expectedOutput;
        private String actualOutput;
        private boolean passed;
        private String error;
    }
}
