package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CoreVerifierBoundaryTest {
  @Test
  void callableStateIsNotSharedByProgramVerification() {
    assertTrue(
        Arrays.stream(CoreProgramVerifier.class.getDeclaredFields())
            .noneMatch(
                field ->
                    field.getType() == CoreReferenceFlow.class
                        || java.util.Deque.class.isAssignableFrom(field.getType())));
    assertTrue(
        Arrays.stream(CoreCallableVerifier.class.getDeclaredFields())
            .anyMatch(field -> field.getType() == CoreReferenceFlow.class));
    assertTrue(
        Arrays.stream(CoreCallableVerifier.class.getDeclaredFields())
            .filter(
                field ->
                    field.getType() == CoreReferenceFlow.class
                        || java.util.Deque.class.isAssignableFrom(field.getType()))
            .allMatch(
                field ->
                    Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())));
  }

  @Test
  void sharedTypeAndIntrinsicValidationHasNoCallableState() {
    for (Class<?> type : List.of(CoreVerificationTypes.class, CoreIntrinsicVerifier.class)) {
      for (var field : type.getDeclaredFields()) {
        assertTrue(Modifier.isPrivate(field.getModifiers()), field.toString());
        assertTrue(Modifier.isFinal(field.getModifiers()), field.toString());
        assertNotEquals(CoreReferenceFlow.class, field.getType());
        assertFalse(java.util.Deque.class.isAssignableFrom(field.getType()));
      }
    }
  }
}
