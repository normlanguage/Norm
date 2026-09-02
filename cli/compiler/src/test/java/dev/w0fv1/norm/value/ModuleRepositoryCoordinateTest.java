package dev.w0fv1.norm.value;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ModuleRepositoryCoordinateTest {
  @Test
  void derivesRepositoryIdentityFromTheModuleName() {
    ModuleRepositoryCoordinate coordinate =
        ModuleRepositoryCoordinate.from(new ModuleCoordinate("commons.lang", 1));

    assertEquals("commons", coordinate.group());
    assertEquals("lang", coordinate.artifact());
    assertEquals("1", coordinate.version());
    assertEquals("commons:lang:1", coordinate.notation());
  }

  @Test
  void preservesAQualifiedNamespace() {
    ModuleRepositoryCoordinate coordinate =
        ModuleRepositoryCoordinate.from(new ModuleCoordinate("apache.commons.lang", 2));

    assertEquals("apache.commons", coordinate.group());
    assertEquals("lang", coordinate.artifact());
  }

  @Test
  void publishesATopLevelModuleWithoutChangingItsModuleIdentity() {
    ModuleRepositoryCoordinate coordinate =
        ModuleRepositoryCoordinate.from(new ModuleCoordinate("orm", 1));

    assertEquals("orm", coordinate.group());
    assertEquals("orm", coordinate.artifact());
    assertEquals("orm:orm:1", coordinate.notation());
  }
}
