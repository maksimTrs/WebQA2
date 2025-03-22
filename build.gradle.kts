import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"
    id("io.qameta.allure") version "2.11.2"
    id("org.openapi.generator") version "7.9.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.5"
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
    testImplementation("org.testng:testng:7.8.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")

    // Assertions
    testImplementation("org.assertj:assertj-core:3.24.2")

    // Selenium
    implementation("org.seleniumhq.selenium:selenium-java:4.11.0")
    implementation("io.github.bonigarcia:webdrivermanager:5.4.1")

    // REST Assured
    implementation("io.rest-assured:rest-assured:5.3.1")
    implementation("io.rest-assured:kotlin-extensions:5.3.1")

    // Allure
    implementation("io.qameta.allure:allure-testng:2.22.2")
    implementation("io.qameta.allure:allure-rest-assured:2.22.2")
    implementation("io.qameta.allure:allure-attachments:2.22.2")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.1")
    implementation("io.qameta.allure:allure-okhttp3:2.17.0")

    // Data Generation
    implementation("net.datafaker:datafaker:2.0.1")

    // Configuration
    implementation("com.typesafe:config:1.4.2")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")

    // WireMock
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.0")
    testImplementation("org.wiremock:wiremock-standalone:3.0.4")

    testImplementation("ch.qos.logback:logback-classic:1.4.11")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testImplementation("org.codehaus.janino:janino:3.1.10")

    implementation("com.squareup.moshi:moshi:1.14.0")
    // For Kotlin support
    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")

    // Swagger UI
    implementation("org.webjars:swagger-ui:5.10.3")

    // Detekt
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.5")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-libraries:1.23.5") 
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-ruleauthors:1.23.5")
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-complexity:1.23.5")
    // Security rules
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-rules-coroutines:1.23.5")
}

// OpenAPI Generator configuration
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/src/main/resources/petstore-openapi.json")
    outputDir.set("${buildDir}/generate-resources")
}

tasks.test {
    useTestNG {
        useDefaultListeners = true
        if (project.hasProperty("suiteFile")) {
            suites(project.property("suiteFile").toString())
        }
    }
    systemProperty("allure.results.directory", "build/allure-results")
    systemProperty("remote", System.getProperty("remote", "false"))

    testLogging {
        events("passed", "skipped", "failed")
    }
}

// Add generated sources to the main source set
sourceSets {
    main {
        kotlin {
            srcDir("$buildDir/generate-resources/src/main")
        }
    }
    /*    test {
            kotlin {
                srcDir("src/test/kotlin")
            }
        }*/
}

allure {
    version = "2.22.2"
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
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(true)
    }
    include("**/*.kt")
    include("**/*.kts")
    exclude("**/build/**")
    exclude("**/resources/**")
}

// Ensure OpenAPI classes are generated before compiling
tasks.withType<KotlinCompile> {
    dependsOn(tasks.openApiGenerate)
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.register<Delete>("cleanOpenApiGenerated") {
    delete("${buildDir}/generate-resources")
}
// ./gradlew openApiGenerate
