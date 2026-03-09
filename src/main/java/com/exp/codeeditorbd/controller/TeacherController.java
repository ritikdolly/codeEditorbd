package com.exp.codeeditorbd.controller;

import com.exp.codeeditorbd.dto.QuestionRequestDto;
import com.exp.codeeditorbd.dto.SubmissionReportDto;
import com.exp.codeeditorbd.dto.TestEntityRequestDto;
import com.exp.codeeditorbd.entity.Question;
import com.exp.codeeditorbd.entity.TestEntity;
import com.exp.codeeditorbd.security.CustomUserDetails;
import com.exp.codeeditorbd.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherController {

    private final TeacherService teacherService;

    @PostMapping("/questions")
    public ResponseEntity<Question> createQuestion(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody QuestionRequestDto dto) {
        return ResponseEntity.ok(teacherService.createQuestion(userDetails.getUser().getId(), dto));
    }

    @GetMapping("/questions")
    public ResponseEntity<List<Question>> getQuestions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(teacherService.getTeacherQuestions(userDetails.getUser().getId()));
    }

    @PostMapping("/tests")
    public ResponseEntity<TestEntity> createTest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody TestEntityRequestDto dto) {
        return ResponseEntity.ok(teacherService.createTest(userDetails.getUser().getId(), dto));
    }

    @GetMapping("/tests")
    public ResponseEntity<List<TestEntity>> getTests(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(teacherService.getTeacherTests(userDetails.getUser().getId()));
    }

    @GetMapping("/tests/{testId}/questions")
    public ResponseEntity<List<Question>> getTestQuestions(@PathVariable UUID testId) {
        return ResponseEntity.ok(teacherService.getTestQuestions(testId));
    }

    @GetMapping("/tests/{testId}/results")
    public ResponseEntity<List<SubmissionReportDto>> getTestResults(@PathVariable UUID testId) {
        return ResponseEntity.ok(teacherService.getTestResults(testId));
    }
}
