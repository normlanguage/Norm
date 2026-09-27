plugins {
    `java-gradle-plugin`
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

layout.buildDirectory = layout.projectDirectory.dir("../../build/build-logic")

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

dependencies {
    implementation(libs.gson)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat(libs.versions.google.java.format.get())
        formatAnnotations()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

gradlePlugin {
    plugins {
        create("normCompiler") {
            id = "norm.compiler"
            implementationClass = "dev.w0fv1.norm.gradle.NormCompilerPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
    systemProperty("norm.test.abi", file("../../cli/compiler/stdlib-abi.json").absolutePath)
}
