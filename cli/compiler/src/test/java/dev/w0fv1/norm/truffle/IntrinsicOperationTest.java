package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

final class IntrinsicOperationTest {
  @Test
  void boundPlatformOperationsDoNotRetainRuntimeOpcodeSelection() throws Exception {
    for (var intrinsic : IntrinsicDispatcher.supportedIntrinsics()) {
      if (java.util.stream.Stream.of("HTTP_", "FILE_", "IO_", "TIME_", "JAR_TASK_")
          .noneMatch(prefix -> intrinsic.name().startsWith(prefix))) continue;
      var pending = new java.util.ArrayDeque<IntrinsicOperation>();
      var visited =
          java.util.Collections.newSetFromMap(
              new java.util.IdentityHashMap<IntrinsicOperation, Boolean>());
      pending.add(IntrinsicDispatcher.resolve(intrinsic));
      while (!pending.isEmpty()) {
        var operation = pending.removeFirst();
        if (!visited.add(operation)) continue;
        for (var field : operation.getClass().getDeclaredFields()) {
          org.junit.jupiter.api.Assertions.assertNotEquals(
              dev.w0fv1.norm.abi.IntrinsicId.class, field.getType(), intrinsic.name());
          if (IntrinsicOperation.class.isAssignableFrom(field.getType())) {
            field.setAccessible(true);
            pending.add((IntrinsicOperation) field.get(operation));
          }
        }
      }
    }
  }

  @Test
  void bindsEverySupportedIntrinsicWithoutExecutingIt() {
    for (var intrinsic : IntrinsicDispatcher.supportedIntrinsics()) {
      assertNotNull(IntrinsicDispatcher.resolve(intrinsic), intrinsic.name());
      assertEquals(
          new RuntimeValues.DispatchTarget.Intrinsic(intrinsic),
          new RuntimeValues.DispatchTarget.Intrinsic(intrinsic));
    }
  }
}
