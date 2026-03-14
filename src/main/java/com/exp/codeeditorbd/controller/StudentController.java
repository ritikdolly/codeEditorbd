package com.exp.codeeditorbd.controller;

import com.exp.codeeditorbd.dto.CodeRunRequest;
import com.exp.codeeditorbd.dto.CodeRunResponse;
import com.exp.codeeditorbd.dto.CodeSubmissionDto;
import com.exp.codeeditorbd.entity.Question;
import com.exp.codeeditorbd.entity.Submission;
import com.exp.codeeditorbd.entity.TestEntity;
import com.exp.codeeditorbd.security.CustomUserDetails;
import com.exp.codeeditorbd.service.CodeExecutionService;
import com.exp.codeeditorbd.service.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final CodeExecutionService codeExecutionService;

    @GetMapping("/tests/{testId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<TestEntity> getTestDetails(@PathVariable UUID testId) {
        return ResponseEntity.ok(studentService.getTestDetails(testId));
    }

    @GetMapping("/tests/{testId}/questions")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Question>> getTestQuestions(@PathVariable UUID testId) {
        return ResponseEntity.ok(studentService.getTestQuestions(testId));
    }

    @PostMapping("/code/run")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<CodeRunResponse> runCode(@RequestBody CodeRunRequest request) {
        return ResponseEntity.ok(codeExecutionService.runCode(request));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Submission> submitCode(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CodeSubmissionDto dto) {
        return ResponseEntity.ok(studentService.submitCode(userDetails.getUser().getId(), dto));
    }

    @PostMapping("/attempts/{testId}/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> startAttempt(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID testId) {
        try {
            return ResponseEntity.ok(studentService.startAttempt(userDetails.getUser().getId(), testId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/attempts/{testId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> submitAttempt(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID testId) {
        try {
            return ResponseEntity.ok(studentService.submitAttempt(userDetails.getUser().getId(), testId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(400).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/attempts/{testId}/questions/{questionId}/draft")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> saveDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID testId,
            @PathVariable UUID questionId,
            @RequestBody Map<String, String> body) {
        studentService.saveDraft(userDetails.getUser().getId(), testId, questionId, body.get("code"));
        return ResponseEntity.ok().build();
    }

    @GetMapping("/attempts/{testId}/questions/{questionId}/draft")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, String>> getDraft(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID testId,
            @PathVariable UUID questionId) {
        String code = studentService.getDraft(userDetails.getUser().getId(), testId, questionId);
        return ResponseEntity.ok(Map.of("code", code));
    }
}
