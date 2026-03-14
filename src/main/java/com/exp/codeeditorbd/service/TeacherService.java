package com.exp.codeeditorbd.service;

import com.exp.codeeditorbd.dto.QuestionRequestDto;
import com.exp.codeeditorbd.dto.StudentTestResultDto;
import com.exp.codeeditorbd.dto.SubmissionReportDto;
import com.exp.codeeditorbd.dto.TestEntityRequestDto;
import com.exp.codeeditorbd.entity.Question;
import com.exp.codeeditorbd.entity.Submission;
import com.exp.codeeditorbd.entity.TestCase;
import com.exp.codeeditorbd.entity.TestEntity;
import com.exp.codeeditorbd.entity.TestQuestion;
import com.exp.codeeditorbd.entity.User;
import com.exp.codeeditorbd.repository.QuestionRepository;
import com.exp.codeeditorbd.repository.SubmissionRepository;
import com.exp.codeeditorbd.repository.TestCaseRepository;
import com.exp.codeeditorbd.repository.TestEntityRepository;
import com.exp.codeeditorbd.repository.TestQuestionRepository;
import com.exp.codeeditorbd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestEntityRepository testEntityRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final SubmissionRepository submissionRepository;

    @Transactional
    public Question createQuestion(UUID teacherId, QuestionRequestDto dto) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        Question question = Question.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .difficulty(dto.getDifficulty())
                .expectedTimeComplexity(dto.getExpectedTimeComplexity())
                .marks(dto.getMarks())
                .inputFormat(dto.getInputFormat())
                .outputFormat(dto.getOutputFormat())
                .constraints(dto.getConstraints())
                .prefixCode(dto.getPrefixCode())
                .suffixCode(dto.getSuffixCode())
                .templateCode(dto.getTemplateCode())
                .teacher(teacher)
                .build();

        Question savedQuestion = questionRepository.save(question);

        if (dto.getTestCases() != null) {
            List<TestCase> testCases = dto.getTestCases().stream().map(tc ->
                    TestCase.builder()
                            .input(tc.getInput())
                            .expectedOutput(tc.getExpectedOutput())
                            .isHidden(tc.isHidden())
                            .question(savedQuestion)
                            .build()
            ).toList();
            testCaseRepository.saveAll(testCases);
        }

        return savedQuestion;
    }

    @Transactional(readOnly = true)
    public List<Question> getTeacherQuestions(UUID teacherId) {
        return questionRepository.findByTeacherId(teacherId);
    }

    @Transactional
    public TestEntity createTest(UUID teacherId, TestEntityRequestDto dto) {
        User teacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new RuntimeException("Teacher not found"));

        TestEntity test = TestEntity.builder()
                .name(dto.getName())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .duration(dto.getDuration())
                .status("SCHEDULED")
                .teacher(teacher)
                .build();

        TestEntity savedTest = testEntityRepository.save(test);

        if (dto.getQuestionIds() != null && !dto.getQuestionIds().isEmpty()) {
            List<TestQuestion> testQuestions = dto.getQuestionIds().stream().map(qId -> {
                Question q = questionRepository.findById(qId)
                        .orElseThrow(() -> new RuntimeException("Question " + qId + " not found"));
                return TestQuestion.builder()
                        .test(savedTest)
                        .question(q)
                        .build();
            }).toList();
            testQuestionRepository.saveAll(testQuestions);
        }

        return savedTest;
    }

    public List<TestEntity> getTeacherTests(UUID teacherId) {
        return testEntityRepository.findByTeacherId(teacherId);
    }

    @Transactional(readOnly = true)
    public List<Question> getTestQuestions(UUID testId) {
        return testQuestionRepository.findByTestId(testId).stream()
                .map(tq -> questionRepository.findByIdWithTestCases(tq.getQuestion().getId())
                        .orElse(tq.getQuestion()))
                .collect(Collectors.toList());
    }

    /**
     * Returns one aggregated row per student showing the sum of their best scores
     * across all questions in the test. Prevents score inflation from re-submissions.
     */
    @Transactional(readOnly = true)
    public List<StudentTestResultDto> getTestResults(UUID testId) {
        // Fetch all best submissions (may still have tie rows when two attempts share
        // the same top score — de-dup via Map below).
        List<Submission> bestSubmissions =
                submissionRepository.findBestSubmissionsPerStudentPerQuestion(testId);

        // De-duplicate: keep exactly one best submission per (studentId, questionId)
        Map<String, Submission> deduped = new LinkedHashMap<>();
        for (Submission s : bestSubmissions) {
            String key = s.getStudent().getId() + ":" + s.getQuestion().getId();
            deduped.putIfAbsent(key, s);
        }

        // Group de-duplicated submissions by student
        Map<UUID, List<Submission>> byStudent = new LinkedHashMap<>();
        for (Submission s : deduped.values()) {
            byStudent.computeIfAbsent(s.getStudent().getId(), k -> new ArrayList<>()).add(s);
        }

        // Aggregate per student
        List<StudentTestResultDto> results = new ArrayList<>();
        for (Map.Entry<UUID, List<Submission>> entry : byStudent.entrySet()) {
            List<Submission> subs = entry.getValue();
            User student = subs.get(0).getStudent();

            double totalScore = subs.stream()
                    .mapToDouble(s -> s.getScore() != null ? s.getScore() : 0.0)
                    .sum();

            double overallAccuracy = subs.stream()
                    .mapToDouble(s -> s.getAccuracy() != null ? s.getAccuracy() : 0.0)
                    .average()
                    .orElse(0.0);

            // "Passed" when at least one question was solved with 100% accuracy
            boolean anyPassed = subs.stream()
                    .anyMatch(s -> s.getAccuracy() != null && s.getAccuracy() == 100.0);

            results.add(StudentTestResultDto.builder()
                    .studentId(student.getId())
                    .studentName(student.getName())
                    .studentEmail(student.getEmail())
                    .totalScore(Math.round(totalScore * 100.0) / 100.0)
                    .overallAccuracy(Math.round(overallAccuracy * 100.0) / 100.0)
                    .status(anyPassed ? "Passed" : "Failed")
                    .build());
        }

        return results;
    }

    /**
     * Returns the best submission per question for a specific student in a test.
     * Used by the expandable row in the teacher dashboard to show per-question breakdown.
     */
    @Transactional(readOnly = true)
    public List<SubmissionReportDto> getStudentTestDetails(UUID testId, UUID studentId) {
        List<Submission> bestSubs =
                submissionRepository.findBestSubmissionsForStudent(testId, studentId);

        // De-duplicate per question (same top-score tie-break)
        Map<UUID, Submission> deduped = new LinkedHashMap<>();
        for (Submission s : bestSubs) {
            deduped.putIfAbsent(s.getQuestion().getId(), s);
        }

        return deduped.values().stream()
                .map(sub -> SubmissionReportDto.builder()
                        .submissionId(sub.getId())
                        .studentId(sub.getStudent().getId())
                        .studentName(sub.getStudent().getName())
                        .studentEmail(sub.getStudent().getEmail())
                        .questionTitle(sub.getQuestion().getTitle())
                        .score(sub.getScore())
                        .accuracy(sub.getAccuracy())
                        .submissionTime(sub.getSubmissionTime())
                        .status((sub.getAccuracy() != null && sub.getAccuracy() == 100.0)
                                ? "Passed" : "Failed")
                        .build())
                .collect(Collectors.toList());
    }
}
