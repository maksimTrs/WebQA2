# WebQA Test Automation Framework

[![Smoke Tests](https://github.com/maksimTrs/WebQA/actions/workflows/smoke.yml/badge.svg?branch=master)](https://github.com/maksimTrs/WebQA/actions/workflows/smoke.yml)
[![Allure Report](https://img.shields.io/badge/Allure-report-orange)](https://maksimtrs.github.io/WebQA/)

UI and API test automation for the WebQA demo shop, written in Kotlin on TestNG, Selenium WebDriver and REST Assured.

## Architecture

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/architecture-dark.png">
  <img alt="WebQA architecture: CI and local Gradle runs start TestNG suites; UI tests drive the WebQA app through page objects and WebDriver (local or Selenium Grid in Docker); API tests use a shared REST Assured client that also targets a WireMock container; Petstore tests use a generated OpenAPI client; configuration and reports are shared" src="docs/architecture-light.png">
</picture>

<sub>Source: <code>docs/architecture.html</code>. After editing it, re-take both PNG screenshots of <code>.frame</code> (light and dark color scheme, 2x).</sub>

- **UI**: fluent page objects on `By` locators; each action is an Allure step. The stand has no `data-testid`, so locators prefer `id`, `href` and text. A failed test gets the URL, a screenshot, the page source and (Chrome) the console in Allure.
- **Secrets**: passwords, tokens and `Authorization` are masked in logs and Allure; password fields are emptied in the page source attached on failure.
- **Logs**: console at INFO, `build/logs/test.log` at DEBUG, each test's own log attached to Allure.
- **Groups**: `smoke`, `wiremock`, `destructive`. The order test buys stock irreversibly, so `destructive` runs only in `regression.xml`: other suites exclude it, and so does a run without a suite file.
- **Reports**: Allure categories separate stand outages, product defects and broken tests. No automatic retries: a retried pass hides flakiness; CI checks the stand before smoke instead.

Stack: Kotlin · Gradle · TestNG · Selenium 4 · REST Assured · WireMock · AssertJ · Allure · DataFaker · detekt.

## Setup

1. JDK 17+ to run Gradle; the JDK 17 toolchain is downloaded if missing.
2. Chrome or Firefox for local UI runs, Docker for Selenium Grid and WireMock.
3. Credentials of a stand account (register one at https://webqa.mercdev.com/signup):
   ```bash
   cp .env.example .env   # set TEST_USER_EMAIL and TEST_PASSWORD
   ```
   Environment variables override `.env`. Other options (browser, URLs, headless, timeouts) are in `.env.example`.
4. Git hooks, once per clone: `git config core.hooksPath .githooks`. `pre-commit` checks that `@Test` files are named `*Test.kt`, `pre-push` runs detekt.

## Running tests

```bash
./gradlew test --tests com.webqa.tests.ui.LoginTest
./gradlew test -PsuiteFile=src/test/resources/api-tests.xml
WEBDRIVER_HEADLESS=true ./gradlew test -PsuiteFile=src/test/resources/smoke-ui.xml
```

| Suite (`src/test/resources/`) | Content |
|---|---|
| `smoke-api.xml`, `smoke-ui.xml` | Group `smoke`, used by CI |
| `api-tests.xml` | WebQA API and Petstore, without `wiremock` and `destructive` |
| `ui-tests.xml` | UI in Chrome and Firefox in parallel |
| `testNg.xml` | Everything without `wiremock` and `destructive` |
| `regression.xml` | Everything (WireMock needs the container) |

### Selenium Grid

```bash
docker compose up -d --wait selenium-hub chrome firefox
./gradlew test -PsuiteFile=src/test/resources/ui-tests.xml -Dremote=true
```

Grid UI: http://localhost:4444/ui. VNC (no password): Chrome http://localhost:7900, Firefox http://localhost:7901. Ports are bound to `127.0.0.1`.

### WireMock

```bash
docker compose up -d --wait wiremock      # port: WIREMOCK_PORT, default 9999
./gradlew test --tests com.webqa.tests.api.ApiClientWireMockTest
```

### Petstore locally

```bash
docker compose up -d --wait petstore
PETSTORE_BASE_URL=http://localhost:8085/api/v3 ./gradlew test --tests com.webqa.tests.api.pets.PetApiTest
```

## Reports

```bash
./gradlew allureServe     # allureReport only builds it
```

Output: `build/allure-results`, `build/test-results/test`, `build/logs/test.log`. CI publishes the latest `master` report to [GitHub Pages](https://maksimtrs.github.io/WebQA/).

## Static analysis

```bash
./gradlew detekt
```

Fails on any finding. Config: `config/detekt/detekt.yml` (deviations from the defaults). Reports: `build/reports/detekt/`.

## CI

`smoke.yml` runs on pushes to `master`, pull requests and manually. **Lint Check** (detekt) runs alongside **Test Stand Check** (`curl`, 3 attempts; if the stand is down, tests are skipped) → **API Tests** and **UI Tests** (on Grid) → **Allure Report**, deployed to Pages from `master`. Changes to `*.md` and `docs/` alone do not start it.

`regression.yml` runs `regression.xml` on manual dispatch only: the shared stand is too unstable for a schedule.

Credentials come from repository secrets, so fork PRs run only the lint. Reports and logs are uploaded as artifacts when a job fails.
