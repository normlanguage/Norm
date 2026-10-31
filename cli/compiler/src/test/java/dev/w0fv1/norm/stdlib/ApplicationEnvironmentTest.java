package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.platform.jdk.JdkSystemPlatform;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ApplicationEnvironmentTest {
  @Test
  void applicationDependenciesUseGuestOutputsInsideDebugCommand() throws Exception {
    var compilation =
        compile(
            """
        import std.logging.logInfo
        Void main() { logInfo("guest") }
        """);
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    var errors = new StringWriter();
    var command = new StringWriter();
    var logger = org.slf4j.LoggerFactory.getLogger("client.transport");
    try (var runtime =
        new dev.w0fv1.norm.jvm.JvmJarBindingRuntime(
            java.util.List.of(),
            dev.w0fv1.norm.jvm.JarBindingClasspath.prepare(java.util.List.of()),
            java.util.List.of())) {
      var context =
          ExecutionContext.builder()
              .error(new PrintWriter(errors))
              .environment(Map.of("NORM_LOG_LEVEL", "warn"))
              .platform(JdkSystemPlatform.standard())
              .javaApplicationEntrypoint(
                  loader -> {
                    logger.debug("application detail");
                    logger.warn("connection lost");
                  })
              .jarBindingRuntime(runtime)
              .build();
      dev.w0fv1.norm.logging.DependencyLogs.command(
          new PrintWriter(command),
          true,
          () -> {
            logger.debug("compiler detail");
            new NormRuntime().run(compilation.output().orElseThrow().artifact(), context);
            return 0;
          });
    }
    assertTrue(command.toString().contains("compiler detail"));
    assertTrue(!command.toString().contains("connection lost"));
    assertTrue(
        errors.toString().contains("[WARN] client.transport connection lost"), errors.toString());
    assertTrue(!errors.toString().contains("application detail"));
  }

  @Test
  void routesApplicationLogsThroughDiscoveredOutputBeforeMain() {
    var compilation =
        compile(
            """
        package demo
        import std.logging.LogOutputer
        import std.logging.LogEvent
        import std.logging.logInfo
        import std.logging.logDebug
        class Database implements LogOutputer {
          Void open(Map<String, String> configuration) { printLine("open") }
          Void output(LogEvent event) { printLine(event.source) printLine(event.message) }
          Void flush() { printLine("flush") }
          Void close() { printLine("close") }
        }
        Void main() { printLine("ready") logDebug("hidden") logInfo("saved") }
        """);
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    var output = new StringWriter();
    var context =
        ExecutionContext.builder()
            .output(new PrintWriter(output))
            .environment(Map.of("NORM_LOG_OUTPUTS", "[{\"name\":\"demo.Database\"}]"))
            .platform(JdkSystemPlatform.standard())
            .build();
    new NormRuntime().run(compilation.output().orElseThrow().artifact(), context);
    assertEquals(
        "open\nready\ndemo\nsaved\nflush\nclose\n", output.toString().replace("\r\n", "\n"));
  }

  @Test
  void exposesIsolatedArgumentsStreamsEnvironmentAndCompletion() {
    var compilation =
        compile(
            """
        import std.application.arguments
        import std.application.environmentVariable
        import std.application.setExitCode
        import std.io.standardInput
        import std.io.readAll
        import std.io.decodeText
        import std.io.TextEncoding
        import std.io.printError
        Void main() {
          printLine(arguments()[0])
          printLine(environmentVariable(name: "NORM_TEST") ?? "missing")
          printLine(decodeText(content: readAll(reader: standardInput(), maximumBytes: 100), encoding: TextEncoding.Utf8))
          printError(text: "diagnostic")
          setExitCode(code: 2)
        }
        """);
    assertTrue(compilation.isSuccess(), () -> compilation.diagnostics().toString());
    var output = new StringWriter();
    var error = new StringWriter();
    var context =
        ExecutionContext.builder()
            .arguments(List.of("a  b"))
            .environment(Map.of("NORM_TEST", "isolated"))
            .input(new ByteArrayInputStream("中文\ninput".getBytes(StandardCharsets.UTF_8)))
            .output(new PrintWriter(output))
            .error(new PrintWriter(error))
            .platform(JdkSystemPlatform.standard())
            .build();
    new NormRuntime().run(compilation.output().orElseThrow().artifact(), context);
    assertEquals("a  b\nisolated\n中文\ninput\n", output.toString().replace("\r\n", "\n"));
    assertEquals("diagnostic", error.toString());
    assertEquals(2, context.exitCode());
    assertEquals(0, ExecutionContext.builder().build().exitCode());
  }
}
