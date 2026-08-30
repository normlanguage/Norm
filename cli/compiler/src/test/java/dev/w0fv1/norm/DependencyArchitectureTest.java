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
  void coreDoesNotDependOnBindingOrRuntimeLayers() {
    noClasses()
        .that()
        .resideInAPackage("..core..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..bound..", "..execution..", "..frontend..", "..syntax..", "..truffle..")
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
}
