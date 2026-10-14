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

- **UI**: fluent page objects (`HomePage.open().clickLogin().login(...)` returns the next page) on explicit waits only. Elements are found by `By` on each action, not through PageFactory proxies, and every wait fails with a message naming the element. Each page action is an Allure step. One driver per thread, so suites run in parallel. Chrome or Firefox, locally or on Selenium Grid. A failed test gets the URL, a screenshot, the page source and (Chrome) the browser console attached to Allure.
- **API**: one `ApiClient` base for all clients. It applies timeouts and logging, and every call is attached to Allure. The `Authorization` header is masked in logs and in Allure. The login call carries the password in and the token out, so only its URI and status are logged, and it is not attached.
- **Mocks**: `ApiClientWireMockTest` points the clients at a WireMock container to test the framework itself where the live stand cannot: the request body and headers the clients send, that the models survive undeclared response fields and keep decimal prices exact, and that a slow response hits the client timeout instead of hanging the run.
- **Petstore**: a client generated from `petstore-openapi.json` by OpenAPI Generator. Tests delete the pets they create. They target the public demo by default; `docker compose up -d --wait petstore` plus `PETSTORE_BASE_URL=http://localhost:8085/api/v3` runs them against a local copy.
- **Config**: `application.conf` (Typesafe Config) with environment-variable overrides. A test that logs in fails at once with a clear message if `TEST_USER_EMAIL` / `TEST_PASSWORD` is missing; tests that do not log in (sign-up, Petstore, WireMock) run without them.
- **Suites**: TestNG suites select tests by package and group (`smoke`, `wiremock`, `destructive`), not by class or method name, so a new or renamed test stays in its suites.
- **Reports**: Allure sorts failures into categories (`config/allure/categories.json`): *Test stand unavailable* (connection errors, timeouts, 5xx), *Product defects* (an assertion failed) and *Broken tests* (any other exception).

Stack: Kotlin · Gradle · TestNG · Selenium 4 · REST Assured · WireMock · AssertJ · Allure · DataFaker · detekt.

## Design decisions

The trade-offs behind the framework, and what each one costs.

- **Login inside the test, not in a configuration method.** A failed `@BeforeMethod`/`@BeforeClass` marks the tests SKIPPED, which hides a broken login. UI tests log in through a helper called from the test body; API tests use a `lazy` token that a failed login leaves uninitialized, so every test fails with the login error. Cost: the first API test of a class carries the login time.
- **No automatic test retries.** A retried pass hides a flaky test or a flaky product. Instead the CI checks the stand before smoke and Allure files outages under *Test stand unavailable*. Only driver session creation is retried: that is infrastructure, not the subject of a test.
- **`By` locators instead of PageFactory.** PageFactory caches proxies that go stale when React re-renders a node; a `By` is resolved on each action. Waits name the element in their failure message, and checks that wait (cart, alert) fail with that message instead of returning `false`.
- **Locators without test ids.** The stand has no `data-testid` and the frontend is not ours to change. Locators prefer `id`, `href` and visible text; MUI class names are used only where nothing else identifies the element (product cards, *Add to cart*). All of them are constants in their page object, so a theme change is a one-file fix.
- **A shared live stand.** Each test creates its own data (UUID emails, random pet ids) and deletes what it can (Petstore pets). An order cannot be undone through the API, so the order test is in the `destructive` group and runs only in `regression.xml`.
- **Two HTTP clients.** REST Assured for the hand-written WebQA API clients; a client generated from the OpenAPI spec for Petstore, to show the contract-first approach. Cost: the generated client brings OkHttp and Moshi next to REST Assured and Jackson.
- **WireMock tests the framework, not the backend.** Stubs that only echo themselves prove nothing; the mocked tests check what the live stand cannot show: the request contract, tolerance to response drift, and that timeouts are applied.

## Setup

1. JDK 17 or newer to run Gradle. The tests are compiled and run on a JDK 17 toolchain, which Gradle downloads if the machine has none.
2. Chrome or Firefox for local UI runs. Docker for Selenium Grid and WireMock.
3. Credentials for the test stand:
   ```bash
   cp .env.example .env   # then set TEST_USER_EMAIL and TEST_PASSWORD
   ```
   Gradle passes `.env` to the tests. Real environment variables take precedence. Optional overrides (browser, URLs, headless, Grid URL, timeouts) are listed in `.env.example`.
