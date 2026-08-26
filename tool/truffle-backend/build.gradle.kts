description = "Norm Truffle execution backend and language adapter"

dependencies {
    api(project(":core"))
    api(project(":execution-api"))
    implementation(project(":project-system"))
    implementation(libs.truffle.api)
    implementation(libs.polyglot)
    runtimeOnly(libs.truffle.runtime)
    annotationProcessor(libs.truffle.dsl.processor)
    testImplementation(libs.archunit)
    testImplementation(project(":project-system"))
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("norm/tests"))
    }
}
