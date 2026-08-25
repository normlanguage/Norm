package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import org.junit.jupiter.api.Test;

final class BuiltinIntrinsicCoverageTest {
  @Test
  void implementsEveryIntrinsicDeclaredByTheBuiltinCatalog() {
    assertEquals(
        BuiltinCatalog.standard().declaredIntrinsics(), IntrinsicDispatcher.supportedIntrinsics());
  }
}
