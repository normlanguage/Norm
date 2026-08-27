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

final class PackageDependencyArchitectureTest {
  private static JavaClasses productionClasses;

  @BeforeAll
  static void importCoreProductionClasses() throws URISyntaxException {
    Path classes =
        Path.of(SemanticModel.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    productionClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPath(classes);
  }

  @Test
  void semanticModelDoesNotDependOnCoreOrBuiltinCatalogs() {
    noClasses()
        .that()
        .resideInAPackage("..semantic..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..core..", "..builtin..")
        .check(productionClasses);
  }

  @Test
  void builtinCatalogDoesNotDependOnCoreOrFrontend() {
    noClasses()
        .that()
        .resideInAPackage("..builtin..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..core..", "..frontend..")
        .check(productionClasses);
  }
}
