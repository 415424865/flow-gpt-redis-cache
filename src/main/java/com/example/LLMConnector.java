package com.example;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.IOException;

public class LLMConnector {

    /**
     * Simulates sending Java code to an LLM for test generation.
     *
     * !!! IMPORTANT: LLM INTEGRATION POINT !!!
     * To integrate a real LLM:
     * 1. Remove or comment out the existing placeholder logic in this method.
     * 2. Add your LLM API client initialization here or pass it in.
     * 3. Construct the prompt for your LLM. This should include:
     *    - The `javaFileContent`.
     *    - The `previousErrors` (if any) to guide the LLM in fixing tests.
     *    - Instructions for the LLM (e.g., "Generate JUnit 5 tests for the following Java class...").
     * 4. Call your LLM API with the constructed prompt.
     * 5. Return the LLM's response (the generated test code as a String).
     *
     * Example (pseudo-code):
     *   // MyLLMClient llmClient = new MyLLMClient("YOUR_API_KEY");
     *   // String prompt = "Generate JUnit 5 tests for this Java code:\n" + javaFileContent;
     *   // if (previousErrors != null && !previousErrors.isEmpty()) {
     *   //   prompt += "\nFix the following errors from the previous attempt:\n" + previousErrors;
     *   // }
     *   // String generatedTests = llmClient.generate(prompt);
     *   // return generatedTests;
     *
     * @param javaFileContent The content of the Java file for which to generate tests.
     * @param previousErrors Optional. Error messages from previous test runs, if any.
     * @return A string containing the generated unit tests.
     */
    public String generateTests(String javaFileContent, String previousErrors) {
        System.out.println("LLMConnector: Received request to generate tests.");
        System.out.println("LLMConnector: Target Java code snippet (first 100 chars): " +
                           (javaFileContent.length() > 100 ? javaFileContent.substring(0, 100) + "..." : javaFileContent));

        // --- START OF PLACEHOLDER LOGIC ---
        // Comment out or remove this section when integrating a real LLM
        if (previousErrors != null && !previousErrors.isEmpty()) {
            System.out.println("LLMConnector: Received previous errors: " + previousErrors);
            // In a real scenario, you would send both javaFileContent and previousErrors to the LLM.
            // For this placeholder, if there are errors, we'll return a slightly modified "fixed" test.
            return getFixedCalculatorTestSource(javaFileContent);
        } else {
            // In a real scenario, you would send javaFileContent to the LLM.
            // For this placeholder, we'll return a basic initial test.
            return getInitialCalculatorTestSource(javaFileContent);
        }
        // --- END OF PLACEHOLDER LOGIC ---
    }

    /**
     * Placeholder for initial test generation.
     * THIS IS A PLACEHOLDER and should be replaced by actual LLM calls in `generateTests`.
     */
    private String getInitialCalculatorTestSource(String javaFileContent) {
        // Determine the package from the javaFileContent
        String packageName = "com.example"; // Default package
        if (javaFileContent.contains("package ")) {
            int packageIndex = javaFileContent.indexOf("package ");
            int semicolonIndex = javaFileContent.indexOf(";", packageIndex);
            if (semicolonIndex > packageIndex) {
                packageName = javaFileContent.substring(packageIndex + 8, semicolonIndex).trim();
            }
        }

        // Extract class name
        String className = "Calculator"; // Default
        if (javaFileContent.contains("public class ")) {
             int classIndex = javaFileContent.indexOf("public class ");
             int braceIndex = javaFileContent.indexOf("{", classIndex);
             if (braceIndex > classIndex) {
                 className = javaFileContent.substring(classIndex + 13, braceIndex).trim();
             }
        }


        // This is a very basic, hardcoded test for Calculator.java.
        // A real LLM would generate this based on the input code.
        return "package " + packageName + ";\n\n" +
               "import org.junit.jupiter.api.Test;\n" +
               "import static org.junit.jupiter.api.Assertions.*;\n\n" +
               "class " + className + "Test {\n" +
               "    @Test\n" +
               "    void testAdd() {\n" +
               "        " + className + " calc = new " + className + "();\n" +
               "        assertEquals(5, calc.add(2, 3));\n" +
               "        // Intentionally introduce an error for the first run\n" +
               "        assertEquals(10, calc.add(5, 4), \"Error in add method\");\n" +
               "    }\n\n" +
               "    @Test\n" +
               "    void testSubtract() {\n" +
               "        " + className + " calc = new " + className + "();\n" +
               "        assertEquals(1, calc.subtract(3, 2));\n" +
               "    }\n" +
               "}";
    }

    /**
     * Placeholder for test generation when previous errors are provided.
     * THIS IS A PLACEHOLDER and should be replaced by actual LLM calls in `generateTests`.
     */
    private String getFixedCalculatorTestSource(String javaFileContent) {
         // Determine the package from the javaFileContent
        String packageName = "com.example"; // Default package
        if (javaFileContent.contains("package ")) {
            int packageIndex = javaFileContent.indexOf("package ");
            int semicolonIndex = javaFileContent.indexOf(";", packageIndex);
            if (semicolonIndex > packageIndex) {
                packageName = javaFileContent.substring(packageIndex + 8, semicolonIndex).trim();
            }
        }

        // Extract class name
        String className = "Calculator"; // Default
        if (javaFileContent.contains("public class ")) {
             int classIndex = javaFileContent.indexOf("public class ");
             int braceIndex = javaFileContent.indexOf("{", classIndex);
             if (braceIndex > classIndex) {
                 className = javaFileContent.substring(classIndex + 13, braceIndex).trim();
             }
        }

        // This is a "fixed" version of the test.
        return "package " + packageName + ";\n\n" +
               "import org.junit.jupiter.api.Test;\n" +
               "import static org.junit.jupiter.api.Assertions.*;\n\n" +
               "class " + className + "Test {\n" +
               "    @Test\n" +
               "    void testAdd() {\n" +
               "        " + className + " calc = new " + className + "();\n" +
               "        assertEquals(5, calc.add(2, 3));\n" +
               "        assertEquals(9, calc.add(5, 4)); // Corrected assertion\n" +
               "    }\n\n" +
               "    @Test\n" +
               "    void testSubtract() {\n" +
               "        " + className + " calc = new " + className + "();\n" +
               "        assertEquals(1, calc.subtract(3, 2));\n" +
               "    }\n\n" +
               "    @Test\n" +
               "    void testMultiply() {\n" +
               "        " + className + " calc = new " + className + "();\n" +
               "        assertEquals(6, calc.multiply(2, 3));\n" +
               "    }\n\n" +
               "    @Test\n" +
               "    void testDivide() {\n" +
               "        " + className + " calc = new " + className + "();\n" +
               "        assertEquals(2, calc.divide(4, 2));\n" +
               "    }\n\n" +
               "    @Test\n" +
               "    void testDivideByZero() {\n" +
               "        " + className + " calc = new " + className + "();\n" +
               "        assertThrows(IllegalArgumentException.class, () -> calc.divide(1, 0));\n" +
               "    }\n" +
               "}";
    }
}
