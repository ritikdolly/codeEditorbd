package com.exp.codeeditorbd.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubmissionReportDto {
    private UUID submissionId;
    private UUID studentId;
    private String studentName;
    private String studentEmail;
    private String questionTitle;
    private Double score;
    private Double accuracy;
    private LocalDateTime submissionTime;
    private String status; // "Passed" or "Failed"
}
