package dev.w0fv1.norm.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class NullableTypeTest {
  @Test
  void normalizesAndDisplaysNullableTypes() {
    SemanticType nullable = SemanticType.STRING.nullable();

    assertEquals("String?", nullable.displayName());
    assertEquals(nullable, nullable.nullable());
    assertEquals(SemanticType.STRING, nullable.nonNullable());
    assertTrue(nullable.mayContainNull());
    assertFalse(SemanticType.STRING.mayContainNull());
  }

  @Test
  void appliesNullableAssignmentRelations() {
    assertTrue(TypeRelations.isAssignable(SemanticType.STRING.nullable(), SemanticType.STRING));
    assertTrue(
        TypeRelations.isAssignable(SemanticType.STRING.nullable(), SemanticType.STRING.nullable()));
    assertTrue(TypeRelations.isAssignable(SemanticType.STRING.nullable(), SemanticType.NULL));
    assertFalse(TypeRelations.isAssignable(SemanticType.STRING, SemanticType.STRING.nullable()));
    assertFalse(TypeRelations.isAssignable(SemanticType.STRING, SemanticType.NULL));
  }

  @Test
  void preservesAndAddsNullabilityDuringGenericSubstitution() {
    SemanticType parameter = SemanticType.parameter("test/T", "T");

    assertEquals(
        SemanticType.STRING.nullable(),
        parameter.substitute(Map.of("test/T", SemanticType.STRING.nullable())));
    assertEquals(
        SemanticType.STRING.nullable(),
        parameter.nullable().substitute(Map.of("test/T", SemanticType.STRING)));
    assertEquals(
        SemanticType.STRING.nullable(),
        parameter.nullable().substitute(Map.of("test/T", SemanticType.STRING.nullable())));
  }

  @Test
  void rejectsNullableReferenceTypes() {
    assertThrows(
        IllegalStateException.class, () -> SemanticType.reference(SemanticType.INTEGER).nullable());
  }
}
