package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JsoupBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheJsoupNarForParsingMutationSanitizationAndLoopbackHttp() throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding/jsoup"))) {
      workspace = workspace.getParent();
    }
    assertTrue(workspace != null, "workspace root is unavailable");
    Path module = workspace.resolve("java-binding/jsoup/jsoup/jsoup/module.norm");
    Path repository = temporaryDirectory.resolve("repository");
    Path jsoupArtifact = Files.createDirectories(repository.resolve("org/jsoup/jsoup/1.23.2"));
    Files.copy(
        workspace.resolve("java-binding/jsoup/jsoup/jsoup/lib/jsoup-1.23.2.jar"),
        jsoupArtifact.resolve("jsoup-1.23.2.jar"));
    Files.writeString(
        jsoupArtifact.resolve("jsoup-1.23.2.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>org.jsoup</groupId>
          <artifactId>jsoup</artifactId>
          <version>1.23.2</version>
        </project>
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = environment.projectLoader(repository)) {
      new ModulePackager(projects).packageModule(module, repository);
    }

    try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      FutureTask<Void> responder =
          new FutureTask<>(
              () -> {
                try (Socket socket = server.accept();
                    BufferedReader input =
                        new BufferedReader(
                            new InputStreamReader(
                                socket.getInputStream(), StandardCharsets.US_ASCII))) {
                  while (!input.readLine().isEmpty()) {}
                  byte[] body =
                      "<html><head><title>Loopback</title></head><body>ok</body></html>"
                          .getBytes(StandardCharsets.UTF_8);
                  PrintWriter output =
                      new PrintWriter(socket.getOutputStream(), false, StandardCharsets.US_ASCII);
                  output.print("HTTP/1.1 200 OK\r\n");
                  output.print("Content-Type: text/html; charset=UTF-8\r\n");
                  output.print("Content-Length: " + body.length + "\r\n");
                  output.print("Connection: close\r\n\r\n");
                  output.flush();
                  socket.getOutputStream().write(body);
                  socket.getOutputStream().flush();
                } catch (java.io.IOException failure) {
                  throw new RuntimeException(failure);
                }
                return null;
              });
      Thread.ofVirtual().start(responder);
      Path app = Files.createDirectories(temporaryDirectory.resolve("app"));
      Path entry = app.resolve("Main.norm");
      Files.writeString(
          app.resolve("module.norm"),
          """
          Module module() {
            return module(
              name: "app",
              version: 1,
              exports: ["Main"],
              dependencies: [dependency(name: "jsoup.jsoup", version: 1)]
            )
          }
          """);
      Files.writeString(
          entry,
          """
          package app

          import jsoup.jsoup.Connection
          import jsoup.jsoup.Document
          import jsoup.jsoup.Element
          import jsoup.jsoup.Elements
          import jsoup.jsoup.DocumentOutputSettings
          import jsoup.jsoup.Safelist
          import jsoup.jsoup.jsoupClean
          import jsoup.jsoup.jsoupConnect
          import jsoup.jsoup.jsoupParse
          import jsoup.jsoup.safelistBasic

          Void main() {
            Document? document = jsoupParse(
              "<html><head><title>Norm</title></head><body><a class='entry' href='/1'>Topic</a></body></html>"
            )
            if document != null {
              printLine(document.title() ?? "missing")
              Elements? entries = document.select("a.entry")
              if entries != null {
                printLine(entries.size())
              }
              Element? entry = document.selectFirst("a.entry")
              if entry != null {
                printLine(entry.text() ?? "missing")
                printLine(entry.attr("href") ?? "missing")
                entry.addClass("selected")
                printLine(entry.hasClass("selected"))
                entry.text("Updated")
                printLine((entry.outerHtml() ?? "").contains("Updated"))
              }
              printLine((document.html() ?? "").contains("Updated"))
              DocumentOutputSettings? settings = document.outputSettings()
              if settings != null {
                settings.prettyPrint(false)
                printLine(settings.prettyPrint())
              }
            }
            Safelist? safelist = safelistBasic()
            if safelist != null {
              String clean = jsoupClean(
                arg0: "<b>safe</b><script>bad()</script>",
                arg1: safelist
              ) ?? ""
              printLine(clean.contains("<b>safe</b>"))
              printLine(clean.contains("script"))
            }
            Connection? connection = jsoupConnect("http://127.0.0.1:%d/")
            if connection != null {
              connection.timeout(2000)
              Document? response = connection.get()
              if response != null {
                printLine(response.title() ?? "missing")
              }
            }
          }
          """
              .formatted(server.getLocalPort()));
      StringWriter output = new StringWriter();
      ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
      try (ProjectLauncher launcher =
          new ProjectLauncher(
              consumerEnvironment.projectLoader(repository),
              consumerEnvironment.compilerSession(),
              backend)) {
        var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
        assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
      }
      responder.get();
      assertEquals(
          String.join(
              System.lineSeparator(),
              "Norm",
              "1",
              "Topic",
              "/1",
              "true",
              "true",
              "true",
              "false",
              "true",
              "false",
              "Loopback",
              ""),
          output.toString());
    }
  }
}
