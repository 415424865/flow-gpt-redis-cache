package com.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TestGenerator {

    private static final int MAX_RETRIES = 3; // Maximum number of attempts to fix tests

    public static void main(String[] args) {
        // Default target file, can be changed or made dynamic
        String targetJavaFile = "src/main/java/com/example/Calculator.java";
        // Default test output directory, can be changed or made dynamic
        String testOutputDirectory = "src/test/java/com/example/";

        System.out.println("Starting test generation process for: " + targetJavaFile);

        try {
            Path targetFilePath = Paths.get(targetJavaFile);
            if (!Files.exists(targetFilePath)) {
                System.err.println("Target Java file not found: " + targetJavaFile);
                return;
            }
            String javaFileContent = new String(Files.readAllBytes(targetFilePath), StandardCharsets.UTF_8);

            LLMConnector llmConnector = new LLMConnector();
            String previousErrors = "";
            boolean testsPassed = false;

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                System.out.println("\nAttempt #" + attempt + " to generate and run tests.");

                // 1. Generate tests using LLMConnector
                System.out.println("Generating tests...");
                String generatedTests = llmConnector.generateTests(javaFileContent, previousErrors);

                // Determine test file name based on target class name
                String className = targetFilePath.getFileName().toString().replace(".java", "");
                String testFileName = className + "Test.java";
                Path testFilePath = Paths.get(testOutputDirectory, testFileName);

                // 2. Write tests to file
                System.out.println("Writing generated tests to: " + testFilePath);
                Files.createDirectories(testFilePath.getParent()); // Ensure directory exists
                Files.write(testFilePath, generatedTests.getBytes(StandardCharsets.UTF_8),
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                // 3. Compile and run tests using Maven
                System.out.println("Compiling and running tests via Maven...");
                ProcessBuilder processBuilder = new ProcessBuilder();
                String mvnCommand = System.getProperty("os.name").toLowerCase().startsWith("windows") ? "mvn.cmd" : "mvn";
                processBuilder.command(mvnCommand, "clean", "test");

                // Set the working directory for the Maven command
                processBuilder.directory(new File(".")); // Run from project root

                Process process = processBuilder.start();

                StringBuilder output = new StringBuilder();
                StringBuilder errorOutput = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    System.out.println("MVN_OUT: " + line);
                }
                while ((line = errorReader.readLine()) != null) {
                    errorOutput.append(line).append("\n");
                    System.err.println("MVN_ERR: " + line);
                }

                int exitCode = process.waitFor();

                if (exitCode == 0 && output.toString().contains("BUILD SUCCESS")) {
                    System.out.println("Tests passed successfully!");
                    testsPassed = true;
                    break;
                } else {
                    System.err.println("Tests failed. Exit code: " + exitCode);
                    previousErrors = "Maven Surefire Report Errors (and other console output):\n" +
                                     extractRelevantErrors(output.toString() + "\n" + errorOutput.toString());
                    System.err.println("Captured errors for next LLM attempt:\n" + previousErrors);
                    if (attempt == MAX_RETRIES) {
                        System.err.println("Max retries reached. Test generation failed.");
                    }
                }
            }

            if (testsPassed) {
                System.out.println("\nTest generation process completed successfully.");
            } else {
                System.err.println("\nTest generation process failed after " + MAX_RETRIES + " attempts.");
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("An error occurred during the test generation process:");
            e.printStackTrace();
        }
    }

    /**
     * Extracts relevant error messages from Maven output.
     * This is a simple heuristic and might need improvement.
     */
    private static String extractRelevantErrors(String fullOutput) {
        List<String> errorLines = new ArrayList<>();
        boolean inErrorSection = false;
        final String resultsHeader = "[INFO] Results:";
        final String testsRunHeader = "[ERROR] Tests run:"; // Surefire often prints errors after this
        final String failedTestsIndicator = ">>> FAILURE!"; // JUnit 5 specific
        final String errorIndicator = "[ERROR]";

        for (String line : fullOutput.split("\n")) {
            if (line.contains(resultsHeader)) {
                inErrorSection = true; // Start capturing after "[INFO] Results:"
            }
            if (inErrorSection || line.contains(failedTestsIndicator) || (line.startsWith(errorIndicator) && !line.contains("Failed to execute goal"))) {
                // Heuristic: Capture lines that seem like test failures or compiler errors
                if(line.matches(".*\s->\s.*") || // Likely an assertion error
                   line.contains("Compilation failure") ||
                   line.contains("cannot find symbol") ||
                   line.contains("error:") ||
                   line.contains(failedTestsIndicator) ||
                   (line.startsWith(errorIndicator) && line.contains("Tests run:")))
                {
                     errorLines.add(line);
                }
            }
        }
         if (errorLines.isEmpty()){
             // If no specific errors found by heuristics, return a snippet of the end of the output
             int lastLines = Math.min(20, fullOutput.split("\n").length);
             String[] lines = fullOutput.split("\n");
             StringBuilder sb = new StringBuilder();
             for(int i = Math.max(0, lines.length - lastLines); i < lines.length; i++){
                 sb.append(lines[i]).append("\n");
             }
             return "Could not isolate specific errors. Last ~20 lines of output:\n" + sb.toString();
         }
        return errorLines.stream().collect(Collectors.joining("\n"));
    }
}
