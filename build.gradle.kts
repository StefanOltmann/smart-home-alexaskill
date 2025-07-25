plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.git.versioning)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktorfit)
    alias(libs.plugins.mokkery)
}

description = "Stefans Smart Home Alexa Skill"
group = "de.stefan_oltmann.smarthome.alexaskill"
version = "1.0.0"

gitVersioning.apply {

    refs {
        tag("(?<version>.*)") {
            version = "\${ref.version}"
        }
    }

    rev {
        version = "\${commit.short}"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.jar {
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
    // The module-info is duplicated, and we don't need it.
    exclude("META-INF/versions/9/module-info.class")
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {

    // Kotlin
    implementation(libs.kotlin.stdlib.jdk8)

    // AWS Lambda Framework
    implementation(libs.aws.lambda.java.core)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktorfit.lib)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Unit Tests
    testImplementation(libs.junit.jupiter)

}
