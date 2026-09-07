package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JavaTypeProjectorTest {
  private final JavaGenericSignatureParser signatures = new JavaGenericSignatureParser();

  @Test
  void descriptorAndGenericSignaturesShareProjectionRules() {
    JavaTypeProjector projector =
        new JavaTypeProjector(Map.of("sample.Mode", JavaReferenceKind.ENUM), Map.of());
    assertEquals(
        new JavaArrayType(new JavaArrayType(JavaPrimitiveType.INT)),
        projector
            .project(signatures.parseType("[[I"), Map.of(), JavaTypeProjector.Position.VALUE)
            .orElseThrow());
    assertEquals(
        JavaReferenceKind.ENUM,
        ((JavaReferenceType)
                projector
                    .project(
                        signatures.parseType("Lsample/Mode;"),
                        Map.of(),
                        JavaTypeProjector.Position.VALUE)
                    .orElseThrow())
            .kind());
    assertTrue(
        projector
            .project(
                signatures.parseType("Ljava/util/List<+Ljava/lang/String;>;"),
                Map.of(),
                JavaTypeProjector.Position.VALUE)
            .isEmpty());
    JavaReferenceType token =
        (JavaReferenceType)
            projector
                .project(
                    signatures.parseType("Ljava/lang/Class<+Ljava/lang/Number;>;"),
                    Map.of(),
                    JavaTypeProjector.Position.VALUE)
                .orElseThrow();
    assertEquals(List.of(JavaBindingTypeArgument.unbounded()), token.arguments());
  }

  @Test
  void onlyParameterPositionProjectsFunctionalInterfaces() {
    JavaTypeProjector projector =
        new JavaTypeProjector(
            Map.of("sample.Mapper", JavaReferenceKind.OPAQUE),
            Map.of(
                "sample.Mapper",
                new JavaTypeProjector.FunctionalInterface(
                    signatures
                        .parseClass("<T:Ljava/lang/Object;>Ljava/lang/Object;")
                        .typeParameters(),
                    "apply",
                    signatures.parseMethod("(TT;)TT;"))));
    JavaTypeSignature type = signatures.parseType("Lsample/Mapper<Ljava/lang/String;>;");
    assertInstanceOf(
        JavaReferenceType.class,
        projector.project(type, Map.of(), JavaTypeProjector.Position.VALUE).orElseThrow());
    JavaCallbackType callback =
        (JavaCallbackType)
            projector.project(type, Map.of(), JavaTypeProjector.Position.PARAMETER).orElseThrow();
    assertEquals(
        List.of(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)),
        callback.parameters());
    assertEquals(callback.parameters().getFirst(), callback.returnType());
    assertInstanceOf(
        JavaReferenceType.class,
        ((JavaArrayType)
                projector
                    .project(
                        new JavaArrayTypeSignature(type),
                        Map.of(),
                        JavaTypeProjector.Position.PARAMETER)
                    .orElseThrow())
            .component());
  }
}
