package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.SourceFile;
import java.util.Objects;
import java.util.Optional;

public record SourceStructure(
    Optional<SourceFile> moduleConfiguration,
    SourceFile programSource,
    boolean applicationFactory,
    boolean mainEntrypoint) {
  public SourceStructure {
    moduleConfiguration = Objects.requireNonNull(moduleConfiguration, "moduleConfiguration");
    Objects.requireNonNull(programSource, "programSource");
  }

  public static SourceStructure inspect(SourceFile source) {
    Objects.requireNonNull(source, "source");
    DiagnosticBag diagnostics = new DiagnosticBag();
    Syntax.Program program =
        new Parser(source, new Lexer(source, diagnostics).lex(), diagnostics).parse();
    Optional<Syntax.FunctionDecl> module =
        program.functions().stream()
            .filter(function -> factory(function, "Module", "module"))
            .findFirst();
    boolean application =
        program.functions().stream()
            .anyMatch(function -> factory(function, "Application", "application"));
    boolean main =
        program.functions().stream().anyMatch(function -> factory(function, "Void", "main"));
    return new SourceStructure(
        module.map(function -> isolate(source, function)),
        module.map(function -> omit(source, function)).orElse(source),
        application,
        main);
  }

  private static boolean factory(Syntax.FunctionDecl function, String returnType, String name) {
    return function.kind() == Syntax.FunctionKind.REGULAR
        && function.name().equals(name)
        && function.typeParameters().isEmpty()
        && function.parameters().isEmpty()
        && function.returnType().map(type -> type.displayName().equals(returnType)).orElse(false);
  }

  private static SourceFile isolate(SourceFile source, Syntax.FunctionDecl declaration) {
    String text = source.text();
    int start = declarationStart(text, declaration.span().startOffset());
    int end = declaration.span().endOffset();
    StringBuilder isolated = new StringBuilder(text.length());
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      isolated.append(
          index >= start && index < end
              ? character
              : character == '\r' || character == '\n' ? character : ' ');
    }
    return SourceFile.of(source.path(), isolated.toString());
  }

  private static SourceFile omit(SourceFile source, Syntax.FunctionDecl declaration) {
    String text = source.text();
    int start = declarationStart(text, declaration.span().startOffset());
    int end = declaration.span().endOffset();
    StringBuilder program = new StringBuilder(text.length());
    for (int index = 0; index < text.length(); index++) {
      char character = text.charAt(index);
      program.append(
          index < start || index >= end
              ? character
              : character == '\r' || character == '\n' ? character : ' ');
    }
    return SourceFile.of(source.path(), program.toString());
  }

  private static int declarationStart(String text, int typeStart) {
    int end = typeStart;
    while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) end--;
    int start = end;
    while (start > 0 && Character.isJavaIdentifierPart(text.charAt(start - 1))) start--;
    String keyword = text.substring(start, end);
    return keyword.equals("public") || keyword.equals("private") ? start : typeStart;
  }
}
