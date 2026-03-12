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
public class StudentTestResultDto {

    private UUID studentId;
    private String studentName;
    private String studentEmail;

    /** Sum of best score for each question in this test */
    private Double totalScore;

    /** Average accuracy across best submissions (0–100) */
    private Double overallAccuracy;

    /** "Passed" if any question was fully passed (accuracy=100), else "Failed" */
    private String status;
}
