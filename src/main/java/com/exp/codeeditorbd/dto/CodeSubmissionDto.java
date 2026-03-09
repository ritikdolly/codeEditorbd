package com.exp.codeeditorbd.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CodeSubmissionDto {
    private UUID questionId;
    private UUID testId; // Optional if practicing
    private String code;
    private String language; // e.g. "java"
}
