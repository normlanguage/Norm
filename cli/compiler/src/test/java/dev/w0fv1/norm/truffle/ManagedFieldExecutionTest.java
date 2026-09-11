package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class ManagedFieldExecutionTest {
  @Test
  void keepsOrdinaryAnnotatedFieldsRequired() {
    assertTrue(
        !NormTestKit.compile(
                """
                import std.annotation.FieldTarget
                import std.annotation.RuntimeRetention
                annotation Inject implements FieldTarget, RuntimeRetention {}
                class Page { @Inject() String title }
                Void main() { Page() }
                """)
            .isSuccess());
  }

  @Test
  void preservesGenericDefaultsAndInheritedConstruction() throws Exception {
    assertEquals(
        "page:42" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.annotation.ManagedField
            import std.annotation.RuntimeRetention
            interface ProvidedField extends ManagedField {}
            annotation Provided implements ProvidedField, RuntimeRetention {}
            class Holder<T = String> {
              String title = "page"
              @Provided() private T supplied
              Void attach(T value) { supplied = value }
              T read() { supplied }
            }
            class Page extends Holder<Integer> {}
            Void main() {
              var page = Page()
              page.attach(42)
              printLine(page.title + ":" + page.read().toString())
            }
            """));
  }

  @Test
  void rejectsManagedValueFields() {
    assertTrue(
        NormTestKit.compile(
                """
                import std.annotation.ManagedField
                import std.annotation.RuntimeRetention
                annotation Provided implements ManagedField, RuntimeRetention {}
                value Page { @Provided() String title }
                Void main() {}
                """)
            .diagnostics()
            .stream()
            .anyMatch(diagnostic -> diagnostic.message().equals("managed fields require a class")));
  }

  @Test
  void reportsReadsBeforeInitializationAndSupportsExplicitConstructors() throws Exception {
    assertEquals(
        "initialized" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.annotation.ManagedField
            import std.annotation.RuntimeRetention
            import std.core.Exception
            annotation Provided implements ManagedField, RuntimeRetention {}
            class Page {
              @Provided() private String title
              Page() {}
              String read() { title }
              Void attach(String supplied) { title = supplied }
            }
            Void main() {
              var page = Page()
              Boolean rejected = false
              try { page.read() } catch Exception failure {
                rejected = failure.message == "field has not been initialized"
              }
              require(condition: rejected, message: "uninitialized read must fail")
              page.attach("initialized")
              printLine(page.read())
            }
            """));
  }

  @Test
  void constructsBeforeExternalFieldInitialization() throws Exception {
    assertEquals(
        "page:42" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.annotation.ManagedField
            import std.annotation.RuntimeRetention
            annotation Provided implements ManagedField, RuntimeRetention {}
            class Service { Integer number = 42 }
            class Page {
              @Provided() private Service service
              String title
              Void attach(Service supplied) { service = supplied }
              String read() { title + ":" + service.number.toString() }
            }
            Void main() {
              var page = Page(title: "page")
              page.attach(Service())
              printLine(page.read())
            }
            """));
  }
}
