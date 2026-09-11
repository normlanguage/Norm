package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

final class JavaAnnotationContractTest {
  @Test
  void projectsImplementationProvidersFromIntroductionMetadata() {
    for (String metadata : List.of("io.micronaut.aop.Introduction", "sample.Introduction")) {
      var type =
          new JavaApiType(
              "sample.GeneratedService",
              JavaApiTypeKind.ANNOTATION,
              Opcodes.ACC_PUBLIC
                  | Opcodes.ACC_INTERFACE
                  | Opcodes.ACC_ABSTRACT
                  | Opcodes.ACC_ANNOTATION,
              new JavaClassSignature(
                  List.of(),
                  Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                  List.of(JavaClassTypeSignature.raw("java.lang.annotation.Annotation"))),
              List.of(new JavaApiAnnotation(metadata, true, List.of())),
              List.of(),
              Optional.empty(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              JavaApiDisposition.BINDABLE);
      String source =
          new JarBindingSourceGenerator()
              .generate(
                  new dev.w0fv1.norm.value.ModuleCoordinate("services", 1),
                  List.of("GeneratedService"),
                  dev.w0fv1.norm.value.Sha256Digest.parse("0123456789abcdef".repeat(4)),
                  new JarApiSchema(List.of(type)))
              .sources()
              .getFirst()
              .text();
      if (metadata.equals("io.micronaut.aop.Introduction")) {
        assertTrue(source.contains("import std.annotation.ManagedImplementation"));
      } else {
        assertFalse(source.contains("ManagedImplementation"));
      }
    }
  }

  @Test
  void projectsJpaIdentityWithoutMatchingShortNames() {
    for (String name :
        List.of(
            "jakarta.persistence.Id",
            "jakarta.persistence.EmbeddedId",
            "sample.Id",
            "sample.EmbeddedId")) {
      var type =
          new JavaApiType(
              name,
              JavaApiTypeKind.ANNOTATION,
              Opcodes.ACC_PUBLIC
                  | Opcodes.ACC_INTERFACE
                  | Opcodes.ACC_ABSTRACT
                  | Opcodes.ACC_ANNOTATION,
              new JavaClassSignature(
                  List.of(),
                  Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                  List.of(JavaClassTypeSignature.raw("java.lang.annotation.Annotation"))),
              List.of(),
              List.of(),
              Optional.empty(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              JavaApiDisposition.BINDABLE);
      if (name.startsWith("jakarta.persistence.")) {
        assertTrue(JavaAnnotationContract.from(type).identityField());
      } else {
        assertFalse(JavaAnnotationContract.from(type).identityField());
      }
    }
  }

  @Test
  void importsJakartaFieldInitializationWithoutMatchingShortNames() {
    for (String name : List.of("jakarta.inject.Inject", "sample.Inject")) {
      var type =
          new JavaApiType(
              name,
              JavaApiTypeKind.ANNOTATION,
              Opcodes.ACC_PUBLIC
                  | Opcodes.ACC_INTERFACE
                  | Opcodes.ACC_ABSTRACT
                  | Opcodes.ACC_ANNOTATION,
              new JavaClassSignature(
                  List.of(),
                  Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                  List.of(JavaClassTypeSignature.raw("java.lang.annotation.Annotation"))),
              List.of(),
              List.of(),
              Optional.empty(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              List.of(),
              JavaApiDisposition.BINDABLE);
      if (name.equals("jakarta.inject.Inject")) {
        assertTrue(JavaAnnotationContract.from(type).managedFields());
      } else {
        assertFalse(JavaAnnotationContract.from(type).managedFields());
      }
    }
  }
}
