package dev.w0fv1.norm;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.w0fv1.norm.semantic.SemanticModel;
import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class DependencyArchitectureTest {
  private static JavaClasses aggregates;

  @BeforeAll
  static void importProductionClasses() throws URISyntaxException {
    Path classes =
        Path.of(SemanticModel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    aggregates =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPath(classes);
  }

  @Test
  void compilerPackagesHaveNoDependencyCycles() {
    com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices()
        .matching("dev.w0fv1.norm.(*)..")
        .should()
        .beFreeOfCycles()
        .check(aggregates);
  }

  @Test
  void sourceModelIsALeaf() {
    noClasses()
        .that()
        .resideInAPackage("..source..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..value..", "..syntax..", "..semantic..", "..core..", "..diagnostic..")
        .check(aggregates);
  }

  @Test
  void abiDoesNotDependOnCompilerModels() {
    noClasses()
        .that()
        .resideInAPackage("..abi..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..builtin..", "..semantic..", "..core..", "..frontend..", "..syntax..")
        .check(aggregates);
  }

  @Test
  void coreDoesNotDependOnBindingOrRuntimeLayers() {
    noClasses()
        .that()
        .resideInAPackage("..core..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..bound..",
            "..execution..",
            "..frontend..",
            "..syntax..",
            "..truffle..",
            "..semantic..",
            "..builtin..")
        .check(aggregates);
  }

  @Test
  void semanticModelDoesNotDependOnCoreOrBuiltinCatalogs() {
    noClasses()
        .that()
        .resideInAPackage("..semantic..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..core..", "..builtin..")
        .check(aggregates);
  }

  @Test
  void builtinCatalogDoesNotDependOnCoreOrFrontend() {
    noClasses()
        .that()
        .resideInAPackage("..builtin..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..core..", "..frontend..")
        .check(aggregates);
  }

  @Test
  void frontendDoesNotDependOnTheTruffleRuntime() {
    noClasses()
        .that()
        .resideInAPackage("..frontend..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..truffle..")
        .check(aggregates);
  }

  @Test
  void boundIrIsConsumedOnlyByTheFrontend() {
    noClasses()
        .that()
        .resideOutsideOfPackages("..bound..", "..frontend..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..bound..")
        .check(aggregates);
  }

  @Test
  void semanticAndBoundLayersDoNotDependOnDownstreamLayers() {
    noClasses()
        .that()
        .resideInAnyPackage("..semantic..", "..bound..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..core..", "..execution..", "..frontend..", "..truffle..")
        .check(aggregates);
  }

  @Test
  void executionContractsDoNotDependOnCompilerInternals() {
    noClasses()
        .that()
        .resideInAPackage("..execution..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..bound..",
            "..frontend..",
            "..platform.jdk..",
            "..project..",
            "..runtime..",
            "..semantic..",
            "..syntax..",
            "..truffle..")
        .check(aggregates);
  }

  @Test
  void platformContractsDoNotDependOnExecutionOrRuntimeImplementations() {
    noClasses()
        .that()
        .resideInAnyPackage(
            "..platform", "..platform.file..", "..platform.http..", "..platform.time..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..execution..", "..platform.jdk..", "..project..", "..runtime..", "..truffle..")
        .check(aggregates);
  }

  @Test
  void projectLifecycleDependsOnContractsInsteadOfRuntimeImplementations() {
    noClasses()
        .that()
        .resideInAPackage("..project..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..platform.jdk..", "..runtime..", "..truffle..")
        .check(aggregates);
  }

  @Test
  void cliAdaptersDoNotDependOnBoundOrTruffleInternals() {
    noClasses()
        .that()
        .resideInAPackage("..cli..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..bound..", "..truffle..")
        .check(aggregates);
  }

  @Test
  void lowererConsumesCoreInsteadOfCompilerInternals() {
    noClasses()
        .that()
        .haveSimpleName("Lowerer")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..bound..", "..frontend..", "..semantic..", "..syntax..")
        .check(aggregates);
  }

  @Test
  void workspaceOwnsProjectAnalysisWithoutDependingOnProtocolAdapters() {
    noClasses()
        .that()
        .resideInAPackage("..workspace..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..cli..", "org.eclipse.lsp4j..")
        .check(aggregates);
  }

  @Test
  void projectInputsDoNotDependOnApplicationOrJvmConsumers() {
    noClasses()
        .that()
        .resideInAPackage("..project..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..application..", "..workspace..", "..cli..", "..polyglot..")
        .check(aggregates);
    noClasses()
        .that()
        .resideInAPackage("..jvm..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..application..", "..project..", "..workspace..")
        .check(aggregates);
  }

  @Test
  void truffleExecutionDoesNotOwnPolyglotOrApplicationCompilation() {
    noClasses()
        .that()
        .resideInAPackage("..truffle..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..polyglot..", "..application..", "..project..")
        .check(aggregates);
  }

  @Test
  void lspDocumentAdapterDoesNotLoadOrCompileProjects() {
    noClasses()
        .that()
        .haveSimpleName("DocumentService")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..project..", "..runtime..", "..application..")
        .check(aggregates);
  }
}
