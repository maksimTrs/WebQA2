# WebQA Test Automation Framework

UI and API test automation for the WebQA demo shop, written in Kotlin on TestNG, Selenium WebDriver and REST Assured.

## Architecture

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/architecture-dark.png">
  <img alt="WebQA architecture: CI and local Gradle runs start TestNG suites; UI tests drive the WebQA app through page objects and WebDriver (local or Selenium Grid in Docker); API tests use a shared REST Assured client that also targets a WireMock container; Petstore tests use a generated OpenAPI client; configuration and reports are shared" src="docs/architecture-light.png">
</picture>

<sub>Source: <code>docs/architecture.html</code>. After editing it, re-take both PNG screenshots of <code>.frame</code> (light and dark color scheme, 2x).</sub>

- **UI**: page objects on explicit waits (`WebDriverWait`, no implicit waits). One driver per thread, so suites run in parallel. Chrome or Firefox, locally or on Selenium Grid. A screenshot is attached to Allure when a test fails.
- **API**: one `ApiClient` base for all clients. It applies timeouts and logging, and every call is attached to Allure. The `Authorization` header is masked in logs, and the login response that carries the token is neither logged nor attached.
- **Mocks**: the same client can point at a WireMock container, which serves stateful scenarios, delays and rate limiting (`SignUpWireMockTest`).
- **Petstore**: a client generated from `petstore-openapi.json` by OpenAPI Generator. Tests delete the pets they create.
- **Config**: `application.conf` (Typesafe Config) with environment-variable overrides. A missing `TEST_USER_EMAIL` / `TEST_PASSWORD` fails the run immediately with a clear message.

Stack: Kotlin · Gradle · TestNG · Selenium 4 · REST Assured · WireMock · AssertJ · Allure · DataFaker · detekt.

## Setup

1. JDK 17.
2. Chrome or Firefox for local UI runs. Docker for Selenium Grid and WireMock.
3. Credentials for the test stand:
   ```bash
   cp .env.example .env   # then set TEST_USER_EMAIL and TEST_PASSWORD
   ```
   Gradle passes `.env` to the tests. Real environment variables take precedence. Optional overrides (browser, URLs, headless, Grid URL) are listed in `.env.example`.

## Running tests

```bash
# One class / one suite
./gradlew test --tests com.webqa.tests.ui.LoginTest
./gradlew test -PsuiteFile=src/test/resources/api-tests.xml

# Headless
WEBDRIVER_HEADLESS=true ./gradlew test -PsuiteFile=src/test/resources/smoke-ui.xml
```

> Gradle skips a test task whose inputs have not changed, and nothing actually runs. To force a re-run, add `--rerun`, e.g. after restarting a container or changing `.env`.

| Suite (`src/test/resources/`) | Content |
|---|---|
| `smoke-api.xml`, `smoke-ui.xml` | Critical path. Used by CI |
| `api-tests.xml` | WebQA API and Petstore tests |
| `ui-tests.xml` | UI tests in Chrome and Firefox in parallel |
| `testNg.xml` | All tests except WireMock |
| `regression.xml` | Everything, including WireMock (needs the container) |

### Selenium Grid

```bash
docker compose up -d selenium-hub chrome firefox
./gradlew test -PsuiteFile=src/test/resources/ui-tests.xml -Dremote=true
```

Grid UI: http://localhost:4444/ui. Watch a browser over VNC: Chrome at http://localhost:7900, Firefox at http://localhost:7901 (no password).

### WireMock

```bash
docker compose up -d wiremock      # host port: WIREMOCK_PORT, default 9999
./gradlew test --tests com.webqa.tests.api.SignUpWireMockTest
```

The tests register their own stubs via the admin API, so `wiremock/mappings` can stay empty.

## Reports

```bash
./gradlew allureServe     # build and open the Allure report (allureReport only builds it)
```

Raw results are written to `build/allure-results`, JUnit XML to `build/test-results/test`.

## Static analysis

```bash
./gradlew detekt detektSecurityCheck
```

Both tasks fail on any finding (`maxIssues: 0`). Reports are written to `build/reports/detekt/`. Config: `config/detekt/detekt.yml`, `detekt-security.yml`, `baseline.xml`.

## CI

`.github/workflows/smoke.yml` runs on pushes to `main`/`master`/`dev*`, on pull requests and manually. Three independent jobs:

- **Lint Check**: detekt.
- **API Tests**: `smoke-api.xml`.
- **UI Tests**: `smoke-ui.xml` on Selenium Grid in Docker.

Credentials come from the `TEST_USER_EMAIL` and `TEST_PASSWORD` secrets. A newer push cancels the outdated run. Reports are uploaded as artifacts when a job fails.
