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
