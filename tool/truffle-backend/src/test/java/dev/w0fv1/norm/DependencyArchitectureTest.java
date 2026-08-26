package dev.w0fv1.norm;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class DependencyArchitectureTest {
  private static JavaClasses aggregates;

  @BeforeAll
  static void importProductionClasses() {
    aggregates =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("dev.w0fv1.norm");
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
  void executionApiDoesNotDependOnCompilerInternals() {
    noClasses()
        .that()
        .resideInAPackage("..execution..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..bound..", "..frontend..", "..semantic..", "..syntax..", "..truffle..")
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
