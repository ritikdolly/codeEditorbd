package com.exp.codeeditorbd.service;

import com.exp.codeeditorbd.dto.CodeRunRequest;
import com.exp.codeeditorbd.dto.CodeRunResponse;
import com.exp.codeeditorbd.entity.Question;
import com.exp.codeeditorbd.entity.Submission;
import com.exp.codeeditorbd.entity.SubmissionResult;
import com.exp.codeeditorbd.entity.TestCase;
import com.exp.codeeditorbd.repository.QuestionRepository;
import com.exp.codeeditorbd.repository.SubmissionRepository;
import com.exp.codeeditorbd.repository.SubmissionResultRepository;
import com.exp.codeeditorbd.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

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
    private final QuestionRepository questionRepository;

    @PostConstruct
    public void initSandbox() {
        log.info("Initializing Docker sandbox...");
        try {
            // Check if image exists
            Process process = new ProcessBuilder("docker", "images", "-q", "java-sandbox").start();
            String output = readStream(process.getInputStream()).trim();
            
            if (output.isEmpty()) {
                log.info("java-sandbox image not found. Building it now...");
                
                // Use absolute path for build context
                String projectRoot = new File("").getAbsolutePath();
                File buildContext = new File(projectRoot, "docker-sandbox");
                
                if (!buildContext.exists()) {
                    log.error("Docker build context NOT FOUND at: {}", buildContext.getAbsolutePath());
                    return;
                }

                log.info("Building Docker image from: {}", buildContext.getAbsolutePath());
                
                ProcessBuilder buildPb = new ProcessBuilder("docker", "build", "-t", "java-sandbox", ".");
                buildPb.directory(buildContext);
                buildPb.redirectErrorStream(true); // Combine stdout and stderr
                
                Process buildProcess = buildPb.start();
                String buildOutput = readStream(buildProcess.getInputStream());
                boolean finished = buildProcess.waitFor(5, TimeUnit.MINUTES);
                
                if (finished && buildProcess.exitValue() == 0) {
                    log.info("java-sandbox image built successfully.");
                } else {
                    log.error("Failed to build java-sandbox image. Exit code: {}. Output:\n{}", 
                        buildProcess.exitValue(), buildOutput);
                }
            } else {
                log.info("java-sandbox image already exists.");
            }
        } catch (Exception e) {
            log.warn("Docker not found or not running. Sandbox features might fail. Error: {}", e.getMessage());
        }
    }

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
            Question question = submission.getQuestion();
            String fullCode = assembleCode(question, submission.getCode());
            
            CompileResult compileResult = compileCode(fullCode);
            if (!compileResult.success()) {
                saveErrorResults(submission, testCases, "Compilation Error:\n" + compileResult.errorOutput());
                return;
            }

            Path tempDir = compileResult.tempDir();

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
            String fullCode = code;
            if (request.getQuestionId() != null) {
                try {
                    UUID qId = UUID.fromString(request.getQuestionId());
                    Question question = questionRepository.findById(qId).orElse(null);
                    if (question != null) {
                        fullCode = assembleCode(question, code);
                    }
                } catch (Exception e) {
                    log.warn("Invalid question ID in CodeRunRequest: {}", request.getQuestionId());
                }
            }

            CompileResult compileResult = compileCode(fullCode);
            if (!compileResult.success()) {
                return CodeRunResponse.builder()
                        .status("COMPILATION_ERROR")
                        .error("Compilation failed:\n" + compileResult.errorOutput())
                        .build();
            }

            Path tempDir = compileResult.tempDir();

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

    private String assembleCode(Question question, String studentCode) {
        StringBuilder fullCode = new StringBuilder();
        if (question.getPrefixCode() != null && !question.getPrefixCode().trim().isEmpty()) {
            fullCode.append(question.getPrefixCode()).append("\n");
        }
        fullCode.append(studentCode);
        if (question.getSuffixCode() != null && !question.getSuffixCode().trim().isEmpty()) {
            fullCode.append("\n").append(question.getSuffixCode());
        }
        return fullCode.toString();
    }

    private record CompileResult(boolean success, Path tempDir, String errorOutput) {}

    private CompileResult compileCode(String code) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("code-" + UUID.randomUUID());
        File sourceFile = new File(tempDir.toFile(), "Main.java");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sourceFile))) {
            writer.write(code);
        }

        ProcessBuilder compilePb = new ProcessBuilder("javac", "--release", "17", "Main.java");
        compilePb.directory(tempDir.toFile());
        compilePb.redirectErrorStream(true);
        Process compileProcess = compilePb.start();
        boolean compiled = compileProcess.waitFor(10, TimeUnit.SECONDS);
        String compileOutput = readStream(compileProcess.getInputStream());

        if (!compiled || compileProcess.exitValue() != 0) {
            log.debug("Compilation failed: {}", compileOutput);
            cleanupDir(tempDir);
            return new CompileResult(false, null, formatCompileOutput(compileOutput));
        }
        return new CompileResult(true, tempDir, null);
    }
    
    private String formatCompileOutput(String rawOutput) {
        if (rawOutput == null || rawOutput.trim().isEmpty()) return "Unknown compilation error.";
        
        // Remove file path from error since we use temporary file names internally, just show 'Main.java:Line'
        String[] lines = rawOutput.split("\\r?\\n");
        StringBuilder cleanError = new StringBuilder();
        
        for (String line : lines) {
            // Javac output typically looks like: "Main.java:7: error: ';' expected"
            // We just strip any directory paths if they happen to appear
            if (line.contains("Main.java:")) {
                String errorPart = line.substring(line.indexOf("Main.java:"));
                cleanError.append(errorPart).append("\n");
            } else {
                cleanError.append(line).append("\n");
            }
        }
        return cleanError.toString().trim();
    }

    private ExecutionResult runAgainstInput(Path tempDir, String input) throws IOException, InterruptedException {
        // Use Docker for execution
        String absolutePath = tempDir.toAbsolutePath().toString();
        
        // docker run --rm -i --memory=64m --cpus=0.5 --network none -v "PATH:/home/sandbox/app" java-sandbox java -cp /home/sandbox/app Main
        // Note: We mount the temp dir to the sandbox user's home or a subfolder.
        // On Windows, absolute paths need careful handling with Docker.
        
        List<String> command = new ArrayList<>(List.of(
            "docker", "run", "--rm", "-i",
            "--memory=128m",
            "--cpus=0.5",
            "--network", "none",
            "-v", absolutePath + ":/home/sandbox/app",
            "java-sandbox",
            "java", "-cp", "/home/sandbox/app", "Main"
        ));

        ProcessBuilder execPb = new ProcessBuilder(command);
        Process execProcess = execPb.start();

        // Send input
        if (input != null && !input.isEmpty()) {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(execProcess.getOutputStream()))) {
                writer.write(input);
                writer.flush();
            }
        } else {
            execProcess.getOutputStream().close();
        }

        boolean finished = execProcess.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            execProcess.destroyForcibly();
            // runCommandAsync("docker ps -l -q | xargs -r docker kill"); // Linux-specific
            return new ExecutionResult(false, "", "Time Limit Exceeded (10s)");
        }

        String output = readStream(execProcess.getInputStream()).trim();
        String error = readStream(execProcess.getErrorStream()).trim();

        if (execProcess.exitValue() != 0) {
            return new ExecutionResult(false, output, error.isEmpty() ? "Execution failed with exit code " + execProcess.exitValue() : "Runtime Error:\n" + error);
        }

        return new ExecutionResult(true, output, null);
    }

    private void runCommandAsync(String cmd) {
        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(cmd);
            } catch (Exception ignored) {}
        }).start();
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
