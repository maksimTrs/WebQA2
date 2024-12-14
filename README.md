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
3. Docker installed (for UI tests) 
4. Chrome browser installed (for UI tests) 

**_TODO_**: Add Docker runners for Chrome and Firefox

### Running UI Tests with Docker

1. Start Selenium Grid and browser containers:
```bash
docker-compose up -d
```

2. Access Selenium Grid and browsers:
- Grid Console: `http://localhost:4444/ui`
- Chrome VNC: `http://localhost:7900` (no password required)
- Firefox VNC: `http://localhost:7901` (no password required)

VNC viewers allow you to watch tests running in real-time in the browser containers.

3. Run tests in Chrome (default):
```bash
gradlew.bat test --tests com.webqa.tests.ui.LoginTest
```

4. Run tests in Firefox:
```bash
gradlew.bat test --tests com.webqa.tests.ui.LoginTest -Dbrowser=firefox
```

5. View Selenium Grid console:
```
http://localhost:4444/ui
```

6. Stop containers when done:
```bash
docker-compose down
```
### Running Tests Using TestNG XML

The project includes several TestNG XML suites for different test categories:

1. Run all tests:
```bash
./gradlew test
```
2. Run specific test suite:
```bash
./gradlew test -PsuiteFile=src/test/resources/testNg.xml
```
### Available Test Suites

Test suite XMLs in `src/test/resources/`:
- `testNg.xml`: Runs all tests
- `ui-tests.xml`: UI tests only (Chrome and Firefox)
- `api-tests.xml`: API tests only
- `smoke.xml`: Critical path tests
- `regression.xml`: Runs full regression suite

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
