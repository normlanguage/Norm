package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class JavaGenericSignatureParserTest {
  private final JavaGenericSignatureParser parser = new JavaGenericSignatureParser();

  @Test
  void parsesClassTypeParametersBoundsAndInterfaces() {
    JavaClassSignature signature =
        parser.parseClass(
            "<K:Ljava/lang/Object;V::Ljava/lang/Comparable<TV;>;>"
                + "Ljava/lang/Object;Ljava/io/Serializable;");

    assertEquals(2, signature.typeParameters().size());
    assertEquals("K", signature.typeParameters().get(0).name());
    assertEquals(
        "java.lang.Object",
        ((JavaClassTypeSignature) signature.typeParameters().get(0).classBound().orElseThrow())
            .binaryName());
    JavaTypeParameter value = signature.typeParameters().get(1);
    assertTrue(value.classBound().isEmpty());
    JavaClassTypeSignature comparable = (JavaClassTypeSignature) value.interfaceBounds().getFirst();
    assertEquals("java.lang.Comparable", comparable.binaryName());
    assertEquals(
        JavaTypeVariance.EXACT, comparable.segments().getFirst().arguments().getFirst().variance());
    assertEquals(
        "V",
        ((JavaTypeVariableSignature)
                comparable.segments().getFirst().arguments().getFirst().type().orElseThrow())
            .name());
    assertEquals("java.lang.Object", signature.superclass().orElseThrow().binaryName());
    assertEquals("java.io.Serializable", signature.interfaces().getFirst().binaryName());
  }

  @Test
  void parsesMethodWildcardsArraysAndDeclaredExceptions() {
    JavaMethodSignature signature =
        parser.parseMethod(
            "<T:Ljava/lang/Object;>(Ljava/util/List<+TT;>;[TT;)"
                + "Ljava/util/List<-TT;>;^Ljava/io/IOException;");

    assertEquals("T", signature.typeParameters().getFirst().name());
    JavaClassTypeSignature input = (JavaClassTypeSignature) signature.parameters().getFirst();
    assertEquals(
        JavaTypeVariance.EXTENDS, input.segments().getFirst().arguments().getFirst().variance());
    assertEquals(
        "T",
        ((JavaTypeVariableSignature)
                ((JavaArrayTypeSignature) signature.parameters().get(1)).component())
            .name());
    JavaClassTypeSignature output = (JavaClassTypeSignature) signature.returnType();
    assertEquals(
        JavaTypeVariance.SUPER, output.segments().getFirst().arguments().getFirst().variance());
    assertEquals(
        "java.io.IOException",
        ((JavaClassTypeSignature) signature.exceptions().getFirst()).binaryName());
  }

  @Test
  void parsesNestedParameterizedTypesAndUnboundedProjection() {
    JavaTypeSignature signature =
        parser.parseType("Ljava/util/Map<Ljava/lang/String;*>.Entry<Ljava/lang/Integer;>;");

    JavaClassTypeSignature entry = (JavaClassTypeSignature) signature;
    assertEquals("java.util.Map$Entry", entry.binaryName());
    assertEquals(2, entry.segments().size());
    assertEquals(
        JavaTypeVariance.UNBOUNDED, entry.segments().getFirst().arguments().get(1).variance());
    assertTrue(entry.segments().getFirst().arguments().get(1).type().isEmpty());
    assertEquals(
        "java.lang.Integer",
        ((JavaClassTypeSignature)
                entry.segments().get(1).arguments().getFirst().type().orElseThrow())
            .binaryName());
  }

  @Test
  void parsesErasedFieldAndMethodDescriptors() {
    assertEquals(
        "java.lang.String",
        ((JavaClassTypeSignature) parser.parseType("Ljava/lang/String;")).binaryName());
    JavaMethodSignature method = parser.parseMethod("(Ljava/lang/String;I)[Ljava/lang/String;");
    assertEquals(2, method.parameters().size());
    assertTrue(method.returnType() instanceof JavaArrayTypeSignature);
  }
}
