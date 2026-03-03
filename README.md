# WebQA Test Automation Framework

A test automation framework built with Kotlin features UI and API testing capabilities.

The framework uses TestNG for test execution, Selenium WebDriver for UI testing, and REST Assured for API testing.

## 🛠 Tech Stack

- **Language**: Kotlin
- **Build Tool**: Gradle
- **Testing Framework**: TestNG
- **UI Testing**:
    - Selenium WebDriver
    - Selenium Grid
- **API Testing & Mocking**:
    - REST Assured
    - WireMock 
        - Docker container
        - Response templating
        - Stateful scenarios
- **Assertions**: AssertJ
- **Reporting**: Allure
- **Configuration**:
    - Typesafe Config
    - Docker Compose
    - Environment variables
- **Data Generation**: DataFaker
- **Logging**: Logback + SLF4J
- **API Client Generation**: OpenAPI Generator
- **Code Security Analysis**: Detekt

## 🗂 Project Structure

```
src
├── main/kotlin/com/webqa
│   └── core
│       ├── api               # API clients and models
│       ├── config            # Configuration classes
│       ├── driver            # WebDriver factory and configuration
│       ├── ui                # Page objects and UI components
│       └── utils             # Utility classes
└── test/kotlin/com/webqa
    └── tests
        ├── BaseApiTest.kt    # Base class for API tests
        ├── BaseTest.kt       # Base class for all tests
        ├── api               # API test implementations
        │   └── wiremock      # WireMock test utilities
        │       ├── HttpStatusExtensions.kt    # HTTP status code constants
        │       ├── WireMockResponseBuilder.kt # Fluent response builder
        │       ├── WireMockResponses.kt       # Response templates
        │       ├── WireMockSupport.kt         # WireMock setup/teardown
        │       └── SignUpWireMockTest.kt      # WireMock test examples
        └── ui                # UI test implementations

Configuration Files:
├─ src/main/resources
│   ├─ allure.properties     # Allure reporting configuration
│   ├─ application.conf      # Application configuration
│   └─ petstore-openapi.json # OpenAPI specification
├─ src/test/resources
│   ├─ api-tests.xml         # API tests configuration
│   ├─ logback-test.xml      # Test logging configuration
│   ├─ regression.xml        # Full regression suite
│   ├─ smoke.xml             # Smoke test suite
│   ├─ testNg.xml            # Main TestNG configuration
│   └─ ui-tests.xml          # UI tests configuration (parallel)
├─ .github/workflows
│   └─ smoke.yml             # GitHub Actions workflow for CI/CD
├─ .env.example              # Template for environment variables (credentials, config overrides)
└─ docker-compose.yml        # FF, Chrome browser and Wiremock services
```

## 🚀 Running Tests

### Prerequisites

1. Java 17
2. Gradle installed (or use the included Gradle wrapper)
3. Docker installed (for remote execution)
4. Browsers installed (for local execution)
5. Copy `.env.example` to `.env` and fill in test credentials:
   ```bash
   cp .env.example .env
   # Edit .env with your TEST_USER_EMAIL and TEST_PASSWORD
   ```

### Continuous Integration with GitHub Actions

The project includes GitHub Actions workflows for automated testing:

#### Smoke Tests Workflow

Located in `.github/workflows/smoke.yml`, this workflow:

- Runs on push to main, master, and dev* branches
- Can be triggered manually via GitHub UI
- Executes three jobs:
  1. **Lint Check**: Runs Detekt security analysis (non-blocking)
  2. **API Tests**: Runs SignUpAPITest
  3. **UI Tests**: Runs LoginTest and SignUpUITest using Selenium Grid with Chrome
- Test credentials are injected via GitHub Secrets (`TEST_USER_EMAIL`, `TEST_PASSWORD`)
- Uses caching for Gradle dependencies to speed up builds
- Uploads build artifacts for failed tests for easier debugging
- Automatically starts and stops Selenium Grid in Docker containers

To view test results:
1. Go to the Actions tab in your GitHub repository
2. Select the latest workflow run
3. Download artifacts for any failed tests
4. For UI test failures with Allure reports, run `allure serve build/allure-results` locally

### Running UI Tests

Tests can be run either locally or remotely using Docker Selenium Grid.

#### Local Execution (Default)

