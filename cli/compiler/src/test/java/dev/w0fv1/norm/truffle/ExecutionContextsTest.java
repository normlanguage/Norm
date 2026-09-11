package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.w0fv1.norm.core.CoreType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

final class ExecutionContextsTest {
  @Test
  @Timeout(5)
  void isolatesExecutionsAndVirtualThreadsAndRestoresAfterFailure() throws Exception {
    var contexts = new ExecutionContexts();
    var separate = new ExecutionContexts();
    var observed = new CompletableFuture<Object>();
    contexts.with(
        CoreType.STRING,
        "outer",
        () -> {
          assertSame(RuntimeValues.NullValue.INSTANCE, separate.get(CoreType.STRING));
          var failure =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      contexts.with(
                          CoreType.STRING,
                          "inner",
                          () -> {
                            assertEquals("inner", contexts.get(CoreType.STRING));
                            throw new IllegalStateException("expected");
                          }));
          assertEquals("expected", failure.getMessage());
          assertEquals("outer", contexts.get(CoreType.STRING));
          Thread.ofVirtual().start(() -> observed.complete(contexts.get(CoreType.STRING)));
          return null;
        });
    assertSame(RuntimeValues.NullValue.INSTANCE, observed.get(2, TimeUnit.SECONDS));
    assertSame(RuntimeValues.NullValue.INSTANCE, contexts.get(CoreType.STRING));
  }
}
