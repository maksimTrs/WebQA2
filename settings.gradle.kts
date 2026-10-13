plugins {
    // Lets jvmToolchain(17) download JDK 17 on a machine that has only another JDK
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "WebQA"
