package com.exp.codeeditorbd.service;

import com.exp.codeeditorbd.dto.CodeRunRequest;
import com.exp.codeeditorbd.dto.CodeRunResponse;
import com.exp.codeeditorbd.entity.Submission;
import com.exp.codeeditorbd.entity.SubmissionResult;
import com.exp.codeeditorbd.entity.TestCase;
import com.exp.codeeditorbd.repository.SubmissionRepository;
import com.exp.codeeditorbd.repository.SubmissionResultRepository;
import com.exp.codeeditorbd.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class CodeExecutionService {

    private final TestCaseRepository testCaseRepository;
    private final SubmissionResultRepository submissionResultRepository;
    private final SubmissionRepository submissionRepository;

    // ===== Used for final SUBMIT: runs ALL test cases (including hidden) =====
    public void evaluateSubmission(Submission submission) {
        List<TestCase> testCases = testCaseRepository.findByQuestionId(submission.getQuestion().getId());

        if (testCases.isEmpty()) {
            submission.setScore(0.0);
            submission.setAccuracy(0.0);
            submissionRepository.save(submission);
            return;
        }

        try {
            Path tempDir = compileCode(submission.getCode());
            if (tempDir == null) {
                saveErrorResults(submission, testCases, "Compilation Error");
                return;
            }

            int passedCount = 0;
            for (TestCase tc : testCases) {
                ExecutionResult result = runAgainstInput(tempDir, tc.getInput());
                boolean passed = result.success && result.output.trim().equals(tc.getExpectedOutput().trim());
                if (passed) passedCount++;

                SubmissionResult sr = SubmissionResult.builder()
                        .submission(submission)
                        .testCase(tc)
                        .passed(passed)
                        .actualOutput(result.output)
                        .errorMessage(result.error)
                        .build();
                submissionResultRepository.save(sr);
            }

            double accuracy = ((double) passedCount / testCases.size()) * 100.0;
            int questionMarks = submission.getQuestion().getMarks() != null ? submission.getQuestion().getMarks() : 0;
            double score = (accuracy / 100.0) * questionMarks;
            submission.setAccuracy(accuracy);
            submission.setScore(score);
            submissionRepository.save(submission);

            cleanupDir(tempDir);
        } catch (Exception e) {
            log.error("Evaluation error for submission {}", submission.getId(), e);
            saveErrorResults(submission, testCases, "Internal Execution Error: " + e.getMessage());
        }
    }

    // ===== Used for RUN button: runs only provided/sample test cases =====
    public CodeRunResponse runCode(CodeRunRequest request) {
        String code = request.getCode();

        try {
            Path tempDir = compileCode(code);
            if (tempDir == null) {
                return CodeRunResponse.builder()
                        .status("COMPILATION_ERROR")
                        .error("Compilation failed. Please check your code for syntax errors.")
                        .build();
            }

            List<CodeRunResponse.TestCaseResult> results = new ArrayList<>();

            // If sample test cases are provided, run against them
            if (request.getSampleTestCases() != null && !request.getSampleTestCases().isEmpty()) {
                for (var tc : request.getSampleTestCases()) {
                    ExecutionResult execResult = runAgainstInput(tempDir, tc.getInput());
                    boolean passed = execResult.success && execResult.output.trim().equals(tc.getExpectedOutput().trim());
                    results.add(CodeRunResponse.TestCaseResult.builder()
                            .input(tc.getInput())
                            .expectedOutput(tc.getExpectedOutput())
                            .actualOutput(execResult.output)
                            .passed(passed)
                            .error(execResult.error)
                            .build());
                }
            } else if (request.getInput() != null) {
                // Simple single-input run
                ExecutionResult execResult = runAgainstInput(tempDir, request.getInput());
                return CodeRunResponse.builder()
                        .status(execResult.success ? "SUCCESS" : "RUNTIME_ERROR")
                        .output(execResult.output)
                        .error(execResult.error)
                        .build();
            }

            cleanupDir(tempDir);

            boolean allPassed = results.stream().allMatch(CodeRunResponse.TestCaseResult::isPassed);
            return CodeRunResponse.builder()
                    .status(allPassed ? "ALL_PASSED" : "SOME_FAILED")
                    .testCaseResults(results)
                    .build();

        } catch (Exception e) {
            log.error("RunCode error", e);
            return CodeRunResponse.builder()
                    .status("ERROR")
                    .error("Internal error: " + e.getMessage())
                    .build();
        }
    }

    // ===== Private helpers =====

    private Path compileCode(String code) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("code-" + UUID.randomUUID());
        File sourceFile = new File(tempDir.toFile(), "Main.java");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sourceFile))) {
            writer.write(code);
        }

        ProcessBuilder compilePb = new ProcessBuilder("javac", "Main.java");
        compilePb.directory(tempDir.toFile());
        compilePb.redirectErrorStream(true);
        Process compileProcess = compilePb.start();
        boolean compiled = compileProcess.waitFor(10, TimeUnit.SECONDS);
        String compileOutput = readStream(compileProcess.getInputStream());

        if (!compiled || compileProcess.exitValue() != 0) {
            log.debug("Compilation failed: {}", compileOutput);
            cleanupDir(tempDir);
            return null;
        }
        return tempDir;
    }

    private ExecutionResult runAgainstInput(Path tempDir, String input) throws IOException, InterruptedException {
        ProcessBuilder execPb = new ProcessBuilder("java", "-Xmx64m", "-Xms16m", "Main");
        execPb.directory(tempDir.toFile());
        Process execProcess = execPb.start();

        // Send input
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(execProcess.getOutputStream()))) {
            if (input != null) {
                writer.write(input);
                writer.newLine();
            }
        }

        boolean finished = execProcess.waitFor(5, TimeUnit.SECONDS);
        if (!finished) {
            execProcess.destroyForcibly();
            return new ExecutionResult(false, "", "Time Limit Exceeded (5s)");
        }

        String output = readStream(execProcess.getInputStream()).trim();
        String error = readStream(execProcess.getErrorStream()).trim();

        if (execProcess.exitValue() != 0 && !error.isEmpty()) {
            return new ExecutionResult(false, output, "Runtime Error:\n" + error);
        }

        return new ExecutionResult(true, output, null);
    }

    private void saveErrorResults(Submission submission, List<TestCase> testCases, String error) {
        for (TestCase tc : testCases) {
            SubmissionResult result = SubmissionResult.builder()
                    .submission(submission)
                    .testCase(tc)
                    .passed(false)
                    .errorMessage(error)
                    .build();
            submissionResultRepository.save(result);
        }
        submission.setAccuracy(0.0);
        submission.setScore(0.0);
        submissionRepository.save(submission);
    }

    private String readStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private void cleanupDir(Path dir) {
        try {
            Files.walk(dir).map(Path::toFile).forEach(File::delete);
            dir.toFile().delete();
        } catch (IOException e) {
            log.warn("Could not cleanup temp dir {}", dir, e);
        }
    }

    private record ExecutionResult(boolean success, String output, String error) {}
}
