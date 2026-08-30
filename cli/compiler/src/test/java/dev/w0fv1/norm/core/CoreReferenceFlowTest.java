package dev.w0fv1.norm.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.value.LexicalLifetime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CoreReferenceFlowTest {
  @Test
  void overlaysExplicitFinallyWritesEvenWhenTheyRestoreTheIncomingLifetime() {
    LexicalLifetime incoming = LexicalLifetime.longLived();
    LexicalLifetime tried = LexicalLifetime.unusable();
    CoreReferenceFlow.State normal = new CoreReferenceFlow.State(Map.of(0, tried));
    CoreReferenceFlow.State finalFlow = new CoreReferenceFlow.State(Map.of(0, incoming));

    CoreReferenceFlow.State result = CoreReferenceFlow.overlay(normal, finalFlow, Set.of(0));

    assertEquals(incoming, result.referenceLifetimes().get(0));
  }
}
