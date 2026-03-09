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
public class QuestionRequestDto {
    private String title;
    private String description;
    private String difficulty; // EASY, MEDIUM, HARD
    private String expectedTimeComplexity;
    private Integer marks;
    private String inputFormat;
    private String outputFormat;
    private String constraints;
    private List<TestCaseDto> testCases;
}
