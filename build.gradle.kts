import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.0"
    id("io.qameta.allure") version "2.11.2"
    id("org.openapi.generator") version "6.3.0"
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

    // Data Generation
    implementation("net.datafaker:datafaker:2.0.1")

    // Configuration
    implementation("com.typesafe:config:1.4.2")

    // JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.2")

    testImplementation("ch.qos.logback:logback-classic:1.4.11")
    testImplementation("org.slf4j:slf4j-api:2.0.9")
    testImplementation("org.codehaus.janino:janino:3.1.10")

    // OpenAPI Generator
    implementation("org.openapitools:openapi-generator:6.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.14.0")
    implementation("com.squareup.moshi:moshi-adapters:1.14.0")
    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.1")
}

// OpenAPI Generator configuration
openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/src/main/resources/petstore-openapi.json")
    outputDir.set("$buildDir/generated")
    apiPackage.set("com.webqa.api")
    modelPackage.set("com.webqa.model")
}

tasks.test {
    useTestNG {
        useDefaultListeners = true
    }
    systemProperty("allure.results.directory", "build/allure-results")

    testLogging {
        events("passed", "skipped", "failed")
    }
}


// Add generated sources to the main source set
sourceSets {
    main {
        kotlin {
            srcDir("$buildDir/generated/src/main/kotlin")
        }
    }
    test {
        kotlin {
            srcDir("src/test/kotlin")
        }
    }
}


allure {
    version = "2.22.2"
}

// Ensure OpenAPI classes are generated before compiling
tasks.withType<KotlinCompile> {
    dependsOn(tasks.openApiGenerate)
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.register<Delete>("cleanOpenApiGenerated") {
    delete("$buildDir/generated")
}
// ./gradlew openApiGenerate
