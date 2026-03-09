package com.exp.codeeditorbd.service;

import com.exp.codeeditorbd.dto.QuestionRequestDto;
import com.exp.codeeditorbd.dto.SubmissionReportDto;
import com.exp.codeeditorbd.dto.TestEntityRequestDto;
import com.exp.codeeditorbd.entity.Question;
import com.exp.codeeditorbd.entity.TestCase;
import com.exp.codeeditorbd.entity.TestEntity;
import com.exp.codeeditorbd.entity.TestQuestion;
import com.exp.codeeditorbd.entity.User;
import com.exp.codeeditorbd.repository.QuestionRepository;
import com.exp.codeeditorbd.repository.TestCaseRepository;
import com.exp.codeeditorbd.repository.TestEntityRepository;
import com.exp.codeeditorbd.repository.TestQuestionRepository;
import com.exp.codeeditorbd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeacherService {

    private final UserRepository userRepository;
    private final QuestionRepository questionRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestEntityRepository testEntityRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final com.exp.codeeditorbd.repository.SubmissionRepository submissionRepository;

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
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SubmissionReportDto> getTestResults(UUID testId) {
        return submissionRepository.findByTestIdWithDetails(testId).stream()
                .map(sub -> SubmissionReportDto.builder()
                        .submissionId(sub.getId())
                        .studentName(sub.getStudent().getName())
                        .studentEmail(sub.getStudent().getEmail())
                        .questionTitle(sub.getQuestion().getTitle())
                        .score(sub.getScore())
                        .accuracy(sub.getAccuracy())
                        .submissionTime(sub.getSubmissionTime())
                        .status((sub.getAccuracy() != null && sub.getAccuracy() == 100.0) ? "Passed" : "Failed")
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }
}
