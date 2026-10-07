package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class PlainObjectJavaBridgeTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void materializesUnannotatedClassesAcrossAnObjectBoundary(boolean separateSource)
      throws Exception {
    runBridge(separateSource, true);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesUnannotatedObjectIdentityWithoutAHostTypeContract(boolean separateSource)
      throws Exception {
    runBridge(separateSource, false);
  }

  private void runBridge(boolean separateSource, boolean materialized) throws Exception {
    Path classes = Files.createDirectories(directory.resolve("classes"));
    Path host = directory.resolve("Host.java");
    Files.writeString(
        host,
        """
        package fixture;
        public final class Host {
          public static String name(Object value) { return value.getClass().getName(); }
          public static Object echo(Object value) { return value; }
          public static String message(Object value) { return ((Throwable) value).getMessage(); }
          public static boolean same(Object left, Object right) { return left == right; }
          public static String tone(Object value) throws ReflectiveOperationException {
            return ((Enum<?>) value.getClass().getField("tone").get(value)).name();
          }
          public static Object mute(Object value) throws ReflectiveOperationException {
            var field = value.getClass().getField("tone");
            for (Object candidate : field.getType().getEnumConstants()) {
              if (((Enum<?>) candidate).name().equals("Muted")) { field.set(value, candidate); return value; }
            }
            throw new IllegalStateException("Missing enum constant");
          }
        }
        """);
    assertEquals(
        0,
        ToolProvider.getSystemJavaCompiler()
            .run(null, null, null, "-d", classes.toString(), host.toString()));
    Path workspace = Files.createDirectories(directory.resolve("workspace"));
    Path binding = Files.createDirectories(workspace.resolve("dependencies/host"));
    Path jarPath = binding.resolve("host.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
      jar.putNextEntry(new JarEntry("fixture/Host.class"));
      jar.write(Files.readAllBytes(classes.resolve("fixture/Host.class")));
      jar.closeEntry();
    }
    Files.writeString(
        binding.resolve("module.norm"),
        """
        Module module() {
          module(name: "host", version: 1, exports: ["Host"],
            binding: jarBinding(target: localJar(path: "host.jar", integrity: sha256("%s")),
              api: [jarType(name: "Host", members: ["name", "echo", "same", "message", "tone", "mute"])]))
        }
        """
            .formatted(Sha256Digest.compute(jarPath).value()));
    Path app = Files.createDirectories(workspace.resolve("example"));
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() { module(name: "example", version: 1,
          dependencies: [dependency(repository: "github", name: "host", version: 1)]) }
        """);
    String declaration =
        "public enum Tone { Primary, Muted }\npublic enum Payload { None, Text(String text) }\npublic class Panel { String title Tone tone Payload payload }\n"
            + "public class Failure extends Exception { Failure(String message) { super(message: message) } }\n";
    if (separateSource)
      Files.writeString(
          app.resolve("panel.norm"), "package example\nimport std.core.Exception\n" + declaration);
    Path entry = app.resolve("main.norm");
    Files.writeString(
        entry,
        """
        package example
        import std.core.Exception
        import host.hostName
        import host.hostEcho
        import host.hostSame
        import host.hostMessage
        import host.hostTone
        import host.hostMute
        %s
        Void main() {
          var panel = Panel(title: "test", tone: Tone.Primary, payload: Payload.Text(text: "preserved"))
          require(condition: hostSame(arg0: panel, arg1: panel), message: "identity retained")
          require(condition: hostEcho(panel) == panel, message: "roundtrip identity retained")
          %s
          var failure = Failure(message: "fixture failure")
          require(condition: hostMessage(failure) == "fixture failure", message: "platform exception mapping retains its message")
          %s
        }
        """
            .formatted(
                separateSource ? "" : declaration,
                materialized
                    ? """
          require(condition: hostName(Panel.class) == "java.lang.Class",
            message: "explicit Class contract materializes the host type")
          require(condition: hostTone(panel) == "Primary", message: "enum field has a Java enum representation")
          require(condition: hostMute(panel) == panel && panel.tone == Tone.Muted, message: "enum field roundtrips from Java")
          require(condition: panel.payload == Payload.Text(text: "preserved"), message: "data enum payload survives Java field roundtrip")
          panel.payload = Payload.None
          require(condition: hostEcho(panel) == panel && panel.payload == Payload.None, message: "empty variant of a data enum retains its representation")
        """
                    : """
          require(condition: hostName(panel) != "example.Panel",
            message: "opaque transport unexpectedly generated a host facade")
        """,
                materialized ? "printLine(hostName(panel)!!)" : "printLine(\"opaque\")"));
    var environment = ProjectEnvironment.bootstrap(new NormRuntime());
    var output = new StringWriter();
    try (var runner = ApplicationRunner.open(environment)) {
      var result = runner.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals(
        (materialized ? "example.Panel" : "opaque") + System.lineSeparator(), output.toString());
  }
}
