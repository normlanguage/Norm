package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaScalarInterfaceIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void dispatchesJavaInterfaceMethodsOnCanonicalNormScalars() throws Exception {
    Path entry =
        Files.writeString(
            temporaryDirectory.resolve("Main.norm"),
            """
        import java.base.lang.CharSequence
        import java.base.lang.Comparable

        Integer length(CharSequence text) { return text.length() }
        Integer compare(Comparable<String> value, String other) {
          return value.compareTo(arg0: other)
        }

        Void main() {
          CharSequence text = "abc"
          printLine(length("abcd"))
          printLine(text.length())
          printLine(text.charAt(arg0: 1).scalarValue())
          CharSequence part = text.subSequence(arg0: 1, arg1: 3)!!
          printLine(part.length())
          printLine(part.charAt(arg0: 0).scalarValue())
          Comparable<String> order = "abc"
          printLine(compare(value: order, other: "abd"))
          printLine(order.compareTo(arg0: "abc"))
          Comparable<Integer> integerOrder = 7
          printLine(integerOrder.compareTo(arg0: 9))
        }
        """);
    var backend = new NormRuntime();
    var output = new StringWriter();
    try (var environment = ProjectEnvironment.bootstrap(backend);
        var projects = environment.projectLoader(temporaryDirectory.resolve("cache"));
        var runner = new ApplicationRunner(projects, environment.compilerSession(), backend)) {
      var result = runner.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals(
        String.join(System.lineSeparator(), "4", "3", "98", "2", "98", "-1", "0", "-1", ""),
        output.toString());
  }
}
