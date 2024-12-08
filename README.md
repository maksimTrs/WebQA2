# WebQA Test Automation Framework

A test automation framework built with Kotlin, featuring both UI and API testing capabilities. 

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
3. Chrome browser installed (for UI tests) 

**_TODO_**: Add Docker runners for Chrome and Firefox

### Running Tests Using TestNG XML

The project includes several TestNG XML suites for different test categories:

1. Run all tests:

`./gradlew test`


2. Run  test suite:

`./gradlew test -PsuiteFile=src/test/resources/testNg.xml`


### Available Test Suites **_TODO_**:

- `api-tests.xml`: Runs all API tests
- `ui-tests.xml`: Runs all UI tests
- `regression.xml`: Runs full regression suite
- `smoke.xml`: Runs smoke tests

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

`./gradlew allureReport`


2. Open Allure report:

`./gradlew allureServe`


## 🔧 Configuration

The framework uses Typesafe Config for configuration management. Main configuration files:

- `application.conf`: Main configuration file
- `petstore-openapi.json`: OpenAPI spec file
- `logback-test.xml`: Logging configuration

## 🧪 Test Data Management

- Uses DataFaker for generating test data
- Test data generators available in `TestDataGenerator` class

