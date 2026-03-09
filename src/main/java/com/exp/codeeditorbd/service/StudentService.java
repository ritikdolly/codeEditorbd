package com.exp.codeeditorbd.service;

import com.exp.codeeditorbd.dto.CodeSubmissionDto;
import com.exp.codeeditorbd.entity.Question;
import com.exp.codeeditorbd.entity.Submission;
import com.exp.codeeditorbd.entity.TestEntity;
import com.exp.codeeditorbd.entity.User;
import com.exp.codeeditorbd.repository.QuestionRepository;
import com.exp.codeeditorbd.repository.SubmissionRepository;
import com.exp.codeeditorbd.repository.TestEntityRepository;
import com.exp.codeeditorbd.repository.TestQuestionRepository;
import com.exp.codeeditorbd.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final UserRepository userRepository;
    private final TestEntityRepository testEntityRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final QuestionRepository questionRepository;
    private final SubmissionRepository submissionRepository;
    private final CodeExecutionService codeExecutionService;

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
                .orElseThrow(() -> new RuntimeException("Student not found"));
        
        Question question = questionRepository.findById(dto.getQuestionId())
                .orElseThrow(() -> new RuntimeException("Question not found"));
        
        TestEntity test = null;
        if (dto.getTestId() != null) {
            test = testEntityRepository.findById(dto.getTestId())
                    .orElseThrow(() -> new RuntimeException("Test not found"));
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
}
