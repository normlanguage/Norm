package dev.w0fv1.norm.frontend;

import static dev.w0fv1.norm.frontend.SemanticDiagnosticCodes.*;

import dev.w0fv1.norm.frontend.BodyAnalysisState.*;
import dev.w0fv1.norm.frontend.SemanticAnalysisContext.*;
import dev.w0fv1.norm.pattern.PatternCoverage;
import dev.w0fv1.norm.semantic.NumericTypes;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class PatternAnalyzer implements PatternCoverage.Domain<SemanticType> {
  private final BodyAnalysisState body;
  private final SemanticModelBuilder model;
  private final TypeResolutionState resolution;
  private final DiagnosticBag diagnostics;
  private final TypeResolver typeResolver;
  private final DeclarationAnalyzer declarationAnalyzer;
  private final FlowAnalyzer flow;
  private final NumericLiteralTyping numeric;

  PatternAnalyzer(
      BodyAnalysisState body,
      SemanticModelBuilder model,
      TypeResolutionState resolution,
      DiagnosticBag diagnostics,
      TypeResolver typeResolver,
      DeclarationAnalyzer declarationAnalyzer,
      FlowAnalyzer flow,
      NumericLiteralTyping numeric) {
    this.body = body;
    this.model = model;
    this.resolution = resolution;
    this.diagnostics = diagnostics;
    this.typeResolver = typeResolver;
    this.declarationAnalyzer = declarationAnalyzer;
    this.flow = flow;
    this.numeric = numeric;
  }

  private final Map<String, SemanticType> patternTypes = new java.util.HashMap<>();

  PatternCoverage.Pattern analyzePattern(Syntax.Pattern pattern, SemanticType expected) {
    if (expected.isNullable() && !(pattern instanceof Syntax.NullPattern)) {
      if (pattern instanceof Syntax.WildcardPattern) return PatternCoverage.Pattern.any();
      PatternCoverage.Pattern value =
          PatternCoverage.Pattern.constructor(
              "$value", List.of(analyzeNonNullPattern(pattern, expected.nonNullable())));
      if (pattern instanceof Syntax.BindingPattern binding
          && model.symbols().get(model.declarationSymbols().get(binding)).type().isNullable()) {
        return PatternCoverage.Pattern.alternatives(
            List.of(PatternCoverage.Pattern.constructor("$null", List.of()), value));
      }
      return value;
    }
    if (pattern instanceof Syntax.NullPattern) {
      if (!expected.isNullable()) {
        diagnostics.error(TYPE_MISMATCH, "null pattern requires a nullable value", pattern.span());
      }
      return PatternCoverage.Pattern.constructor("$null", List.of());
    }
    return analyzeNonNullPattern(pattern, expected.nonNullable());
  }

  PatternCoverage.Pattern analyzeNonNullPattern(Syntax.Pattern pattern, SemanticType expected) {
    return switch (pattern) {
      case Syntax.WildcardPattern ignored -> PatternCoverage.Pattern.any();
      case Syntax.BindingPattern binding -> {
        typeResolver.validateType(binding.type(), false);
        SemanticType type = typeResolver.resolveType(binding.type(), resolution.parameters());
        if (!typeResolver.isAssignable(expected, type.nonNullable())
            && !typeResolver.isAssignable(type.nonNullable(), expected)) {
          diagnostics.error(
              TYPE_MISMATCH,
              "pattern type " + type.displayName() + " does not match " + expected.displayName(),
              binding.type().span());
        }
        Symbol symbol =
            declarationAnalyzer.register(
                binding,
                binding.name(),
                SymbolKind.LOCAL_VARIABLE,
                type,
                binding.nameSpan(),
                this.body.currentCallable(),
                List.of(),
                List.of());
        flow.declareExisting(binding.name(), type, binding.nameSpan(), symbol.id());
        this.body.declareLambdaLocal(symbol.id());
        patternTypes.put("type:" + type.nonNullable(), type.nonNullable());
        yield typeResolver.isAssignable(type.nonNullable(), expected)
            ? PatternCoverage.Pattern.any()
            : PatternCoverage.Pattern.constructor("type:" + type.nonNullable(), List.of());
      }
      case Syntax.VariantPattern variant -> analyzeVariantPattern(variant, expected);
      case Syntax.IntegerPattern integer -> {
        SemanticType literalType =
            numeric.numericIntegerType(integer.value(), expected, integer.span());
        typeResolver.requireType(expected, literalType, integer.span());
        model.putType(integer.span(), literalType);
        yield PatternCoverage.Pattern.constructor(
            "numeric:"
                + literalType.identity()
                + ":"
                + (literalType.equals(SemanticType.DYNAMIC)
                    ? integer.value()
                    : NumericTypes.materialize(integer.value(), literalType)),
            List.of());
      }
      case Syntax.DecimalPattern decimal -> {
        SemanticType literalType =
            numeric.numericDecimalType(decimal.value(), expected, decimal.span());
        typeResolver.requireType(expected, literalType, decimal.span());
        model.putType(decimal.span(), literalType);
        yield PatternCoverage.Pattern.constructor(
            "numeric:"
                + literalType.identity()
                + ":"
                + (literalType.equals(SemanticType.DYNAMIC)
                    ? decimal.value()
                    : NumericTypes.materialize(decimal.value(), literalType)),
            List.of());
      }
      case Syntax.CodePointPattern codePoint -> {
        typeResolver.requireType(SemanticType.CODE_POINT, expected, codePoint.span());
        yield PatternCoverage.Pattern.constructor("codepoint:" + codePoint.value(), List.of());
      }
      case Syntax.BooleanPattern bool -> {
        typeResolver.requireType(SemanticType.BOOLEAN, expected, bool.span());
        yield PatternCoverage.Pattern.constructor("boolean:" + bool.value(), List.of());
      }
      case Syntax.StringPattern string -> {
        typeResolver.requireType(SemanticType.STRING, expected, string.span());
        yield PatternCoverage.Pattern.constructor("string:" + string.value(), List.of());
      }
      case Syntax.NullPattern ignored -> {
        diagnostics.error(TYPE_MISMATCH, "null pattern requires a nullable value", pattern.span());
        yield PatternCoverage.Pattern.constructor("$null", List.of());
      }
    };
  }

  PatternCoverage.Pattern analyzeVariantPattern(
      Syntax.VariantPattern pattern, SemanticType expected) {
    Syntax.EnumDecl enumDecl = typeResolver.resolveEnum(expected);
    if (enumDecl == null) {
      diagnostics.error(
          TYPE_MISMATCH,
          "variant pattern requires an enum value, found " + expected.displayName(),
          pattern.span());
      return PatternCoverage.Pattern.constructor("variant:" + pattern.name(), List.of());
    }
    Syntax.EnumVariant variant =
        enumDecl.variants().stream()
            .filter(candidate -> candidate.name().equals(pattern.name()))
            .findFirst()
            .orElse(null);
    if (variant == null) {
      diagnostics.error(
          UNKNOWN_NAME,
          "enum '" + enumDecl.name() + "' has no variant '" + pattern.name() + "'",
          pattern.nameSpan());
      return PatternCoverage.Pattern.constructor("variant:" + pattern.name(), List.of());
    }
    model.putBinding(pattern.nameSpan(), model.declarationSymbols().get(variant));
    Map<String, SemanticType> substitutions = typeResolver.enumSubstitutions(enumDecl, expected);
    List<SemanticType> payloadTypes =
        variant.parameters().stream()
            .map(
                parameter ->
                    typeResolver
                        .resolveDeclarationType(
                            parameter.type(), parameter, typeResolver.enumTypeParameters(enumDecl))
                        .substitute(substitutions))
            .toList();
    int requiredPayloads = 0;
    for (int index = 0; index < variant.parameters().size(); index++) {
      if (variant.parameters().get(index).defaultValue().isEmpty()) requiredPayloads = index + 1;
    }
    if (pattern.arguments().size() < requiredPayloads
        || pattern.arguments().size() > payloadTypes.size()) {
      diagnostics.error(
          TYPE_MISMATCH,
          "variant pattern '"
              + pattern.name()
              + "' accepts "
              + requiredPayloads
              + " to "
              + payloadTypes.size()
              + " argument(s), found "
              + pattern.arguments().size(),
          pattern.span());
    }
    List<PatternCoverage.Pattern> arguments = new ArrayList<>();
    for (int index = 0;
        index < Math.min(pattern.arguments().size(), payloadTypes.size());
        index++) {
      arguments.add(analyzePattern(pattern.arguments().get(index), payloadTypes.get(index)));
    }
    for (int index = arguments.size(); index < payloadTypes.size(); index++) {
      arguments.add(PatternCoverage.Pattern.any());
    }
    return PatternCoverage.Pattern.constructor("variant:" + variant.name(), arguments);
  }

  @Override
  public List<PatternCoverage.Constructor<SemanticType>> constructors(SemanticType type) {
    if (type.isNullable()) {
      return List.of(
          new PatternCoverage.Constructor<>("$null", List.of()),
          new PatternCoverage.Constructor<>("$value", List.of(type.nonNullable())));
    }
    if (type.equals(SemanticType.BOOLEAN)) {
      return List.of(
          new PatternCoverage.Constructor<>("boolean:false", List.of()),
          new PatternCoverage.Constructor<>("boolean:true", List.of()));
    }
    Syntax.EnumDecl declaration = typeResolver.resolveEnum(type);
    if (declaration == null) return List.of();
    Map<String, SemanticType> substitutions = typeResolver.enumSubstitutions(declaration, type);
    Map<String, SemanticType> parameters = typeResolver.enumTypeParameters(declaration);
    return declaration.variants().stream()
        .map(
            variant ->
                new PatternCoverage.Constructor<>(
                    "variant:" + variant.name(),
                    variant.parameters().stream()
                        .map(
                            field ->
                                typeResolver
                                    .resolveDeclarationType(field.type(), field, parameters)
                                    .substitute(substitutions))
                        .toList()))
        .toList();
  }

  @Override
  public boolean covers(SemanticType input, String previous, String candidate) {
    if (previous.equals(candidate)) return true;
    var previousType = patternTypes.get(previous);
    var candidateType = patternTypes.get(candidate);
    return previousType != null
        && candidateType != null
        && typeResolver.isAssignable(previousType, candidateType);
  }

  @Override
  public PatternCoverage.Constructor<SemanticType> openConstructor(SemanticType type, String key) {
    return constructors(type).isEmpty() ? new PatternCoverage.Constructor<>(key, List.of()) : null;
  }
}
