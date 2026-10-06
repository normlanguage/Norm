package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaScalarConformancesTest {
  @TempDir Path cache;

  @Test
  void derivesCanonicalScalarInterfacesFromActualJavaSignatures() throws Exception {
    try (var archive = JdkModuleArchive.open(cache, "java.base")) {
      var schema =
          new JarApiScanner()
              .scan(
                  new JavaApiScanInput(archive.graph(), List.of()),
                  List.of("java.lang.String", "java.lang.Integer", "java.lang.Short"),
                  true);
      var owners =
          schema.allTypes().stream()
              .filter(type -> type.kind() == JavaApiTypeKind.INTERFACE)
              .collect(
                  Collectors.toMap(
                      JavaApiType::binaryName,
                      type ->
                          new JarBindingClassReference.Nominal(
                              new ModuleCoordinate("java.base", 1),
                              "java.base."
                                  + type.binaryName()
                                      .substring(5, type.binaryName().lastIndexOf('.')),
                              type.binaryName()
                                  .substring(type.binaryName().lastIndexOf('.') + 1))));
      var relations = JavaScalarConformances.derive(schema, owners);
      assertTrue(
          relations.stream()
              .anyMatch(
                  value ->
                      value.concreteType().equals(SemanticType.STRING)
                          && value
                              .interfaceType()
                              .identity()
                              .equals("java.base.lang.CharSequence")));
      assertTrue(
          relations.stream()
              .anyMatch(
                  value ->
                      value.concreteType().equals(SemanticType.INTEGER)
                          && value.interfaceType().identity().equals("java.base.lang.Comparable")
                          && value
                              .interfaceType()
                              .arguments()
                              .equals(List.of(SemanticType.INTEGER))));
      assertFalse(
          relations.stream()
              .anyMatch(
                  value ->
                      value.concreteType().equals(SemanticType.INTEGER)
                          && value.interfaceType().arguments().stream()
                              .anyMatch(argument -> argument.identity().contains("Short"))));
    }
  }
}
