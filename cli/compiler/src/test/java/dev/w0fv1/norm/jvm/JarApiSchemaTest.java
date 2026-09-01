package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

final class JarApiSchemaTest {
  @Test
  void apiIdentityNormalizesTypeParameterNames() {
    JarApiSchema first = new JarApiSchema(List.of(genericType("T", "java.lang.Object")));
    JarApiSchema renamed = new JarApiSchema(List.of(genericType("Element", "java.lang.Object")));
    JarApiSchema rebound = new JarApiSchema(List.of(genericType("T", "java.lang.Number")));

    assertEquals(first.apiId(), renamed.apiId());
    assertNotEquals(first.apiId(), rebound.apiId());
  }

  private static JavaApiType genericType(String parameterName, String bound) {
    JavaTypeParameter parameter =
        new JavaTypeParameter(
            parameterName, Optional.of(JavaClassTypeSignature.raw(bound)), List.of());
    JavaApiField field =
        new JavaApiField(
            "sample.Box",
            "value",
            "Ljava/lang/Object;",
            new JavaTypeVariableSignature(parameterName),
            Opcodes.ACC_PUBLIC,
            Optional.empty(),
            List.of(),
            List.of(),
            JavaApiDisposition.UNSUPPORTED,
            Optional.of(
                new JavaApiIssue(
                    JavaApiIssueCode.GENERIC_MAPPING, "Java generic mapping is not implemented")),
            List.of());
    return new JavaApiType(
        "sample.Box",
        JavaApiTypeKind.CLASS,
        Opcodes.ACC_PUBLIC,
        new JavaClassSignature(
            List.of(parameter),
            Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
            List.of()),
        List.of(),
        List.of(),
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(field),
        List.of(),
        JavaApiDisposition.BINDABLE);
  }
}
