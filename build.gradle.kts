import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.allure)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.detekt)
}

group = "com.webqa"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.selenium)

    implementation(libs.rest.assured)
    implementation(libs.rest.assured.kotlin)
    implementation(libs.jackson.kotlin)

    implementation(libs.allure.testng)
    implementation(libs.allure.rest.assured)
    implementation(libs.allure.okhttp3)

    // Generated Petstore client
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)

    implementation(libs.datafaker)
    implementation(libs.typesafe.config)
    implementation(libs.slf4j.api)

    testImplementation(libs.testng)
    testImplementation(libs.assertj)
    testImplementation(libs.wiremock)
    testImplementation(libs.logback)

    detektPlugins(libs.detekt.formatting)
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/src/main/resources/petstore-openapi.json")
    outputDir.set(layout.buildDirectory.dir("generate-resources").get().asFile.path)
}

// Generated sources are compiled with the main source set
sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generate-resources/src/main"))
        }
    }
}

// Compiles and runs tests on JDK 17 whatever JDK runs Gradle; also sets jvmTarget to 17
kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(tasks.openApiGenerate)
}

// Load .env file into environment for test tasks
val dotEnv = rootProject.file(".env")
    .takeIf { it.exists() }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    ?.associate { line ->
        val (key, value) = line.split("=", limit = 2)
        key.trim() to value.trim().removeSurrounding("\"").removeSurrounding("'")
    }
    ?: emptyMap()

tasks.test {
    useTestNG {
        useDefaultListeners = true
        if (project.hasProperty("suiteFile")) {
            // A missing suite file otherwise runs zero tests and the build stays green
            val suite = file(project.property("suiteFile").toString())
            require(suite.isFile) { "TestNG suite file not found: $suite" }
            suites(suite)
        }
    }
    systemProperty("allure.results.directory", "build/allure-results")
    // -Dremote=true on the Gradle command line overrides webdriver.remote (Typesafe Config reads system properties)
    System.getProperty("remote")?.let { systemProperty("webdriver.remote", it) }
    // Tests hit live stands and read .env, neither of which Gradle tracks: never skip as UP-TO-DATE
    outputs.upToDateWhen { false }

    // Pass .env values as environment variables (OS env vars take precedence)
    dotEnv.forEach { (key, value) ->
        environment(key, System.getenv(key) ?: value)
    }

    testLogging {
        events("passed", "skipped", "failed")
        // Full cause chain in the console: e.g. a missing TEST_USER_EMAIL is otherwise visible only in the report
        exceptionFormat = TestExceptionFormat.FULL
    }
}

allure {
    version = libs.versions.allure.commandline.get()
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
    parallel = true
}

tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektSecurityCheck") {
    description = "Run security focused detekt checks"
    setSource(files("src/main/kotlin", "src/test/kotlin"))
    config.setFrom(files("$projectDir/config/detekt/detekt-security.yml"))
    // Separate file names: by default both detekt tasks write build/reports/detekt/detekt.*
    reports {
        html.required.set(true)
        html.outputLocation.set(layout.buildDirectory.file("reports/detekt/security.html"))
        xml.required.set(true)
        xml.outputLocation.set(layout.buildDirectory.file("reports/detekt/security.xml"))
        txt.required.set(false)
        sarif.required.set(true)
        sarif.outputLocation.set(layout.buildDirectory.file("reports/detekt/security.sarif"))
        md.required.set(false)
    }
    include("**/*.kt")
    include("**/*.kts")
    exclude("**/build/**")
    exclude("**/resources/**")
}
