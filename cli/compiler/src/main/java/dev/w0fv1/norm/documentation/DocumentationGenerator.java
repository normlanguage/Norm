package dev.w0fv1.norm.documentation;

import dev.w0fv1.norm.frontend.CompilationSnapshot;
import dev.w0fv1.norm.semantic.AnnotationApplication;
import dev.w0fv1.norm.semantic.AnnotationDeclarationReference;
import dev.w0fv1.norm.semantic.AnnotationSchema;
import dev.w0fv1.norm.semantic.AnnotationSite;
import dev.w0fv1.norm.semantic.AnnotationValue;
import dev.w0fv1.norm.semantic.DocumentSemanticModel;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.syntax.AstNode;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnnotationAbi;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class DocumentationGenerator {
  private static final String DOCUMENT = "Document";
  private static final String DOCUMENT_IDENTITY = AnnotationAbi.PACKAGE + "." + DOCUMENT;

  public DocumentationGenerator() {}

  public ModuleDocumentation generate(
      ModuleCoordinate module,
      Map<DocumentId, String> sourcePaths,
      Set<DocumentId> exportedSources,
      CompilationSnapshot snapshot,
      boolean strict) {
    Objects.requireNonNull(module, "module");
    Objects.requireNonNull(sourcePaths, "sourcePaths");
    Objects.requireNonNull(exportedSources, "exportedSources");
    Objects.requireNonNull(snapshot, "snapshot");
    SemanticModel semantics = snapshot.semanticModel();
    DocumentationIndex documents = documentationIndex(semantics);
    Map<SymbolId, String> declarationIds = declarationIds(sourcePaths, snapshot);
    List<String> missing = new ArrayList<>();
    List<ModuleDocumentation.File> files =
        sourcePaths.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .map(
                entry ->
                    file(
                        entry.getValue(),
                        exportedSources.contains(entry.getKey()),
                        snapshot.document(entry.getKey()).orElseThrow(),
                        semantics,
                        documents,
                        declarationIds,
                        strict,
                        missing))
            .toList();
    if (!missing.isEmpty()) throw new MissingDocumentationException(missing);
    return new ModuleDocumentation(module, files);
  }

  private ModuleDocumentation.File file(
      String sourcePath,
      boolean exported,
      DocumentSemanticModel document,
      SemanticModel semantics,
      DocumentationIndex documents,
      Map<SymbolId, String> declarationIds,
      boolean strict,
      List<String> missing) {
    Syntax.Program program = document.syntax();
    List<AstNode> nodes = declarations(program);
    List<ModuleDocumentation.Declaration> declarations =
        exported
            ? nodes.stream()
                .filter(DocumentationGenerator::isPublic)
                .map(
                    node ->
                        declaration(
                            node,
                            program.packageName(),
                            semantics,
                            documents,
                            declarationIds,
                            strict,
                            missing))
                .toList()
            : List.of();
    String documentPath =
        sourcePath.substring(0, sourcePath.length() - ".norm".length()) + ".api.json";
    Optional<ModuleDocumentation.Document> fileDocument =
        documents
            .packages()
            .getOrDefault(document.source().id(), Optional.empty())
            .map(value -> materialize(value, semantics, declarationIds));
    return new ModuleDocumentation.File(
        sourcePath, documentPath, program.packageName(), exported, fileDocument, declarations);
  }

  private ModuleDocumentation.Declaration declaration(
      AstNode node,
      String packageName,
      SemanticModel semantics,
      DocumentationIndex documents,
      Map<SymbolId, String> declarationIds,
      boolean strict,
      List<String> missing) {
    return switch (node) {
      case Syntax.FunctionDecl function ->
          callable(
              function,
              function.nameSpan(),
              function.name(),
              function.kind() == Syntax.FunctionKind.EXTENSION ? "extension" : "function",
              function.visibility(),
              function.typeParameters(),
              function.parameters(),
              packageName,
              semantics,
              documents,
              declarationIds,
              strict,
              missing);
      case Syntax.AggregateDecl aggregate ->
          aggregate(aggregate, packageName, semantics, documents, declarationIds, strict, missing);
      case Syntax.InterfaceDecl declaration ->
          interfaceDeclaration(
              declaration, packageName, semantics, documents, declarationIds, strict, missing);
      case Syntax.EnumDecl declaration ->
          enumDeclaration(
              declaration, packageName, semantics, documents, declarationIds, strict, missing);
      default -> throw new IllegalArgumentException("unsupported documented declaration");
    };
  }

  private ModuleDocumentation.Declaration aggregate(
      Syntax.AggregateDecl declaration,
      String packageName,
      SemanticModel semantics,
      DocumentationIndex documents,
      Map<SymbolId, String> declarationIds,
      boolean strict,
      List<String> missing) {
    Symbol symbol = requireSymbol(semantics, declaration.nameSpan());
    List<AstNode> memberNodes = new ArrayList<>();
    memberNodes.addAll(declaration.fields());
    memberNodes.addAll(declaration.constructors());
    memberNodes.addAll(declaration.methods());
    memberNodes.sort(Comparator.comparingInt(value -> value.span().startOffset()));
    List<ModuleDocumentation.Declaration> members = new ArrayList<>();
    for (AstNode member : memberNodes) {
      switch (member) {
        case Syntax.FieldDecl field -> {
          if (field.visibility() == Syntax.Visibility.PUBLIC) {
            Symbol fieldSymbol = requireSymbol(semantics, field.nameSpan());
            members.add(
                leaf(
                    "field",
                    fieldSymbol,
                    fieldSignature(field),
                    field.visibility(),
                    field.span(),
                    List.of(),
                    List.of(),
                    Optional.empty(),
                    Optional.of(type(fieldSymbol.type())),
                    semantics,
                    documents,
                    declarationIds,
                    strict,
                    missing));
          }
        }
        case Syntax.ConstructorDecl constructor ->
            members.add(
                callable(
                    constructor,
                    constructor.nameSpan(),
                    constructor.name(),
                    "constructor",
                    Syntax.Visibility.PUBLIC,
                    List.of(),
                    constructor.parameters(),
                    packageName,
                    semantics,
                    documents,
                    declarationIds,
                    strict,
                    missing));
        case Syntax.FunctionDecl method -> {
          if (method.visibility() == Syntax.Visibility.PUBLIC) {
            members.add(
                callable(
                    method,
                    method.nameSpan(),
                    method.name(),
                    "method",
                    method.visibility(),
                    method.typeParameters(),
                    method.parameters(),
                    packageName,
                    semantics,
                    documents,
                    declarationIds,
                    strict,
                    missing));
          }
        }
        default -> throw new IllegalArgumentException("unsupported aggregate member");
      }
    }
    return leaf(
        declaration.kind().keyword(),
        symbol,
        aggregateSignature(declaration),
        declaration.visibility(),
        declaration.span(),
        typeParameters(symbol),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        semantics,
        documents,
        declarationIds,
        strict,
        missing,
        members);
  }

  private ModuleDocumentation.Declaration interfaceDeclaration(
      Syntax.InterfaceDecl declaration,
      String packageName,
      SemanticModel semantics,
      DocumentationIndex documents,
      Map<SymbolId, String> declarationIds,
      boolean strict,
      List<String> missing) {
    Symbol symbol = requireSymbol(semantics, declaration.nameSpan());
    List<ModuleDocumentation.Declaration> members =
        declaration.methods().stream()
            .map(
                method ->
                    callable(
                        method,
                        method.nameSpan(),
                        method.name(),
                        "interfaceMethod",
                        Syntax.Visibility.PUBLIC,
                        method.typeParameters(),
                        method.parameters(),
                        packageName,
                        semantics,
                        documents,
                        declarationIds,
                        strict,
                        missing))
            .toList();
    return leaf(
        "interface",
        symbol,
        interfaceSignature(declaration),
        declaration.visibility(),
        declaration.span(),
        typeParameters(symbol),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        semantics,
        documents,
        declarationIds,
        strict,
        missing,
        members);
  }

  private ModuleDocumentation.Declaration enumDeclaration(
      Syntax.EnumDecl declaration,
      String packageName,
      SemanticModel semantics,
      DocumentationIndex documents,
      Map<SymbolId, String> declarationIds,
      boolean strict,
      List<String> missing) {
    Symbol symbol = requireSymbol(semantics, declaration.nameSpan());
    List<ModuleDocumentation.Declaration> members =
        declaration.variants().stream()
            .map(
                variant -> {
                  Symbol variantSymbol = requireSymbol(semantics, variant.nameSpan());
                  return leaf(
                      "variant",
                      variantSymbol,
                      variantSignature(variant),
                      Syntax.Visibility.PUBLIC,
                      variant.span(),
                      List.of(),
                      parameters(
                          variant.parameters(),
                          variantSymbol,
                          semantics,
                          documents,
                          declarationIds,
                          false,
                          missing),
                      Optional.empty(),
                      Optional.empty(),
                      semantics,
                      documents,
                      declarationIds,
                      false,
                      missing);
                })
            .toList();
    return leaf(
        "enum",
        symbol,
        enumSignature(declaration),
        declaration.visibility(),
        declaration.span(),
        typeParameters(symbol),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        semantics,
        documents,
        declarationIds,
        strict,
        missing,
        members);
  }

  private ModuleDocumentation.Declaration callable(
      AstNode node,
      SourceSpan nameSpan,
      String name,
      String kind,
      Syntax.Visibility visibility,
      List<Syntax.TypeParameter> typeParameterSyntax,
      List<Syntax.Parameter> parameterSyntax,
      String packageName,
      SemanticModel semantics,
      DocumentationIndex documents,
      Map<SymbolId, String> declarationIds,
      boolean strict,
      List<String> missing) {
    Symbol symbol = requireSymbol(semantics, nameSpan);
    Optional<ModuleDocumentation.Type> returns =
        kind.equals("constructor") ? Optional.empty() : Optional.of(type(symbol.type()));
    String signature =
        switch (node) {
          case Syntax.FunctionDecl function -> functionSignature(function);
          case Syntax.ConstructorDecl constructor -> constructorSignature(constructor);
          case Syntax.InterfaceMethodDecl method -> interfaceMethodSignature(method);
          default -> throw new IllegalArgumentException("unsupported callable declaration");
        };
    return leaf(
        kind,
        symbol,
        signature,
        visibility,
        node.span(),
        typeParameters(symbol),
        parameters(parameterSyntax, symbol, semantics, documents, declarationIds, strict, missing),
        returns,
        Optional.empty(),
        semantics,
        documents,
        declarationIds,
        strict,
        missing);
  }

  private ModuleDocumentation.Declaration leaf(
      String kind,
      Symbol symbol,
      String signature,
      Syntax.Visibility visibility,
      SourceSpan span,
      List<ModuleDocumentation.TypeParameter> typeParameters,
      List<ModuleDocumentation.Parameter> parameters,
      Optional<ModuleDocumentation.Type> returns,
      Optional<ModuleDocumentation.Type> type,
      SemanticModel semantics,
      DocumentationIndex documents,
      Map<SymbolId, String> declarationIds,
      boolean strict,
      List<String> missing) {
    return leaf(
        kind,
        symbol,
        signature,
        visibility,
        span,
        typeParameters,
        parameters,
        returns,
        type,
        semantics,
        documents,
        declarationIds,
        strict,
        missing,
        List.of());
  }

  private ModuleDocumentation.Declaration leaf(
      String kind,
      Symbol symbol,
      String signature,
      Syntax.Visibility visibility,
      SourceSpan span,
      List<ModuleDocumentation.TypeParameter> typeParameters,
      List<ModuleDocumentation.Parameter> parameters,
      Optional<ModuleDocumentation.Type> returns,
      Optional<ModuleDocumentation.Type> type,
      SemanticModel semantics,
      DocumentationIndex documents,
      Map<SymbolId, String> declarationIds,
      boolean strict,
      List<String> missing,
      List<ModuleDocumentation.Declaration> members) {
    Optional<RawDocument> raw = documents.symbols().getOrDefault(symbol.id(), Optional.empty());
    if (strict && raw.isEmpty()) missing.add(declarationIds.get(symbol.id()));
    return new ModuleDocumentation.Declaration(
        kind,
        declarationIds.get(symbol.id()),
        symbol.name(),
        signature,
        visibility == Syntax.Visibility.PUBLIC ? "public" : "private",
        range(span),
        typeParameters,
        parameters,
        returns,
        type,
        raw.map(value -> materialize(value, semantics, declarationIds)),
        members);
  }

  private List<ModuleDocumentation.Parameter> parameters(
      List<Syntax.Parameter> syntax,
      Symbol callable,
      SemanticModel semantics,
      DocumentationIndex documents,
      Map<SymbolId, String> declarationIds,
      boolean strict,
      List<String> missing) {
    List<ModuleDocumentation.Parameter> result = new ArrayList<>();
    for (int index = 0; index < syntax.size(); index++) {
      Syntax.Parameter parameter = syntax.get(index);
      Optional<Symbol> symbol = semantics.symbolOf(parameter.nameSpan());
      Optional<RawDocument> raw =
          symbol.flatMap(value -> documents.symbols().getOrDefault(value.id(), Optional.empty()));
      if (strict && raw.isEmpty()) {
        missing.add(declarationIds.get(callable.id()) + "." + parameter.name());
      }
      SemanticType parameterType =
          index < callable.parameters().size()
              ? callable.parameters().get(index).type()
              : semantics.typeOf(parameter.type()).orElse(SemanticType.DYNAMIC);
      result.add(
          new ModuleDocumentation.Parameter(
              parameter.name(),
              type(parameterType),
              raw.map(value -> materialize(value, semantics, declarationIds))));
    }
    return List.copyOf(result);
  }

  private static List<ModuleDocumentation.TypeParameter> typeParameters(Symbol symbol) {
    return symbol.typeParameters().stream()
        .map(
            parameter ->
                new ModuleDocumentation.TypeParameter(
                    parameter.name(),
                    parameter.upperBound().map(DocumentationGenerator::type),
                    parameter.defaultType().map(DocumentationGenerator::type)))
        .toList();
  }

  private ModuleDocumentation.Document materialize(
      RawDocument document, SemanticModel semantics, Map<SymbolId, String> declarationIds) {
    return new ModuleDocumentation.Document(
        document.description(),
        references("type", document.types(), semantics, declarationIds),
        references("function", document.functions(), semantics, declarationIds),
        references("field", document.fields(), semantics, declarationIds));
  }

  private List<ModuleDocumentation.Reference> references(
      String kind,
      List<SymbolId> targets,
      SemanticModel semantics,
      Map<SymbolId, String> declarationIds) {
    return targets.stream()
        .map(
            target -> {
              Symbol symbol = semantics.symbol(target).orElseThrow();
              return new ModuleDocumentation.Reference(
                  kind,
                  declarationIds.getOrDefault(target, externalId(symbol, semantics)),
                  symbol.name());
            })
        .toList();
  }

  private DocumentationIndex documentationIndex(SemanticModel semantics) {
    Map<DocumentId, Optional<RawDocument>> packages = new LinkedHashMap<>();
    Map<SymbolId, Optional<RawDocument>> symbols = new LinkedHashMap<>();
    for (AnnotationApplication application : semantics.annotations().applications()) {
      AnnotationSchema schema =
          semantics.annotations().schema(application.annotation()).orElseThrow();
      Symbol annotation = semantics.symbol(application.annotation()).orElseThrow();
      if (!schema.name().equals(DOCUMENT)
          || !annotation.type().identity().equals(DOCUMENT_IDENTITY)) {
        continue;
      }
      Map<String, AnnotationValue> values = new LinkedHashMap<>();
      for (int index = 0; index < schema.parameters().size(); index++) {
        values.put(schema.parameters().get(index).name(), application.values().get(index));
      }
      RawDocument document =
          new RawDocument(
              literal(values.get("description")),
              references(values.get("types")),
              references(values.get("functions")),
              references(values.get("fields")));
      switch (application.target()) {
        case AnnotationSite.Package site -> packages.put(site.document(), Optional.of(document));
        case AnnotationSite.Symbol site -> symbols.put(site.symbol(), Optional.of(document));
      }
    }
    return new DocumentationIndex(packages, symbols);
  }

  private static String literal(AnnotationValue value) {
    if (value == null || !(value.value() instanceof AnnotationValue.Literal literal)) return "";
    return literal.value().toString();
  }

  private static List<SymbolId> references(AnnotationValue value) {
    if (value == null || value.value() == AnnotationValue.Null.INSTANCE) return List.of();
    if (!(value.value() instanceof AnnotationValue.ListValue list)) return List.of();
    return list.values().stream()
        .map(AnnotationValue::value)
        .filter(AnnotationDeclarationReference.class::isInstance)
        .map(AnnotationDeclarationReference.class::cast)
        .map(AnnotationDeclarationReference::target)
        .toList();
  }

  private Map<SymbolId, String> declarationIds(
      Map<DocumentId, String> sourcePaths, CompilationSnapshot snapshot) {
    Map<SymbolId, String> result = new LinkedHashMap<>();
    for (DocumentId document : sourcePaths.keySet()) {
      DocumentSemanticModel model = snapshot.document(document).orElseThrow();
      String packageName = model.syntax().packageName();
      for (AstNode declaration : declarations(model.syntax())) {
        collectIds(declaration, packageName, model.semanticModel(), result);
      }
    }
    return Map.copyOf(result);
  }

  private void collectIds(
      AstNode node, String packageName, SemanticModel semantics, Map<SymbolId, String> ids) {
    switch (node) {
      case Syntax.FunctionDecl function ->
          putCallableId(function.nameSpan(), packageName, semantics, ids);
      case Syntax.AggregateDecl aggregate -> {
        Symbol owner = requireSymbol(semantics, aggregate.nameSpan());
        ids.put(owner.id(), packageName + "::" + owner.name());
        aggregate
            .fields()
            .forEach(
                field -> {
                  Symbol symbol = requireSymbol(semantics, field.nameSpan());
                  ids.put(symbol.id(), packageName + "::" + owner.name() + "." + symbol.name());
                });
        aggregate
            .constructors()
            .forEach(
                constructor -> putCallableId(constructor.nameSpan(), packageName, semantics, ids));
        aggregate
            .methods()
            .forEach(method -> putCallableId(method.nameSpan(), packageName, semantics, ids));
      }
      case Syntax.InterfaceDecl declaration -> {
        Symbol owner = requireSymbol(semantics, declaration.nameSpan());
        ids.put(owner.id(), packageName + "::" + owner.name());
        declaration
            .methods()
            .forEach(method -> putCallableId(method.nameSpan(), packageName, semantics, ids));
      }
      case Syntax.EnumDecl declaration -> {
        Symbol owner = requireSymbol(semantics, declaration.nameSpan());
        ids.put(owner.id(), packageName + "::" + owner.name());
        declaration
            .variants()
            .forEach(
                variant -> {
                  Symbol symbol = requireSymbol(semantics, variant.nameSpan());
                  ids.put(symbol.id(), packageName + "::" + owner.name() + "." + symbol.name());
                });
      }
      default -> throw new IllegalArgumentException("unsupported declaration identity");
    }
  }

  private void putCallableId(
      SourceSpan nameSpan, String packageName, SemanticModel semantics, Map<SymbolId, String> ids) {
    Symbol symbol = requireSymbol(semantics, nameSpan);
    String owner =
        symbol.owner().flatMap(semantics::symbol).map(value -> value.name() + ".").orElse("");
    String parameters =
        symbol.parameters().stream()
            .map(parameter -> parameter.type().displayName())
            .collect(java.util.stream.Collectors.joining(","));
    String name =
        symbol.kind() == dev.w0fv1.norm.semantic.SymbolKind.CONSTRUCTOR
            ? "constructor"
            : symbol.name();
    ids.put(symbol.id(), packageName + "::" + owner + name + "(" + parameters + ")");
  }

  private static String externalId(Symbol symbol, SemanticModel semantics) {
    if (symbol.kind() == dev.w0fv1.norm.semantic.SymbolKind.TYPE
        || symbol.kind() == dev.w0fv1.norm.semantic.SymbolKind.INTERFACE) {
      return symbol.type().identity();
    }
    return symbol
            .owner()
            .flatMap(semantics::symbol)
            .map(owner -> owner.type().identity() + ".")
            .orElse("")
        + symbol.name();
  }

  private static ModuleDocumentation.Type type(SemanticType type) {
    String identity =
        type.kind() == SemanticType.Kind.TYPE_PARAMETER ? type.name() : type.identity();
    return new ModuleDocumentation.Type(
        type.kind().name().toLowerCase(java.util.Locale.ROOT),
        identity,
        type.name(),
        type.displayName(),
        type.isNullable(),
        type.arguments().stream().map(DocumentationGenerator::type).toList());
  }

  private static ModuleDocumentation.SourceRange range(SourceSpan span) {
    return new ModuleDocumentation.SourceRange(
        new ModuleDocumentation.Position(span.start().line(), span.start().column()),
        new ModuleDocumentation.Position(span.end().line(), span.end().column()));
  }

  private static Symbol requireSymbol(SemanticModel semantics, SourceSpan name) {
    return semantics
        .symbolOf(name)
        .orElseThrow(() -> new IllegalStateException("declaration symbol is absent"));
  }

  private static List<AstNode> declarations(Syntax.Program program) {
    List<AstNode> values = new ArrayList<>();
    values.addAll(program.enums());
    values.addAll(program.interfaces());
    values.addAll(program.aggregates());
    values.addAll(program.functions());
    values.sort(Comparator.comparingInt(value -> value.span().startOffset()));
    return List.copyOf(values);
  }

  private static boolean isPublic(AstNode node) {
    return switch (node) {
      case Syntax.EnumDecl value -> value.visibility() == Syntax.Visibility.PUBLIC;
      case Syntax.InterfaceDecl value -> value.visibility() == Syntax.Visibility.PUBLIC;
      case Syntax.AggregateDecl value -> value.visibility() == Syntax.Visibility.PUBLIC;
      case Syntax.FunctionDecl value -> value.visibility() == Syntax.Visibility.PUBLIC;
      default -> false;
    };
  }

  private static String enumSignature(Syntax.EnumDecl value) {
    return visibility(value.visibility())
        + "enum "
        + value.name()
        + typeParameters(value.typeParameters());
  }

  private static String interfaceSignature(Syntax.InterfaceDecl value) {
    String parents =
        value.extendedInterfaces().isEmpty()
            ? ""
            : " extends "
                + value.extendedInterfaces().stream()
                    .map(Syntax.TypeRef::displayName)
                    .collect(java.util.stream.Collectors.joining(", "));
    return visibility(value.visibility())
        + "interface "
        + value.name()
        + typeParameters(value.typeParameters())
        + parents;
  }

  private static String aggregateSignature(Syntax.AggregateDecl value) {
    String parent = value.extendedClass().map(type -> " extends " + type.displayName()).orElse("");
    String interfaces =
        value.implementedInterfaces().isEmpty()
            ? ""
            : " implements "
                + value.implementedInterfaces().stream()
                    .map(Syntax.TypeRef::displayName)
                    .collect(java.util.stream.Collectors.joining(", "));
    return visibility(value.visibility())
        + value.kind().keyword()
        + " "
        + value.name()
        + typeParameters(value.typeParameters())
        + parent
        + interfaces;
  }

  private static String functionSignature(Syntax.FunctionDecl value) {
    return visibility(value.visibility())
        + (value.kind() == Syntax.FunctionKind.EXTENSION ? "extension " : "")
        + value.returnType().map(type -> type.displayName() + " ").orElse("")
        + value.name()
        + typeParameters(value.typeParameters())
        + parameters(value.parameters());
  }

  private static String interfaceMethodSignature(Syntax.InterfaceMethodDecl value) {
    return value.returnType().displayName()
        + " "
        + value.name()
        + typeParameters(value.typeParameters())
        + parameters(value.parameters());
  }

  private static String constructorSignature(Syntax.ConstructorDecl value) {
    return value.name() + parameters(value.parameters());
  }

  private static String fieldSignature(Syntax.FieldDecl value) {
    return visibility(value.visibility()) + value.type().displayName() + " " + value.name();
  }

  private static String variantSignature(Syntax.EnumVariant value) {
    return value.name() + (value.parameters().isEmpty() ? "" : parameters(value.parameters()));
  }

  private static String visibility(Syntax.Visibility visibility) {
    return visibility == Syntax.Visibility.PRIVATE ? "private " : "";
  }

  private static String typeParameters(List<Syntax.TypeParameter> values) {
    if (values.isEmpty()) return "";
    return values.stream()
        .map(
            value ->
                value.name()
                    + value.upperBound().map(bound -> " extends " + bound.displayName()).orElse("")
                    + value.defaultType().map(type -> " = " + type.displayName()).orElse(""))
        .collect(java.util.stream.Collectors.joining(", ", "<", ">"));
  }

  private static String parameters(List<Syntax.Parameter> values) {
    return values.stream()
        .map(DocumentationGenerator::parameter)
        .collect(java.util.stream.Collectors.joining(", ", "(", ")"));
  }

  private static String parameter(Syntax.Parameter value) {
    if (value.callableParameters().isPresent() && value.type().name().equals("Function")) {
      return value.type().arguments().getFirst().displayName()
          + " "
          + value.name()
          + parameters(value.callableParameters().orElseThrow());
    }
    return value.type().displayName() + " " + value.name();
  }

  private record RawDocument(
      String description, List<SymbolId> types, List<SymbolId> functions, List<SymbolId> fields) {
    private RawDocument {
      Objects.requireNonNull(description, "description");
      types = List.copyOf(types);
      functions = List.copyOf(functions);
      fields = List.copyOf(fields);
    }
  }

  private record DocumentationIndex(
      Map<DocumentId, Optional<RawDocument>> packages,
      Map<SymbolId, Optional<RawDocument>> symbols) {
    private DocumentationIndex {
      packages = Map.copyOf(packages);
      symbols = Map.copyOf(symbols);
    }
  }
}
