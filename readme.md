# LLM Auto Test Generator (Java)

This project provides a framework for automatically generating Java unit tests using a Large Language Model (LLM).
It iteratively prompts an LLM to generate tests for a given Java class, runs the tests, and if they fail,
feeds the error messages back to the LLM for refinement.

## Project Structure

- `src/main/java/com/example/`: Contains the core Java code.
  - `Calculator.java`: A sample class for which tests are generated.
  - `LLMConnector.java`: A placeholder class responsible for interacting with the LLM. **This is where you'll integrate your LLM.**
  - `TestGenerator.java`: The main class that orchestrates the test generation and execution loop.
- `src/test/java/com/example/`: Where the generated tests will be written.
- `pom.xml`: Maven project file, configured for Java 8 and JUnit 5.

## How to Run

1.  **Prerequisites**:
    - Java Development Kit (JDK) 8 or higher installed.
    - Apache Maven installed.
2.  **Clone the repository** (or ensure you have the files).
3.  **Integrate Your LLM**:
    - Open `src/main/java/com/example/LLMConnector.java`.
    - Follow the instructions in the comments within the `generateTests` method to connect to your chosen LLM API. You will need to replace the placeholder logic with actual API calls.
4.  **Compile the project**:
    ```bash
    mvn compile
    ```
5.  **Run the TestGenerator**:
    ```bash
    mvn exec:java -Dexec.mainClass="com.example.TestGenerator"
    ```
    The `TestGenerator` is currently hardcoded to process `src/main/java/com/example/Calculator.java`. You can modify the `targetJavaFile` variable in `TestGenerator.java` to point to other classes.

## LLM Integration Details (`LLMConnector.java`)

The `LLMConnector.java` file contains a `generateTests` method. This method is called by `TestGenerator` to get the unit test code.

To integrate your LLM:
- Modify the `generateTests` method in `LLMConnector.java`.
- You'll need to:
  1. Initialize your LLM client (e.g., using an API key).
  2. Construct a prompt for the LLM. This prompt should include:
     - The full content of the Java class for which you want to generate tests (`javaFileContent`).
     - Any error messages from previous test runs (`previousErrors`). This helps the LLM correct its mistakes.
     - Clear instructions to the LLM, specifying that you need JUnit 5 tests for the provided class.
  3. Make the API call to your LLM.
  4. Return the raw Java test code as a String.

The existing placeholder logic in `generateTests` (which uses `getInitialCalculatorTestSource` and `getFixedCalculatorTestSource`) should be removed or commented out once you add your actual LLM calls.

## How it Works

1.  `TestGenerator` reads the target Java class content.
2.  It calls `LLMConnector.generateTests()` to get the initial set of tests.
    - (Placeholder) `LLMConnector` returns a hardcoded basic test for `Calculator.java` which includes an intentional error.
3.  `TestGenerator` writes these tests to a `*Test.java` file in `src/test/java/com/example/`.
4.  It runs `mvn clean test` to compile and execute the tests.
5.  If the tests fail:
    - `TestGenerator` captures the error output from Maven.
    - It calls `LLMConnector.generateTests()` again, this time providing the `javaFileContent` AND the `previousErrors`.
    - (Placeholder) `LLMConnector` sees the `previousErrors` and returns a "fixed" version of the tests.
    - The process repeats from step 3.
6.  If tests pass, or the maximum number of retries is reached, the process stops.

This iterative refinement loop allows the LLM to (theoretically) correct its own mistakes and produce working unit tests.
