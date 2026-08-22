package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.syntax.LanguageSyntax;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.SourceFile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ModuleManifestParser {
  public ModuleManifestParser() {}

  public ModuleManifest parse(SourceFile source) {
    DiagnosticBag diagnostics = new DiagnosticBag();
    var tokens = new Lexer(source, diagnostics).lex();
    Optional<Syntax.Expression> expression =
        new Parser(source, tokens, diagnostics).parseExpressionDocument();
    if (diagnostics.hasErrors() || expression.isEmpty()) {
      String message =
          diagnostics.isEmpty()
              ? "expected Module expression"
              : diagnostics.snapshot().getFirst().message();
      throw invalid(source, message);
    }
    if (!(expression.orElseThrow() instanceof Syntax.Call call)
        || !(call.callee() instanceof Syntax.Name constructor)
        || !constructor.value().equals("Module")
        || !constructor.typeArguments().isEmpty()) {
      throw invalid(source, "must contain one Module expression");
    }
    String name = null;
    Integer version = null;
    List<String> exports = null;
    Set<String> fields = new HashSet<>();
    for (Syntax.CallArgument argument : call.arguments()) {
      if (argument.label().isEmpty()) throw invalid(source, "Module fields must be named");
      String field = argument.label().orElseThrow().name();
      if (!fields.add(field)) {
        throw invalid(source, "duplicate Module field '" + field + "'");
      }
      switch (field) {
        case "name" -> {
          if (!(argument.value() instanceof Syntax.StringLiteralExpr value)) {
            throw invalid(source, "expected module name");
          }
          name = value.value();
          validateQualifiedName(source, name, "module");
        }
        case "version" -> {
          if (!(argument.value() instanceof Syntax.IntegerLiteral value)
              || value.value() > Integer.MAX_VALUE) {
            throw invalid(source, "module version is outside the supported integer range");
          }
          version = (int) value.value();
          if (version < 1) throw invalid(source, "module version must be positive");
          if (version != 1) throw invalid(source, "unsupported module version " + version);
        }
        case "exports" -> exports = parseExports(source, argument.value());
        default -> throw invalid(source, "unknown Module field '" + field + "'");
      }
    }
    if (name == null || version == null || exports == null) {
      throw invalid(source, "Module requires name, version, and exports");
    }
    return new ModuleManifest(source, name, version, exports);
  }

  public void validateExport(
      ModuleManifest manifest, String exportedName, SourceFile exportedSource) {
    DiagnosticBag diagnostics = new DiagnosticBag();
    Syntax.Program program =
        new Parser(exportedSource, new Lexer(exportedSource, diagnostics).lex(), diagnostics)
            .parse();
    if (diagnostics.hasErrors()) {
      throw invalid(exportedSource, diagnostics.snapshot().getFirst().message());
    }
    String expectedPackage = manifest.sourcePackage(exportedName);
    if (!program.packageName().equals(expectedPackage)) {
      throw invalid(
          manifest.source(),
          "exported source '"
              + manifest.sourcePath(exportedName)
              + "' must declare package '"
              + expectedPackage
              + "'");
    }
  }

  private List<String> parseExports(SourceFile source, Syntax.Expression expression) {
    if (!(expression instanceof Syntax.ArrayLiteral array)) {
      throw invalid(source, "expected exported source names");
    }
    List<String> exports = new ArrayList<>();
    Set<String> unique = new HashSet<>();
    for (Syntax.Expression element : array.elements()) {
      if (!(element instanceof Syntax.StringLiteralExpr value)) {
        throw invalid(source, "expected exported source name");
      }
      String name = value.value();
      validateQualifiedName(source, name, "export");
      if (!unique.add(name)) throw invalid(source, "duplicate exported source '" + name + "'");
      exports.add(name);
    }
    return List.copyOf(exports);
  }

  private static void validateQualifiedName(SourceFile source, String value, String role) {
    if (value.isBlank()) throw invalid(source, role + " name must not be blank");
    for (String segment : value.split("\\.", -1)) {
      if (!LanguageSyntax.isIdentifier(segment)) {
        throw invalid(source, "invalid " + role + " name '" + value + "'");
      }
    }
  }

  private static IllegalArgumentException invalid(SourceFile source, String message) {
    return new IllegalArgumentException("invalid " + source.displayName() + ": " + message);
  }
}
