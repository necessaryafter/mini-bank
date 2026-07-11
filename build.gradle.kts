import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.plugin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
}

val javaVersion = libs.versions.java.get()

allprojects {
    group = "com.carbonbank"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.spring.dependency-management")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaVersion.toInt())
        }
    }

    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
            jvmTarget = JvmTarget.fromTarget(javaVersion)
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    // Service modules apply the Spring Boot plugin themselves; point bootRun at the
    // repo root so Spring Boot's Docker Compose support finds the shared compose.yaml.
    plugins.withId("org.springframework.boot") {
        tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
            workingDir = rootProject.projectDir
        }
    }
}
