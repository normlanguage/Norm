description = "Norm Truffle execution backend and language adapter"

dependencies {
    api(project(":core"))
    api(project(":execution-api"))
    implementation(libs.truffle.api)
    implementation(libs.polyglot)
    runtimeOnly(libs.truffle.runtime)
    annotationProcessor(libs.truffle.dsl.processor)
    testImplementation(libs.archunit)
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("norm/tests"))
    }
}
