package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.Opcodes;

final class JarBindingImportsTest {
  @Test
  void javaBaseBindingsHaveNoStandardLibraryDependency() {
    var generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("java.base", 1),
                List.of("Host"),
                List.of(new JarBindingType("Host", List.of("accept"))),
                Sha256Digest.parse("0123456789abcdef".repeat(4)),
                schema(),
                Map.of());
    for (var source : generated.sources())
      assertFalse(source.text().contains("std."), source.text());
  }

  @Test
  void referencesJavaBaseDomainTypesThroughTheirPublicOwner() {
    var owner =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("java.base", 1), "java.base.util", "List");
    var schema = schema();
    var node = schema.supportingTypes().getFirst();
    var list =
        new JavaApiType(
            "java.util.List",
            node.kind(),
            node.modifiers(),
            node.signature(),
            node.annotations(),
            node.typeAnnotations(),
            node.enclosingType(),
            node.recordComponents(),
            node.permittedSubclasses(),
            node.fields(),
            node.methods(),
            node.inheritedMethods(),
            node.disposition());
    var host = schema.types().getFirst();
    var call =
        new JavaBindingCallable(
            "sample.Host",
            "accept",
            "(Ljava/util/List;)V",
            JavaCallableKind.STATIC_METHOD,
            List.of(new JavaReferenceType("java.util.List", JavaReferenceKind.OPAQUE)),
            JavaPrimitiveType.VOID);
    var method = host.effectiveMethods().getFirst();
    var accept =
        new JavaApiMethod(
            call.owner(),
            call.name(),
            call.descriptor(),
            new JavaGenericSignatureParser().parseMethod(call.descriptor()),
            method.modifiers(),
            call.kind(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            JavaApiDisposition.BINDABLE,
            Optional.empty(),
            Optional.of(call));
    var local =
        new JavaApiType(
            host.binaryName(),
            host.kind(),
            host.modifiers(),
            host.signature(),
            host.annotations(),
            host.typeAnnotations(),
            host.enclosingType(),
            host.recordComponents(),
            host.permittedSubclasses(),
            host.fields(),
            List.of(accept),
            List.of(),
            host.disposition());
    var generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("host", 1),
                List.of("Host"),
                List.of(new JarBindingType("Host", List.of("accept"))),
                Sha256Digest.parse("0123456789abcdef".repeat(4)),
                new JarApiSchema(List.of(local), List.of(list)),
                Map.of("java.util.List", owner));
    assertEquals(1, generated.sources().size());
    var source = generated.sources().getFirst().text();
    assertTrue(source.contains("import java.base.util.List\n"), source);
    assertFalse(source.contains("std."), source);
    assertFalse(generated.classDescriptors().containsValue("Ljava/util/List;"));
    var wrongOwner =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("collections", 1), "collections", "List");
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new JarBindingSourceGenerator()
                    .generateSurface(
                        new ModuleCoordinate("host", 1),
                        List.of("Host"),
                        List.of(new JarBindingType("Host", List.of("accept"))),
                        Sha256Digest.parse("0123456789abcdef".repeat(4)),
                        new JarApiSchema(List.of(local), List.of(list)),
                        Map.of("java.util.List", wrongOwner)));
    assertTrue(failure.getMessage().contains("java.base"), failure.getMessage());
  }

  @Test
  void javaBaseClassesCannotBeOwnedByAnotherModule() {
    var existing = schema().supportingTypes().getFirst();
    var type =
        new JavaApiType(
            "java.util.ArrayList",
            existing.kind(),
            existing.modifiers(),
            existing.signature(),
            existing.annotations(),
            existing.typeAnnotations(),
            existing.enclosingType(),
            existing.recordComponents(),
            existing.permittedSubclasses(),
            existing.fields(),
            existing.methods(),
            existing.inheritedMethods(),
            existing.disposition());
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new JarBindingSourceGenerator()
                    .generateSurface(
                        new ModuleCoordinate("collections", 1),
                        List.of("ArrayList"),
                        List.of(new JarBindingType("java.util.ArrayList", List.of())),
                        Sha256Digest.parse("0123456789abcdef".repeat(4)),
                        new JarApiSchema(List.of(type)),
                        Map.of()));
    assertTrue(failure.getMessage().contains("java.base"), failure.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Node", "Widget"})
  void referencesTheDependencyDeclarationInsteadOfGeneratingAnotherWrapper(String name) {
    var external =
        new JarBindingClassReference.Nominal(new ModuleCoordinate("widgets", 1), "widgets", name);
    var generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("host", 1),
                List.of("Host"),
                List.of(new JarBindingType("Host", List.of("accept"))),
                Sha256Digest.parse("0123456789abcdef".repeat(4)),
                schema(),
                Map.of("sample.Node", external));
    assertEquals(1, generated.sources().size());
    var source = generated.sources().getFirst().text();
    assertTrue(source.contains("import widgets." + name + "\n"), source);
    assertTrue(source.contains(name + "?"), source);
    assertFalse(generated.classDescriptors().containsValue("Lsample/Node;"));
  }

  @Test
  void keepsAnOpaqueDeclarationWhenThereIsNoPublicDependencyOwner() {
    var generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("host", 1),
                List.of("Host"),
                List.of(new JarBindingType("Host", List.of("accept"))),
                Sha256Digest.parse("0123456789abcdef".repeat(4)),
                schema(),
                Map.of());
    assertEquals(2, generated.sources().size());
    assertTrue(generated.classDescriptors().containsValue("Lsample/Node;"));
  }

  @Test
  void aliasesImportedSuperclassWithSameNameAsExport() {
    var external =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("fx.controls", 2), "fx.controls", "Button");
    var emptySignature =
        new JavaClassSignature(
            List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of());
    var localSignature =
        new JavaClassSignature(
            List.of(),
            Optional.of(JavaClassTypeSignature.raw("javafx.scene.control.Button")),
            List.of());
    var base =
        new JavaApiType(
            "javafx.scene.control.Button",
            JavaApiTypeKind.CLASS,
            Opcodes.ACC_PUBLIC,
            emptySignature,
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            JavaApiDisposition.BINDABLE);
    var wrapper =
        new JavaApiType(
            "sample.Button",
            JavaApiTypeKind.CLASS,
            Opcodes.ACC_PUBLIC,
            localSignature,
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            JavaApiDisposition.BINDABLE);
    var generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("ui.component", 1),
                List.of("Button"),
                List.of(new JarBindingType("sample.Button", List.of())),
                Sha256Digest.parse("0123456789abcdef".repeat(4)),
                new JarApiSchema(List.of(wrapper), List.of(base)),
                Map.of("javafx.scene.control.Button", external));
    var source = generated.sources().getFirst().text();
    assertTrue(source.contains("import fx.controls.Button as ButtonImported1\n"), source);
    assertTrue(source.contains("class Button extends ButtonImported1"), source);
  }

  private static JarApiSchema schema() {
    var node = new JavaReferenceType("sample.Node", JavaReferenceKind.OPAQUE);
    var call =
        new JavaBindingCallable(
            "sample.Host",
            "accept",
            "(Lsample/Node;)V",
            JavaCallableKind.STATIC_METHOD,
            List.of(node),
            JavaPrimitiveType.VOID);
    var method =
        new JavaApiMethod(
            call.owner(),
            call.name(),
            call.descriptor(),
            new JavaGenericSignatureParser().parseMethod(call.descriptor()),
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            call.kind(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            JavaApiDisposition.BINDABLE,
            Optional.empty(),
            Optional.of(call));
    var signature =
        new JavaClassSignature(
            List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of());
    var host =
        new JavaApiType(
            "sample.Host",
            JavaApiTypeKind.CLASS,
            Opcodes.ACC_PUBLIC,
            signature,
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(method),
            List.of(),
            JavaApiDisposition.BINDABLE);
    var dependency =
        new JavaApiType(
            "sample.Node",
            JavaApiTypeKind.CLASS,
            Opcodes.ACC_PUBLIC,
            signature,
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            JavaApiDisposition.BINDABLE);
    return new JarApiSchema(List.of(host), List.of(dependency));
  }
}
