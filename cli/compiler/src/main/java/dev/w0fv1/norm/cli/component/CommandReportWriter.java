package dev.w0fv1.norm.cli.component;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.w0fv1.norm.cli.value.CommandReport;
import dev.w0fv1.norm.diagnostic.DiagnosticRenderer;
import dev.w0fv1.norm.execution.GuestStackFrame;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.value.BuildMetadata;
import java.io.PrintWriter;
import java.util.Locale;

public final class CommandReportWriter {
  public int write(CommandReport report, boolean json, PrintWriter out, PrintWriter err) {
    if (json) {
      out.println(new GsonBuilder().disableHtmlEscaping().create().toJson(json(report)));
    } else {
      report
          .diagnostics()
          .forEach(diagnostic -> err.println(DiagnosticRenderer.render(diagnostic)));
      report
          .failure()
          .ifPresent(
              failure -> {
                err.printf("error[%s]: %s%n", failure.code(), failure.message());
                failure
                    .location()
                    .ifPresent(
                        location ->
                            err.printf(
                                " --> %s:%d:%d%n",
                                location.uri(), location.line(), location.column()));
              });
      report
          .tests()
          .ifPresent(
              tests -> {
                tests
                    .failures()
                    .forEach(
                        failure ->
                            err.printf("FAILED %s: %s%n", failure.test(), failure.message()));
                out.printf(
                    "Tests: %d found, %d passed, %d failed, %d skipped%n",
                    tests.testsFound(),
                    tests.testsSucceeded(),
                    tests.testsFailed(),
                    tests.testsSkipped());
              });
    }
    return report.status().exitCode();
  }

  public JsonObject json(CommandReport report) {
    JsonObject result = new JsonObject();
    result.addProperty("schemaVersion", 1);
    result.addProperty("toolchainVersion", BuildMetadata.VERSION);
    result.addProperty("command", report.command());
    result.addProperty("status", report.status().name().toLowerCase(Locale.ROOT));
    result.addProperty("exitCode", report.status().exitCode());
    result.addProperty("positionEncoding", "utf-16");
    JsonArray diagnostics = new JsonArray();
    for (var diagnostic : report.diagnostics()) {
      JsonObject value = new JsonObject();
      value.addProperty("code", diagnostic.code().value());
      value.addProperty("severity", diagnostic.severity().name().toLowerCase(Locale.ROOT));
      value.addProperty("message", diagnostic.message());
      value.add("location", location(diagnostic.primarySpan()));
      JsonArray related = new JsonArray();
      for (var information : diagnostic.relatedInformation()) {
        JsonObject entry = new JsonObject();
        entry.addProperty("message", information.message());
        entry.add("location", location(information.span()));
        related.add(entry);
      }
      value.add("relatedInformation", related);
      JsonArray notes = new JsonArray();
      diagnostic.notes().forEach(notes::add);
      value.add("notes", notes);
      diagnostics.add(value);
    }
    result.add("diagnostics", diagnostics);
    report
        .tests()
        .ifPresent(
            reportTests -> {
              JsonObject tests = new JsonObject();
              tests.addProperty("found", reportTests.testsFound());
              tests.addProperty("passed", reportTests.testsSucceeded());
              tests.addProperty("failed", reportTests.testsFailed());
              tests.addProperty("skipped", reportTests.testsSkipped());
              tests.addProperty("containersFailed", reportTests.containersFailed());
              JsonArray failures = new JsonArray();
              reportTests
                  .failures()
                  .forEach(
                      failure -> {
                        JsonObject value = new JsonObject();
                        value.addProperty("test", failure.test());
                        value.addProperty("message", failure.message());
                        failures.add(value);
                      });
              tests.add("failures", failures);
              result.add("tests", tests);
            });
    report
        .failure()
        .ifPresent(
            failure -> {
              JsonObject value = new JsonObject();
              value.addProperty("code", failure.code());
              value.addProperty("message", failure.message());
              failure
                  .location()
                  .ifPresent(location -> value.add("location", runtimeLocation(location)));
              JsonArray stack = new JsonArray();
              failure
                  .stack()
                  .forEach(
                      frame -> {
                        JsonObject entry = runtimeLocation(frame);
                        entry.addProperty("name", frame.name());
                        stack.add(entry);
                      });
              value.add("stack", stack);
              result.add("failure", value);
            });
    return result;
  }

  private static JsonObject location(SourceSpan span) {
    JsonObject result = new JsonObject();
    result.addProperty("uri", span.source().id().uri().toString());
    result.addProperty("startOffset", span.startOffset());
    result.addProperty("endOffset", span.endOffset());
    return result;
  }

  private static JsonObject runtimeLocation(GuestStackFrame frame) {
    JsonObject result = new JsonObject();
    result.addProperty("uri", frame.uri().toString());
    result.addProperty("line", frame.line());
    result.addProperty("column", frame.column());
    return result;
  }
}
