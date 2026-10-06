package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.objectweb.asm.Opcodes;

final class JavaBaseDomainBindingTest {
  @ParameterizedTest
  @CsvSource({
    "java.lang.CharSequence,0,CharSequence",
    "java.nio.charset.Charset,0,Charset",
    "java.io.InputStream,0,InputStream",
    "java.io.OutputStream,0,OutputStream",
    "java.util.concurrent.Future,1,Future",
    "java.util.concurrent.CompletionStage,1,CompletionStage",
    "java.util.concurrent.CompletableFuture,1,CompletableFuture",
    "java.time.Duration,0,Duration",
    "java.net.URI,0,URI",
    "java.net.URL,0,URL",
    "java.nio.file.Path,0,Path",
    "java.io.File,0,File",
    "java.util.Optional,1,Optional",
    "java.util.OptionalInt,0,OptionalInt",
    "java.util.OptionalLong,0,OptionalLong",
    "java.util.OptionalDouble,0,OptionalDouble",
    "java.lang.Iterable,1,Iterable",
    "java.util.Iterator,1,Iterator",
    "java.util.Collection,1,Collection",
    "java.util.List,1,JavaList",
    "java.util.Set,1,JavaSet",
    "java.util.Map,2,JavaMap",
    "java.lang.Throwable,0,Throwable",
    "java.lang.Exception,0,Exception",
    "java.lang.RuntimeException,0,RuntimeException",
    "java.lang.AutoCloseable,0,AutoCloseable"
  })
  void domainReferencesUseTheirJavaBaseOwner(String binaryName, int arity, String name) {
    var parameterTypes = new ArrayList<JavaBindingTypeArgument>();
    if (arity > 0)
      parameterTypes.add(
          JavaBindingTypeArgument.exact(
              new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)));
    if (arity > 1)
      parameterTypes.add(
          JavaBindingTypeArgument.exact(
              new JavaBoxedType("java.lang.Integer", JavaPrimitiveType.INT)));
    var reference = new JavaReferenceType(binaryName, JavaReferenceKind.OPAQUE, parameterTypes);
    var packageName = "java.base." + binaryName.substring(5, binaryName.lastIndexOf('.'));
    var owner =
        new JarBindingClassReference.Nominal(
            new ModuleCoordinate("java.base", 1), packageName, name);
    var generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("adapter", 1),
                List.of("Api"),
                List.of(new JarBindingType("sample.Api", List.of("echo"))),
                Sha256Digest.parse("0123456789abcdef".repeat(4)),
                schema(reference, arity),
                Map.of(binaryName, owner));
    assertEquals(1, generated.sources().size());
    var source = generated.sources().getFirst().text();
    assertTrue(source.contains("import " + packageName + "." + name + "\n"), source);
    var expected = name + (arity == 0 ? "" : arity == 1 ? "<String>" : "<String, Integer>");
    assertTrue(source.contains(expected + "? apiEcho(" + expected + "? arg0)"), source);
    assertFalse(source.contains("std."), source);
    assertFalse(source.contains("ownResourceInContext"), source);
    assertFalse(generated.classDescriptors().containsValue(reference.descriptor()));
  }

  @Test
  void missingJavaBaseOwnerCannotCreateAnotherDeclaration() {
    var reference = new JavaReferenceType("java.time.Duration", JavaReferenceKind.OPAQUE);
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new JarBindingSourceGenerator()
                    .generateSurface(
                        new ModuleCoordinate("adapter", 1),
                        List.of("Api"),
                        List.of(new JarBindingType("sample.Api", List.of("echo"))),
                        Sha256Digest.parse("0123456789abcdef".repeat(4)),
                        schema(reference, 0),
                        Map.of()));
    assertTrue(failure.getMessage().contains("java.base dependency owner"), failure.getMessage());
  }

  private static JarApiSchema schema(JavaReferenceType reference, int arity) {
    var signature =
        new JavaClassSignature(
            List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of());
    var call =
        new JavaBindingCallable(
            "sample.Api",
            "echo",
            "(" + reference.descriptor() + ")" + reference.descriptor(),
            JavaCallableKind.STATIC_METHOD,
            List.of(reference),
            reference);
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
    var local =
        new JavaApiType(
            "sample.Api",
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
    var parameters =
        java.util.stream.IntStream.range(0, arity)
            .mapToObj(index -> new JavaTypeParameter("T" + index, Optional.empty(), List.of()))
            .toList();
    var external =
        new JavaApiType(
            reference.binaryName(),
            JavaApiTypeKind.CLASS,
            Opcodes.ACC_PUBLIC,
            new JavaClassSignature(parameters, signature.superclass(), List.of()),
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            JavaApiDisposition.BINDABLE);
    return new JarApiSchema(List.of(local), List.of(external));
  }
}
