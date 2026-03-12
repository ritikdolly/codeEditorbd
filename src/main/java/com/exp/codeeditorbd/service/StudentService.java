package com.exp.codeeditorbd.service;

import com.exp.codeeditorbd.dto.CodeSubmissionDto;
import com.exp.codeeditorbd.dto.StudentTestResultDto;
import com.exp.codeeditorbd.entity.Question;
import com.exp.codeeditorbd.entity.Submission;
import com.exp.codeeditorbd.entity.TestEntity;
import com.exp.codeeditorbd.entity.User;
import com.exp.codeeditorbd.repository.QuestionRepository;
import com.exp.codeeditorbd.repository.SubmissionRepository;
import com.exp.codeeditorbd.repository.TestEntityRepository;
import com.exp.codeeditorbd.repository.TestQuestionRepository;
import com.exp.codeeditorbd.repository.UserRepository;
import com.exp.codeeditorbd.repository.TestAttemptRepository;
import com.exp.codeeditorbd.repository.TestDraftRepository;
import com.exp.codeeditorbd.entity.TestAttempt;
import com.exp.codeeditorbd.entity.TestDraft;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final UserRepository userRepository;
    private final TestEntityRepository testEntityRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final CodeExecutionService codeExecutionService;
    private final TestAttemptRepository testAttemptRepository;
    private final TestDraftRepository testDraftRepository;

    public TestEntity getTestDetails(UUID testId) {
        return testEntityRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));
    }

    @Transactional(readOnly = true)
    public List<Question> getTestQuestions(UUID testId) {
        return testQuestionRepository.findByTestId(testId).stream()
                .map(tq -> questionRepository.findByIdWithTestCases(tq.getQuestion().getId())
                        .orElse(tq.getQuestion()))
                .collect(Collectors.toList());
    }

    @Transactional
    public Submission submitCode(UUID studentId, CodeSubmissionDto dto) {
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found for id: " + studentId));

        Question question = questionRepository.findById(dto.getQuestionId())
                .orElseThrow(() -> new RuntimeException("Question not found for id: " + dto.getQuestionId()));

        TestEntity test = null;
        if (dto.getTestId() != null) {
            test = testEntityRepository.findById(dto.getTestId())
                    .orElseThrow(() -> new RuntimeException("Test not found for id: " + dto.getTestId()));
        }

        Submission submission = Submission.builder()
                .student(student)
                .question(question)
                .test(test)
                .code(dto.getCode())
                .language(dto.getLanguage())
                .build();

        Submission savedSubmission = submissionRepository.save(submission);

        // Execute Code and Evaluation
        codeExecutionService.evaluateSubmission(savedSubmission);

        return savedSubmission;
    }

    @Transactional
    public TestAttempt startAttempt(UUID studentId, UUID testId) {
        TestAttempt attempt = testAttemptRepository.findFirstByTestIdAndStudentIdOrderByStartTimeDesc(testId, studentId)
                .orElse(null);
                
        if (attempt != null) {
            if ("SUBMITTED".equals(attempt.getStatus())) {
                throw new RuntimeException("Test ALREADY submitted and evaluated!");
            }
            return attempt;
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        TestEntity test = testEntityRepository.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found"));

        TestAttempt newAttempt = TestAttempt.builder()
                .student(student)
                .test(test)
                .status("IN_PROGRESS")
                .startTime(LocalDateTime.now())
                .build();
                
        return testAttemptRepository.save(newAttempt);
    }

    @Transactional
    public StudentTestResultDto submitAttempt(UUID studentId, UUID testId) {
        TestAttempt attempt = testAttemptRepository.findFirstByTestIdAndStudentIdOrderByStartTimeDesc(testId, studentId)
                .orElseThrow(() -> new RuntimeException("Attempt not found"));
                
        if (!"SUBMITTED".equals(attempt.getStatus())) {
            attempt.setStatus("SUBMITTED");
            attempt.setSubmitTime(LocalDateTime.now());
            testAttemptRepository.save(attempt);
        }

        // Calculate final scores
        List<Submission> bestSubs = submissionRepository.findBestSubmissionsForStudent(testId, studentId);

        // De-duplicate per question (same top-score tie-break)
        Map<UUID, Submission> deduped = new LinkedHashMap<>();
        for (Submission s : bestSubs) {
            deduped.putIfAbsent(s.getQuestion().getId(), s);
        }

        double totalScore = deduped.values().stream()
                .mapToDouble(s -> s.getScore() != null ? s.getScore() : 0.0)
                .sum();

        double overallAccuracy = deduped.values().stream()
                .mapToDouble(s -> s.getAccuracy() != null ? s.getAccuracy() : 0.0)
                .average()
                .orElse(0.0);

        boolean anyPassed = deduped.values().stream()
                .anyMatch(s -> s.getAccuracy() != null && s.getAccuracy() == 100.0);

        User student = attempt.getStudent();

        return StudentTestResultDto.builder()
                .studentId(student.getId())
                .studentName(student.getName())
                .studentEmail(student.getEmail())
                .totalScore(Math.round(totalScore * 100.0) / 100.0)
                .overallAccuracy(Math.round(overallAccuracy * 100.0) / 100.0)
                .status(anyPassed ? "Passed" : "Failed")
                .build();
    }

    @Transactional
    public void saveDraft(UUID studentId, UUID testId, UUID questionId, String code) {
        TestDraft draft = testDraftRepository.findByTestIdAndQuestionIdAndStudentId(testId, questionId, studentId)
                .orElse(null);

        if (draft != null) {
            draft.setCode(code);
            draft.setUpdatedAt(LocalDateTime.now());
            testDraftRepository.save(draft);
        } else {
            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found"));
            TestEntity test = testEntityRepository.findById(testId)
                    .orElseThrow(() -> new RuntimeException("Test not found"));
            Question question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found"));

            TestDraft newDraft = TestDraft.builder()
                    .student(student)
                    .test(test)
                    .question(question)
                    .code(code)
                    .updatedAt(LocalDateTime.now())
                    .build();
            testDraftRepository.save(newDraft);
        }
    }

    @Transactional(readOnly = true)
    public String getDraft(UUID studentId, UUID testId, UUID questionId) {
        return testDraftRepository.findByTestIdAndQuestionIdAndStudentId(testId, questionId, studentId)
                .map(TestDraft::getCode)
                .orElse("");
    }
}
