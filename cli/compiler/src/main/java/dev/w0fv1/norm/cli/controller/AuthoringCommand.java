package dev.w0fv1.norm.cli.controller;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.w0fv1.norm.cli.component.CommandReportWriter;
import dev.w0fv1.norm.cli.component.SemanticQueryWriter;
import dev.w0fv1.norm.cli.value.CommandReport;
import dev.w0fv1.norm.cli.value.CommandReport.Status;
import dev.w0fv1.norm.frontend.CompilationInfrastructureException;
import dev.w0fv1.norm.language.LanguageService;
import dev.w0fv1.norm.language.SemanticQuery;
import dev.w0fv1.norm.project.ModuleCompilationException;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

final class AuthoringCommand implements Command {
  enum Kind {
    QUERY,
    RENAME
  }

  private final Kind kind;

  AuthoringCommand(Kind kind) {
    this.kind = java.util.Objects.requireNonNull(kind, "kind");
  }

  @Override
  public String name() {
    return kind.name().toLowerCase(java.util.Locale.ROOT);
  }

  @Override
  public String summary() {
    return kind == Kind.QUERY
        ? "Query project declarations and semantic context as JSON"
        : "Preview and validate a semantic rename without writing files";
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    var writer = new CommandReportWriter();
    AuthoringOptions options;
    try {
      options = AuthoringOptions.parse(arguments, kind == Kind.RENAME);
    } catch (IllegalArgumentException exception) {
      return writer.write(
          CommandReport.failed(name(), Status.USAGE_ERROR, "NORM-CLI-0003", exception.getMessage()),
          true,
          out,
          err);
    }
    CommandReport report;
    JsonObject data = new JsonObject();
    try {
      var environment = ProjectEnvironment.bootstrap(new NormRuntime());
      try (var projects = environment.projectLoader();
          var language = new LanguageService(environment.compilerSession())) {
        var project = projects.loadForAnalysis(options.entry());
        if (kind == Kind.RENAME) {
          var preview =
              language.previewRename(
                  project.compilationRequest(),
                  options.symbol().orElseThrow(),
                  options.revision().orElseThrow(),
                  options.newName().orElseThrow());
          report = CommandReport.checked(name(), preview.after());
          JsonObject result = writer.json(report);
          JsonObject rename = new SemanticQueryWriter().rename(preview);
          rename.add(
              "beforeDiagnostics",
              writer.json(CommandReport.checked(name(), preview.before())).get("diagnostics"));
          result.add("rename", rename);
          out.println(new GsonBuilder().disableHtmlEscaping().create().toJson(result));
          return report.status().exitCode();
        }
        var query =
            language.query(
                language.snapshot(project.compilationRequest()),
                project.scope().coordinates().keySet());
        if (options.overview()) {
          data.addProperty("root", project.root().toUri().toString());
          data.addProperty("entry", project.primaryPath().toUri().toString());
          data.addProperty("revisionScope", "document-text-utf8-sha256");
          data.addProperty("declarationScope", "analyzed-source-documents");
          JsonArray modules = new JsonArray();
          project.moduleDescriptors().entrySet().stream()
              .sorted(java.util.Map.Entry.comparingByKey())
              .forEach(
                  entry -> {
                    JsonObject module = new JsonObject();
                    module.addProperty("name", entry.getKey().name());
                    module.addProperty("version", entry.getKey().version());
                    JsonArray dependencies = new JsonArray();
                    entry.getValue().dependencies().stream()
                        .sorted(
                            java.util.Comparator.comparing(requirement -> requirement.coordinate()))
                        .forEach(
                            requirement -> {
                              JsonObject dependency = new JsonObject();
                              dependency.addProperty("name", requirement.coordinate().name());
                              dependency.addProperty("version", requirement.coordinate().version());
                              dependencies.add(dependency);
                            });
                    module.add("dependencies", dependencies);
                    modules.add(module);
                  });
          data.add("modules", modules);
          JsonArray documents = new JsonArray();
          query
              .documents()
              .forEach(
                  revision -> {
                    JsonObject item = new JsonObject();
                    item.addProperty("uri", revision.document().uri().toString());
                    item.addProperty("revision", revision.content().value());
                    var coordinate = project.scope().coordinate(revision.document());
                    item.addProperty("module", coordinate.module().name());
                    item.addProperty("moduleVersion", coordinate.module().version());
                    item.addProperty(
                        "testSource", project.scope().testSources().contains(revision.document()));
                    documents.add(item);
                  });
          data.add("documents", documents);
        }
        var semanticWriter = new SemanticQueryWriter();
        if (options.symbol().isPresent())
          data.add(
              "context",
              semanticWriter.context(
                  query.context(
                      options.symbol().orElseThrow(),
                      options.revision().orElseThrow(),
                      options.offset(),
                      options.limit(),
                      options.source())));
        else
          data.add(
              "symbols",
              semanticWriter.symbols(
                  query.search(options.search(), options.offset(), options.limit())));
        report = CommandReport.checked(name(), query.diagnostics());
      }
    } catch (SemanticQuery.StaleRevisionException exception) {
      report =
          CommandReport.failed(name(), Status.CONFLICT, "NORM-QUERY-0001", exception.getMessage());
    } catch (ModuleCompilationException exception) {
      report = CommandReport.checked(name(), exception.diagnostics());
    } catch (IOException exception) {
      report =
          CommandReport.failed(name(), Status.INPUT_ERROR, "NORM-CLI-0004", exception.getMessage());
    } catch (CompilationInfrastructureException exception) {
      report =
          CommandReport.failed(
              name(), Status.INTERNAL_ERROR, "NORM-CLI-0005", exception.getMessage());
    } catch (dev.w0fv1.norm.execution.NormExecutionException exception) {
      report = CommandReport.runtimeFailure(name(), exception);
    } catch (IllegalArgumentException exception) {
      report =
          CommandReport.failed(
              name(), Status.INPUT_ERROR, "NORM-QUERY-0002", exception.getMessage());
    }
    JsonObject result = writer.json(report);
    if (report.failure().isEmpty() && (data.has("symbols") || data.has("context")))
      result.add("query", data);
    out.println(new GsonBuilder().disableHtmlEscaping().create().toJson(result));
    return report.status().exitCode();
  }
}