4. Git hooks (once per clone):
   ```bash
   git config core.hooksPath .githooks
   ```
   `pre-commit` checks that files with `@Test` are named `*Test.kt`. `pre-push` runs the same detekt checks as the CI Lint Check job.

## Running tests

```bash
# One class / one suite
./gradlew test --tests com.webqa.tests.ui.LoginTest
./gradlew test -PsuiteFile=src/test/resources/api-tests.xml

# Headless
WEBDRIVER_HEADLESS=true ./gradlew test -PsuiteFile=src/test/resources/smoke-ui.xml
```

| Suite (`src/test/resources/`) | Content |
|---|---|
| `smoke-api.xml`, `smoke-ui.xml` | Group `smoke`: critical path. Used by CI |
| `api-tests.xml` | WebQA API and Petstore tests, except `wiremock` and `destructive` |
| `ui-tests.xml` | UI tests in Chrome and Firefox in parallel |
| `testNg.xml` | All tests except groups `wiremock` and `destructive` |
| `regression.xml` | Everything, including WireMock (needs the container) and `destructive` (buys stock on the shared stand) |

### Selenium Grid

```bash
docker compose up -d --wait selenium-hub chrome firefox   # --wait: until a node has registered
./gradlew test -PsuiteFile=src/test/resources/ui-tests.xml -Dremote=true
```

Grid UI: http://localhost:4444/ui. Watch a browser over VNC: Chrome at http://localhost:7900, Firefox at http://localhost:7901 (no password). All ports are bound to `127.0.0.1`, so they are not reachable from the network.

### WireMock

```bash
docker compose up -d --wait wiremock      # host port: WIREMOCK_PORT, default 9999
./gradlew test --tests com.webqa.tests.api.ApiClientWireMockTest
```

The tests register their own stubs via the admin API, each matching a value unique to its test (email, token), and reset WireMock before and after the class, so `wiremock/mappings` can stay empty and an interrupted run leaves no state behind.

## Reports

```bash
./gradlew allureServe     # build and open the Allure report (allureReport only builds it)
```

Raw results are written to `build/allure-results`, JUnit XML to `build/test-results/test`. CI publishes the report of the latest `master` run, with trends, to [GitHub Pages](https://maksimtrs.github.io/WebQA/).

## Static analysis

```bash
./gradlew detekt detektStrict
```

Both tasks fail on any finding (`maxIssues: 0`). Reports are written to `build/reports/detekt/`. Config: `config/detekt/detekt.yml` (only the deviations from the detekt defaults) and `detekt-strict.yml`, a stricter subset for exception handling, potential bugs and nullability. Neither is a security scan.

## CI

`.github/workflows/smoke.yml` runs on pushes to `master`, on pull requests and manually:

- **Lint Check**: detekt.
- **Test Stand Check**: `curl` on the app and the API, three attempts. If the stand does not answer, the test jobs are skipped with one clear error instead of a page of failures, and no report replaces the published one.
- **API Tests**: `smoke-api.xml`.
- **UI Tests**: `smoke-ui.xml` on Selenium Grid in Docker.
- **Allure Report**: merges the results of both test jobs into one report, uploaded as an artifact. On `master` it is deployed to GitHub Pages.

`.github/workflows/regression.yml` runs `regression.xml` on manual dispatch only (the shared stand is too unstable for a schedule): UI tests in Chrome and Firefox on Grid, API tests including WireMock and Petstore in local containers.

Credentials come from the `TEST_USER_EMAIL` and `TEST_PASSWORD` secrets. Pull requests from forks get no secrets, so they run only the lint. A newer push to a pull request cancels its outdated run; runs on `master` queue instead, so a Pages deployment is never cut off. Test and Grid reports are uploaded as artifacts when a job fails. Gradle runs through `gradle/actions/setup-gradle`, which caches dependencies and validates the wrapper jar.

Dependabot (`.github/dependabot.yml`) proposes weekly updates of the version catalog, the pinned actions and the Docker images: one grouped pull request per ecosystem for minor and patch versions, majors one by one.
