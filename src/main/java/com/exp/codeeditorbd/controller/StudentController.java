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

@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final StudentService studentService;
    private final CodeExecutionService codeExecutionService;

    @GetMapping("/tests/{testId}")
    public ResponseEntity<TestEntity> getTestDetails(@PathVariable UUID testId) {
        return ResponseEntity.ok(studentService.getTestDetails(testId));
    }

    @GetMapping("/tests/{testId}/questions")
    public ResponseEntity<List<Question>> getTestQuestions(@PathVariable UUID testId) {
        return ResponseEntity.ok(studentService.getTestQuestions(testId));
    }

    @PostMapping("/code/run")
    public ResponseEntity<CodeRunResponse> runCode(@RequestBody CodeRunRequest request) {
        return ResponseEntity.ok(codeExecutionService.runCode(request));
    }

    @PostMapping("/submit")
    public ResponseEntity<Submission> submitCode(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody CodeSubmissionDto dto) {
        return ResponseEntity.ok(studentService.submitCode(userDetails.getUser().getId(), dto));
    }
}
