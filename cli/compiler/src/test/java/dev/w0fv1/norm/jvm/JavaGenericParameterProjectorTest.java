package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class JavaGenericParameterProjectorTest {
  @Test
  void projectsRepresentableSelfReferentialInterfaceBounds() {
    JavaClassSignature signature =
        new JavaGenericSignatureParser()
            .parseClass("<T::Lsample/Lifecycle<TT;>;>Ljava/lang/Object;");

    JavaGenericParameterProjector.Projection projection =
        JavaGenericParameterProjector.project(
                signature.typeParameters(),
                Map.of(),
                (bound, variables) ->
                    new JavaReferenceType(
                        "sample.Lifecycle",
                        JavaReferenceKind.OPAQUE,
                        List.of(JavaBindingTypeArgument.exact(variables.get("T")))))
            .orElseThrow();

    JavaBindingTypeVariable variable = projection.variables().get("T");
    assertEquals(
        new JavaReferenceType(
            "sample.Lifecycle",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(variable))),
        projection.parameters().getFirst().bound().orElseThrow());
  }

  @Test
  void omitsAPlatformValueMappingThatIsNotANormNominalBound() {
    JavaClassSignature signature =
        new JavaGenericSignatureParser()
            .parseClass("<K::Ljava/lang/CharSequence;>Ljava/lang/Object;");

    JavaGenericParameterProjector.Projection projection =
        JavaGenericParameterProjector.project(
                signature.typeParameters(),
                Map.of(),
                (bound, variables) ->
                    new JavaReferenceType(
                        "java.lang.CharSequence", JavaReferenceKind.CHAR_SEQUENCE))
            .orElseThrow();

    assertEquals(java.util.Optional.empty(), projection.parameters().getFirst().bound());
  }
}
