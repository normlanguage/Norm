import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    base
    alias(libs.plugins.spotless)
}

group = "dev.w0fv1.norm"
version = providers.gradleProperty("normVersion").get()

val javaLanguageVersion = libs.versions.java.get()
val googleJavaFormatVersion = libs.versions.google.java.format.get()
val junitBom = libs.junit.bom
val junitJupiter = libs.junit.jupiter
val junitPlatformLauncher = libs.junit.platform.launcher

allprojects {
    apply(plugin = "com.diffplug.spotless")
    group = rootProject.group
    version = rootProject.version

    extensions.configure<SpotlessExtension> {
        java {
            if (project == rootProject)
                target("build-tools/src/**/*.java", "build-maven-plugin/src/**/*.java")
            else target("src/**/*.java")
            googleJavaFormat(googleJavaFormatVersion)
            formatAnnotations()
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(javaLanguageVersion)
        }
        modularity.inferModulePath = true
        withSourcesJar()
    }

    dependencies {
        "testImplementation"(platform(junitBom))
        "testImplementation"(junitJupiter)
        "testRuntimeOnly"(junitPlatformLauncher)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = javaLanguageVersion.toInt()
        options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        maxHeapSize = "1g"
        jvmArgs("--sun-misc-unsafe-memory-access=allow", "--enable-native-access=ALL-UNNAMED")
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }
}

tasks.register("qualityCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs formatting checks, compilation, and all tests."
    dependsOn(tasks.named("spotlessCheck"))
    dependsOn(subprojects.map { it.tasks.named("check") })
}
