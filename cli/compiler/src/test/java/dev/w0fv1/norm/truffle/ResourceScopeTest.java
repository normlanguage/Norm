package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ResourceScopeTest {
  @Test
  void closesRemainingResourcesOnceInReverseRegistrationOrder() {
    List<String> closed = new ArrayList<>();
    ResourceScope scope = new ResourceScope();
    ManagedResource first = scope.register("first", () -> closed.add("first"));
    scope.register("second", () -> closed.add("second"));

    first.close();
    first.close();
    scope.close();
    scope.close();

    assertEquals(List.of("first", "second"), closed);
  }

  @Test
  void retainsLaterCloseFailuresAsSuppressedFailures() {
    ResourceScope scope = new ResourceScope();
    IllegalStateException first = new IllegalStateException("first");
    IllegalArgumentException second = new IllegalArgumentException("second");
    scope.register(
        "first",
        () -> {
          throw first;
        });
    scope.register(
        "second",
        () -> {
          throw second;
        });

    ResourceCloseException failure = assertThrows(ResourceCloseException.class, scope::close);

    assertSame(second, failure.getCause());
    assertEquals(1, failure.getSuppressed().length);
    assertSame(first, failure.getSuppressed()[0].getCause());
  }

  @Test
  void preservesTheFirstExplicitCloseFailure() {
    ResourceScope scope = new ResourceScope();
    ManagedResource resource =
        scope.register(
            "resource",
            () -> {
              throw new IllegalStateException("close");
            });

    ResourceCloseException first = assertThrows(ResourceCloseException.class, resource::close);
    ResourceCloseException second = assertThrows(ResourceCloseException.class, resource::close);

    assertSame(first, second);
  }
}
