# WebQA Test Automation Framework

A test automation framework built with Kotlin features UI and API testing capabilities.

The framework uses TestNG for test execution, Selenium WebDriver for UI testing, and REST Assured for API testing.

## 🛠 Tech Stack

- **Language**: Kotlin
- **Build Tool**: Gradle
- **Testing Framework**: TestNG
- **UI Testing**: Selenium WebDriver
- **API Testing**: REST Assured
- **Assertions**: AssertJ
- **Reporting**: Allure
- **Configuration**: Typesafe Config
- **Data Generation**: DataFaker
- **Logging**: Logback + SLF4J
- **API Client Generation**: OpenAPI Generator

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
        └── ui                # UI test implementations

Configuration Files:
├── src/main/resources
│   ├── allure.properties     # Allure reporting configuration
│   ├── application.conf      # Application configuration
│   └── petstore-openapi.json # OpenAPI specification
└── src/test/resources
    ├── api-tests.xml         # API tests configuration
    ├── logback-test.xml      # Test logging configuration
    ├── regression.xml        # Full regression suite
    ├── smoke.xml             # Smoke test suite
    ├── testNg.xml            # Main TestNG configuration
    └── ui-tests.xml          # UI tests configuration (parallel)
```

## 🚀 Running Tests

### Prerequisites

1. Java 17
2. Gradle installed (or use the included Gradle wrapper)
3. Docker installed (for remote execution)
4. Browsers installed (for local execution)

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

#### Remote Execution (Selenium Grid):

- Chrome: 120.0.6099.109
- Firefox: 120.0.1

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

- Authentication endpoints
- Product API operations
- Pet store API operations (using OpenAPI generated client)
- Order management

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

The framework uses Typesafe Config for configuration management. Main configuration files:

- `application.conf`: Main configuration file
- `petstore-openapi.json`: OpenAPI spec file
- `logback-test.xml`: Logging configuration
- `docker-compose.yml`: Docker file to run FF and Chrome instances via selenium/hub:4.16.1

## 🧪 Test Data Management

- Uses DataFaker for generating test data
