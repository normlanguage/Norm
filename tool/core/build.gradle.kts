description = "Norm compiler and canonical core"

val standardLibraryDirectory = rootProject.file("norm/stdlib")
val generatedBuildMetadata = layout.buildDirectory.dir("generated/sources/build-metadata")
val generateBuildMetadata = tasks.register<Copy>("generateBuildMetadata") {
    inputs.property("normVersion", project.version)
    from(layout.projectDirectory.dir("src/main/templates"))
    into(generatedBuildMetadata)
    expand("normVersion" to project.version.toString())
}

sourceSets {
    main {
        java.srcDir(generatedBuildMetadata)
        resources.srcDir(standardLibraryDirectory)
    }
    test {
        resources.srcDir(rootProject.file("norm/tests"))
    }
}

tasks.compileJava {
    dependsOn(generateBuildMetadata)
}
