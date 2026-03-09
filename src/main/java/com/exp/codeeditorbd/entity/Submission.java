package com.exp.codeeditorbd.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_id") // Nullable if generic practice
    private TestEntity test;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String code;

    private String language; // e.g. "java"

    private Double score;
    private Double accuracy; // 0 to 100 percentage
    
    @Column(name = "submission_time")
    private LocalDateTime submissionTime;

    @PrePersist
    protected void onCreate() {
        submissionTime = LocalDateTime.now();
    }
}
