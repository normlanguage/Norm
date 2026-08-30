package dev.w0fv1.norm.stdlib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.frontend.CompilerSession;
import dev.w0fv1.norm.project.ProjectEnvironment;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.truffle.TruffleExecutionBackend;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class StandardLibraryDocumentationTest {
  @Test
  void documentsEveryPublicStandardLibraryDeclaration() throws Exception {
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(new TruffleExecutionBackend(8));
    try (CompilerSession compiler = environment.compilerSession()) {
      CompilationSnapshot snapshot =
          compiler.snapshot(SourceFile.of(Path.of("documentation.norm"), "Void main() {}"));
      List<String> missing = new ArrayList<>();
      Map<String, Integer> packageDocuments = new HashMap<>();
      int publicDeclarations = 0;
      for (var document : snapshot.documentIds()) {
        if (!"stdlib".equals(document.uri().getScheme())) continue;
        Syntax.Program program = snapshot.document(document).orElseThrow().syntax();
        if (program.packageName().isEmpty()) continue;
        if (documented(program.packageAnnotations())) {
          packageDocuments.merge(program.packageName(), 1, Integer::sum);
        }
        publicDeclarations += inspect(program, document.uri().toString(), missing);
      }
      packageDocuments.forEach(
          (packageName, count) -> {
            if (count > 1) missing.add("package " + packageName + " has duplicate @Document");
          });
      snapshot.documentIds().stream()
          .filter(document -> "stdlib".equals(document.uri().getScheme()))
          .map(snapshot::document)
          .flatMap(java.util.Optional::stream)
          .map(document -> document.syntax().packageName())
          .filter(name -> !name.isEmpty())
          .distinct()
          .filter(name -> !packageDocuments.containsKey(name))
          .forEach(name -> missing.add("package " + name));

      assertTrue(publicDeclarations > 0);
      assertEquals(List.of(), missing);
    }
  }

  private static int inspect(Syntax.Program program, String document, List<String> missing) {
    int declarations = 0;
    for (Syntax.EnumDecl declaration : program.enums()) {
      if (declaration.visibility() != Syntax.Visibility.PUBLIC) continue;
      declarations +=
          require(declaration.annotations(), document, "enum", declaration.name(), missing);
    }
    for (Syntax.InterfaceDecl declaration : program.interfaces()) {
      if (declaration.visibility() != Syntax.Visibility.PUBLIC) continue;
      declarations +=
          require(declaration.annotations(), document, "interface", declaration.name(), missing);
      for (Syntax.InterfaceMethodDecl method : declaration.methods()) {
        declarations +=
            require(
                method.annotations(),
                document,
                "function",
                declaration.name() + "." + method.name(),
                missing);
        declarations +=
            inspectParameters(
                method.parameters(), document, declaration.name() + "." + method.name(), missing);
      }
    }
    for (Syntax.AggregateDecl declaration : program.aggregates()) {
      if (declaration.visibility() != Syntax.Visibility.PUBLIC) continue;
      declarations +=
          require(
              declaration.annotations(),
              document,
              declaration.kind().keyword(),
              declaration.name(),
              missing);
      for (Syntax.FieldDecl field : declaration.fields()) {
        if (field.visibility() != Syntax.Visibility.PUBLIC) continue;
        declarations +=
            require(
                field.annotations(),
                document,
                "field",
                declaration.name() + "." + field.name(),
                missing);
      }
      for (Syntax.ConstructorDecl constructor : declaration.constructors()) {
        declarations +=
            require(
                constructor.annotations(), document, "constructor", declaration.name(), missing);
        declarations +=
            inspectParameters(constructor.parameters(), document, declaration.name(), missing);
      }
      for (Syntax.FunctionDecl method : declaration.methods()) {
        if (method.visibility() != Syntax.Visibility.PUBLIC) continue;
        declarations +=
            require(
                method.annotations(),
                document,
                "function",
                declaration.name() + "." + method.name(),
                missing);
        declarations +=
            inspectParameters(
                method.parameters(), document, declaration.name() + "." + method.name(), missing);
      }
    }
    for (Syntax.FunctionDecl function : program.functions()) {
      if (function.visibility() != Syntax.Visibility.PUBLIC) continue;
      declarations +=
          require(function.annotations(), document, "function", function.name(), missing);
      declarations += inspectParameters(function.parameters(), document, function.name(), missing);
    }
    return declarations;
  }

  private static int inspectParameters(
      List<Syntax.Parameter> parameters, String document, String owner, List<String> missing) {
    for (Syntax.Parameter parameter : parameters) {
      require(
          parameter.annotations(), document, "parameter", owner + "." + parameter.name(), missing);
    }
    return parameters.size();
  }

  private static int require(
      List<Syntax.AnnotationUse> annotations,
      String document,
      String kind,
      String name,
      List<String> missing) {
    if (!documented(annotations)) missing.add(document + " " + kind + " " + name);
    return 1;
  }

  private static boolean documented(List<Syntax.AnnotationUse> annotations) {
    return annotations.stream()
        .filter(annotation -> annotation.name().equals("Document"))
        .flatMap(annotation -> annotation.arguments().stream())
        .filter(
            argument ->
                argument.label().map(Syntax.ArgumentLabel::name).orElse("").equals("description"))
        .map(Syntax.CallArgument::value)
        .filter(Syntax.StringLiteralExpr.class::isInstance)
        .map(Syntax.StringLiteralExpr.class::cast)
        .map(Syntax.StringLiteralExpr::value)
        .anyMatch(description -> !description.isBlank());
  }
}
