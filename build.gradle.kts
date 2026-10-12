import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.0.21"
    id("io.qameta.allure") version "2.12.0"
    id("org.openapi.generator") version "7.26.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "com.webqa"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))

    // Testing
    testImplementation("org.testng:testng:7.12.0")

    // Assertions
    testImplementation("org.assertj:assertj-core:3.27.7")

    // Selenium
    implementation("org.seleniumhq.selenium:selenium-java:4.51.0")
    implementation("io.github.bonigarcia:webdrivermanager:5.9.3")

    // REST Assured
    implementation("io.rest-assured:rest-assured:5.5.7")
    implementation("io.rest-assured:kotlin-extensions:5.5.7")

    // Allure
    implementation("io.qameta.allure:allure-testng:2.35.5")
    implementation("io.qameta.allure:allure-rest-assured:2.35.5")
    implementation("io.qameta.allure:allure-attachments:2.35.5")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("io.qameta.allure:allure-okhttp3:2.35.5")

    // Data Generation
    implementation("net.datafaker:datafaker:2.7.0")

    // Configuration
    implementation("com.typesafe:config:1.4.9")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.3")

    // WireMock
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")

    testImplementation("ch.qos.logback:logback-classic:1.6.5")
    testImplementation("org.slf4j:slf4j-api:2.0.20")

    implementation("com.squareup.moshi:moshi:1.15.2")
    // For Kotlin support
    implementation("com.squareup.moshi:moshi-kotlin:1.15.2")

    // Detekt
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

// OpenAPI Generator configuration
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/src/main/resources/petstore-openapi.json")
    outputDir.set(layout.buildDirectory.dir("generate-resources").get().asFile.path)
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
    systemProperty("remote", System.getProperty("remote", "false"))

    // Pass .env values as environment variables (OS env vars take precedence)
    dotEnv.forEach { (key, value) ->
        environment(key, System.getenv(key) ?: value)
    }

    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Add generated sources to the main source set
sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generate-resources/src/main"))
        }
    }
}

allure {
    version = "2.46.1"
}

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$projectDir/config/detekt/detekt.yml"))
    baseline = file("$projectDir/config/detekt/baseline.xml")
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

// Ensure OpenAPI classes are generated before compiling
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(tasks.openApiGenerate)
}

tasks.register<Delete>("cleanOpenApiGenerated") {
    delete(layout.buildDirectory.dir("generate-resources"))
}
// ./gradlew openApiGenerate
