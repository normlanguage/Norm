description = "Norm compiler and execution core"

dependencies {
    implementation(libs.truffle.api)
    implementation(libs.polyglot)
    runtimeOnly(libs.truffle.runtime)
    annotationProcessor(libs.truffle.dsl.processor)
}

val standardLibraryDirectory = rootProject.file("norm/stdlib")

sourceSets {
    main {
        resources.srcDir(standardLibraryDirectory)
    }
    test {
        resources.srcDir(rootProject.file("norm/tests"))
    }
}
