package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreVisibility;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class JavaApplicationTypeNameTest {
  @Test
  void mapsTheDefaultNormPackageToAValidHostPackage() {
    CoreNominalTypeKey nominal =
        new CoreNominalTypeKey(
            ModuleCoordinate.localApplication(),
            "",
            "Controller",
            CoreVisibility.PUBLIC,
            Optional.empty());

    assertEquals(
        "norm.generated.application.Controller", JavaApplicationTypeName.binaryName(nominal));
  }

  @Test
  void preservesExplicitNormPackages() {
    CoreNominalTypeKey nominal =
        new CoreNominalTypeKey(
            new ModuleCoordinate("hello.web", 0),
            "hello.web",
            "Controller",
            CoreVisibility.PUBLIC,
            Optional.empty());

    assertEquals("hello.web.Controller", JavaApplicationTypeName.binaryName(nominal));
  }
}
