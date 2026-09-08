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
    REFACTOR
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
  public String usage() {
    return kind == Kind.QUERY
        ? "norm query <path> [<qualified-name> | --search <text>] [options]"
        : "norm refactor name <path> <qualified-name> --to <name> [--preview]";
  }

  @Override
  public int help(PrintWriter out, PrintWriter err) {
    return execute(List.of("-h"), out, err);
  }

  @Override
  public int execute(List<String> arguments, PrintWriter out, PrintWriter err) {
    var writer = new CommandReportWriter();
    if (arguments.equals(List.of("-h"))
        || arguments.equals(List.of("--help"))
        || kind == Kind.REFACTOR
            && (arguments.equals(List.of("name", "-h"))
                || arguments.equals(List.of("name", "--help")))) {
      if (kind == Kind.QUERY) {
        out.println("Usage: " + usage());
        out.println(
            """
            Without a selector, show the project overview and declarations. Output is JSON.
            Exact names select types, functions, fields, parameters and local declarations.
            Options:
              --source          Include enclosing declaration source
              --references      Include semantic reference locations
              --dependencies    Include declaration dependencies
              --tests           Include explicitly associated tests
              --offset <n>      Page offset, default 0
              --limit <n>       Page size, 1..1000, default 20
              --at <uri>#<n>    Disambiguate using a candidate's at field (zero-based UTF-16)
              -h, --help        Show help without loading a project
            Expansions require an exact name and may be combined. Each result list is paged.
            Examples:
              norm query ./app --search amount
              norm query ./app app.orders.amount --source --references
              norm query ./app "app.orders.amount(Integer).value"
            Ambiguous names return candidates; copy their selector and, if needed, at field.
            """);
      } else {
        out.println("Usage: " + usage());
        out.println(
            """
            Types:
              name              Rename a declaration and its semantic references
            Options:
              --to <name>       Required new declaration name
              --preview         Preview edits and statically validate; this is the default
              --at <uri>#<n>    Disambiguate using a query candidate's at field
              -h, --help        Show help without loading a project
            Output is JSON. No files are written. --apply is not supported.
            Names resolve against current sources. Input revisions are captured in the preview.
            Examples:
              norm query ./app --search amount
              norm refactor name ./app app.orders.amount --to total --preview
              norm refactor name ./app "app.orders.amount(Integer).value" --to quantity
            """);
      }
      return 0;
    }
    AuthoringOptions options;
    try {
      if (kind == Kind.REFACTOR) {
        if (arguments.isEmpty() || !arguments.getFirst().equals("name"))
          throw new IllegalArgumentException("expected refactor type 'name'; use norm refactor -h");
        arguments = arguments.subList(1, arguments.size());
      }
      options = AuthoringOptions.parse(arguments, kind == Kind.REFACTOR);
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
        var query =
            language.query(
                language.snapshot(project.compilationRequest()),
                project.scope().coordinates().keySet());
        var semanticWriter = new SemanticQueryWriter();
        if (options.target().isPresent()) {
          var matches = query.select(options.target().orElseThrow(), options.at(), 0, 1);
          if (matches.total() != 1) {
            data.add(
                "candidates",
                semanticWriter.symbols(
                    query.select(
                        options.target().orElseThrow(),
                        options.at(),
                        options.offset(),
                        options.limit())));
            throw new IllegalArgumentException(
                matches.total() == 0
                    ? "declaration not found: " + options.target().orElseThrow()
                    : "ambiguous declaration; copy a candidate selector, or add --at using its location");
          }
          var selected = matches.items().getFirst();
          if (kind == Kind.REFACTOR) {
            var preview =
                language.previewRename(
                    project.compilationRequest(),
                    selected.symbol().id(),
                    selected
                        .revision()
                        .orElseThrow(
                            () ->
                                new IllegalArgumentException(
                                    "declaration is outside editable project sources")),
                    options.newName().orElseThrow());
            report = CommandReport.checked(name(), preview.after());
            var result = writer.json(report);
            var refactor = semanticWriter.refactor(preview);
            refactor.addProperty("type", "name");
            refactor.add(
                "beforeDiagnostics",
                writer.json(CommandReport.checked(name(), preview.before())).get("diagnostics"));
            result.add("refactor", refactor);
            out.println(new GsonBuilder().disableHtmlEscaping().create().toJson(result));
            return report.status().exitCode();
          }
          JsonObject context;
          if (options.expansions().isEmpty()) {
            context = new JsonObject();
            context.add("declaration", semanticWriter.declaration(selected));
          } else {
            if (selected.revision().isEmpty())
              throw new IllegalArgumentException(
                  "context unavailable for this external declaration");
            context =
                semanticWriter.context(
                    query.context(
                        selected.symbol().id(),
                        selected.revision().orElseThrow(),
                        options.offset(),
                        options.limit(),
                        new SemanticQuery.Sections(
                            options.expansions().contains("--source"),
                            options.expansions().contains("--references"),
                            options.expansions().contains("--dependencies"),
                            options.expansions().contains("--tests"))));
            for (String field : List.of("references", "dependencies", "tests")) {
              if (!options.expansions().contains("--" + field)) context.remove(field);
            }
          }
          data.add("context", context);
        }
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
        if (options.target().isEmpty())
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
    if (!data.isEmpty()) result.add("query", data);
    out.println(new GsonBuilder().disableHtmlEscaping().create().toJson(result));
    return report.status().exitCode();
  }
}
