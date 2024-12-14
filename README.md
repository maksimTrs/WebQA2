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
│   ├── core
│   │   ├── api        # API clients and models
│   │   ├── config     # Configuration classes
│   │   ├── ui         # Page objects and UI components
│   │   └── utils      # Utility classes
│   └── resources      # Configuration files and test data
└── test/kotlin/com/webqa
    └── tests
        ├── api        # API tests
        ├── ui         # UI tests
        └── utils      # Test utilities
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

Requires browsers to be installed locally:
```bash
# Chrome (default)
gradlew.bat test --tests com.webqa.tests.ui.LoginTest

# Firefox
gradlew.bat test --tests com.webqa.tests.ui.LoginTest -Dbrowser=firefox
```

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
```bash
# Chrome
gradlew.bat test --tests com.webqa.tests.ui.LoginTest -Dremote=true

# Firefox
gradlew.bat test --tests com.webqa.tests.ui.LoginTest -Dbrowser=firefox -Dremote=true
```

4. Stop containers:
```bash
docker-compose down
```

### Test Suites

The project includes several TestNG XML suites in `src/test/resources/`:

1. `testNg.xml`: All tests
2. `ui-tests.xml`: UI tests in Chrome and Firefox
3. `api-tests.xml`: API tests only
4. `smoke.xml`: Critical path tests
5. `regression.xml`: Full regression suite

Run a specific suite:
```bash
# Local execution
gradlew.bat test -PsuiteFile=src/test/resources/regression.xml

# Remote execution (Docker)
gradlew.bat test -PsuiteFile=src/test/resources/regression.xml -Dremote=true
```

Browser selection:
- TestNG XML: `<parameter name="browser" value="firefox"/>`
- System property: `-Dbrowser=firefox`
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

## 🧪 Test Data Management

- Uses DataFaker for generating test data
- Test data generators available in `TestDataGenerator` class
