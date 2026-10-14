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
    packageName.set("com.webqa.petstore")
}

// Generated sources are compiled with the main source set. Taken from the task's output, so compilation
// depends on the generator without an explicit dependsOn
sourceSets {
    main {
        kotlin {
            srcDir(tasks.openApiGenerate.map { "${it.outputDir.get()}/src/main/kotlin" })
        }
    }
}

// Compiles and runs tests on JDK 17 whatever JDK runs Gradle; also sets jvmTarget to 17
kotlin {
    jvmToolchain(17)
}

// Load .env file into environment for test tasks
val dotEnv = rootProject.file(".env")
    .takeIf { it.exists() }
    ?.readLines()
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() && !it.startsWith("#") && "=" in it }
    ?.associate { line ->
        val (key, raw) = line.split("=", limit = 2).map { it.trim() }
        val quoted = raw.length >= 2 && raw.first() in "\"'" && raw.last() == raw.first()
        // Quoted values are taken as is; unquoted ones drop an inline " # comment", as dotenv tools do
        key to if (quoted) raw.substring(1, raw.length - 1) else raw.substringBefore(" #").trim()
    }
    ?: emptyMap()

// Allure reads categories.json from the results directory: it sorts failures into stand outages,
// product defects and broken tests (config/allure/categories.json)
val allureCategories by tasks.registering(Copy::class) {
    from("config/allure/categories.json")
    into(layout.buildDirectory.dir("allure-results"))
}

tasks.test {
    dependsOn(allureCategories)
    useTestNG {
        useDefaultListeners = true
        if (project.hasProperty("suiteFile")) {
            // A missing suite file otherwise runs zero tests and the build stays green
            val suite = file(project.property("suiteFile").toString())
            require(suite.isFile) { "TestNG suite file not found: $suite" }
            suites(suite)
        } else {
            // A plain "gradlew test" or --tests filter runs classes directly: keep the stock-buying test out.
            // Only regression.xml runs it
            excludeGroups("destructive")
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
