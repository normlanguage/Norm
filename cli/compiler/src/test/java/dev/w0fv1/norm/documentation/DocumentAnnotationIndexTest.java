package dev.w0fv1.norm.documentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import dev.w0fv1.norm.value.CompilationRequest;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class DocumentAnnotationIndexTest {
  @Test
  void exportsDescriptionsAndRelationshipsFromTheSharedSemanticIndex() throws Exception {
    SourceFile source =
        SourceFile.of(
            Path.of("api.norm"),
            """
            @Document(description: "Sample API.") package sample
            import std.annotation.Document
            import std.testing.Test
            @Document(description: "Task model.") public class Task {
              @Document(description: "Task title.") public String title
            }
            @Document(description: "Searches tasks.", types: [Task.class], functions: [findTask.function], fields: [Task.title.field])
            public String findTask(
              @Document(description: "Search key.") String key
            ) { return key }
            @Test(functions: [findTask.function])
            Void findsTask() { require(condition: findTask(key: "x") == "x", message: "finds task") }
            """);
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new TruffleExecutionBackend(8));
    try (CompilerSession compiler = environment.compilerSession()) {
      var snapshot = compiler.snapshot(CompilationRequest.single(source));
      assertTrue(snapshot.diagnostics().isEmpty(), snapshot.diagnostics().toString());
      var files =
          new DocumentationGenerator()
              .generate(
                  ModuleCoordinate.localApplication(),
                  Map.of(source.id(), "api.norm"),
                  Set.of(source.id()),
                  snapshot,
                  false)
              .files();
      var file = files.getFirst();
      assertEquals("Sample API.", file.document().orElseThrow().description());
      var task =
          file.declarations().stream()
              .filter(value -> value.name().equals("Task"))
              .findFirst()
              .orElseThrow();
      assertEquals("Task model.", task.document().orElseThrow().description());
      assertEquals(
          "Task title.",
          task.members().stream()
              .filter(value -> value.name().equals("title"))
              .findFirst()
              .orElseThrow()
              .document()
              .orElseThrow()
              .description());
      var findTask =
          file.declarations().stream()
              .filter(value -> value.name().equals("findTask"))
              .findFirst()
              .orElseThrow();
      var document = findTask.document().orElseThrow();
      assertEquals("Searches tasks.", document.description());
      assertEquals("Task", document.types().getFirst().display());
      assertEquals("findTask", document.functions().getFirst().display());
      assertEquals("title", document.fields().getFirst().display());
      assertEquals("findsTask", document.unitTests().getFirst().display());
      assertEquals(
          "Search key.", findTask.parameters().getFirst().document().orElseThrow().description());
    }
  }
}