Requires browsers to be installed locally  (use <"> or <'> to wrap xml path):

_Chrome (default)_

`./gradlew clean test --tests com.webqa.tests.ui.LoginTest`

`./gradlew clean test -PsuiteFile=src/test/resources/regression.xml`

#### Remote Execution (Docker)

1. Start Selenium Grid and browser containers:

```bash
docker-compose up -d
```

2. Access test execution:

- Selenium Grid: `http://localhost:4444/ui`
- Chrome VNC: `http://localhost:7900` (no password required)
- Firefox VNC: `http://localhost:7901` (no password required)

3. Run tests:

`./gradlew clean test -PsuiteFile=src/test/resources/ui-tests.xml -Dremote=true`

4. Stop containers:

```bash
docker-compose down
```

### Test Suites

The project includes several TestNG XML suites in `src/test/resources/`:

1. `testNg.xml`: All tests
2. `ui-tests.xml`: UI tests in Chrome and Firefox (parallel execution)
3. `api-tests.xml`: API tests only
4. `smoke.xml`: Critical path tests
5. `regression.xml`: Full regression suite

Run a specific suite (use <"> or <'> to wrap xml path):

```bash
# Local execution
gradlew.bat test -PsuiteFile=src/test/resources/regression.xml

# Remote execution (Docker)
gradlew.bat test -PsuiteFile=src/test/resources/regression.xml -Dremote=true
```

### Parallel Test Execution

The framework supports parallel test execution across different browsers. For example, `ui-tests.xml` runs tests simultaneously in Chrome and Firefox:

```xml

<suite name="UI Test Suite" parallel="tests" thread-count="2">
    <test name="Chrome Tests">
        <parameter name="browser" value="chrome"/>
        <classes>
            <class name="com.webqa.tests.ui.LoginTest"/>
            <class name="com.webqa.tests.ui.ProductTest"/>
            <class name="com.webqa.tests.ui.SignUpUITest"/>
        </classes>
    </test>
    <test name="Firefox Tests">
        <parameter name="browser" value="firefox"/>
        <classes>
            <class name="com.webqa.tests.ui.LoginTest"/>
            <class name="com.webqa.tests.ui.ProductTest"/>
            <class name="com.webqa.tests.ui.SignUpUITest"/>
        </classes>
    </test>
</suite>
```

### Browser Compatibility

The framework has been tested with:

#### Local Execution:

- Chrome: 131.0.6778.140
- Firefox: 133.0.3

#### Remote Execution (Selenium Grid 4.27.0):

- Chrome and Firefox versions are determined by the Selenium Grid Docker images in `docker-compose.yml`

Browser selection:

- TestNG XML: `<parameter name="browser" value="firefox"/>`
- Default: Chrome

Execution mode:

- Local: Default
- Remote: `-Dremote=true`

## 📝 Test Categories

### UI Tests

- Login functionality
- Sign-up process
- Product management
- Shopping cart operations

### API Tests

The framework supports two types of API testing approaches:

1. **Real API Testing**
    - Direct integration with actual API endpoints
    - Uses REST Assured for HTTP requests
    - Suitable for integration and end-to-end testing

2. **Mock API Testing (WireMock)**
    - Simulates API responses for controlled testing
    - Supports various scenarios:
        - Success/failure responses
        - Network delays
        - Rate limiting
        - Custom response templates

#### Running WireMock Tests

1. Configure WireMock (optional):
    - Edit `.env` file to change WireMock port (default: **9999**)
    - Update `application.conf` mock section if needed

2. Start WireMock container:

```bash
docker-compose up -d wiremock
```

3. Run WireMock tests:

```bash
./gradlew clean test --tests "com.webqa.tests.api.SignUpWireMockTest"
```

4. Access WireMock UI:
    - URL: `http://localhost:9999/__admin/health`


#### WireMock Features

- **Response Templates**: Pre-defined response patterns in `WireMockResponses`
- **Builder Pattern**: Fluent API for creating responses via `WireMockResponseBuilder`
- **Scenario Testing**: Support for stateful behavior simulation
- **Network Conditions**: Ability to test delays and timeouts
- **Docker Integration**: Runs in containerized environment for consistency

## 📚 Swagger UI Documentation

To view the OpenAPI specification in Swagger UI:

1. Compile the project:

```bash
./gradlew compileJava
```

2. Start the Swagger UI server:

```bash
java -cp build/classes/java/main com.webqa.swagger.SwaggerServer
```

The server will start on port 9000 (or the next available port up to 9004).

3. Open the Swagger UI in your browser:

```
http://localhost:9000
```

4. To stop the server:
    - Press `Ctrl+C` in the terminal where the server is running

The Swagger UI provides an interactive interface to:

- Browse all API endpoints
- View request/response schemas
- Read API documentation

## 📊 Test Reports

The framework uses Allure for test reporting. After test execution:

1. Generate Allure report:

```bash
./gradlew allureReport
```

2. Open Allure report:

```bash
./gradlew allureServe
```

## 🔧 Configuration

The framework uses Typesafe Config for configuration management. Sensitive values (credentials, URLs) are loaded from environment variables with safe placeholder defaults.

Main configuration files:

- `application.conf`: Main configuration file (uses `${?ENV_VAR}` substitution for secrets)
- `.env.example`: Template listing all required/optional environment variables
- `petstore-openapi.json`: OpenAPI spec file
- `logback-test.xml`: Logging configuration
- `docker-compose.yml`: Docker file to run FF and Chrome instances via selenium/hub and Wiremock service

## 🧪 Test Data Management

- Uses DataFaker for generating test data

## 🔒 Code Security Analysis

The framework includes Detekt, a static code analysis tool for Kotlin with a focus on security. The implementation helps to identify potential code vulnerabilities, anti-patterns, and security issues.

### Running Security Analysis

Run the security analysis with the following command:

```bash
./gradlew detektSecurityCheck
```

By default, this task will:
- Analyze all Kotlin code in the project
- Generate reports in HTML, XML, and SARIF formats in `build/reports/detekt/`
- Fail the build if any issues are found (maxIssues is set to 0)

To run the check without failing the build on issues:

```bash
./gradlew detektSecurityCheck -Ddetekt.maxIssues=100
```

### Security Rules

The security check focuses on:
- Exception handling issues (too generic exceptions, swallowed exceptions)
- Potential bugs (unsafe casts, null safety issues, etc.)
- API design issues (public visibility where unnecessary)
- Coroutine safety issues

### Configuration

The Detekt configuration files are located in:
- `config/detekt/detekt.yml` - Main configuration
- `config/detekt/detekt-security.yml` - Security-focused rules
- `config/detekt/baseline.xml` - Baseline of accepted issues
