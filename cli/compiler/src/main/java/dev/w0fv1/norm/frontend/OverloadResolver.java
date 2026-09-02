package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.TypeRelations;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

final class OverloadResolver {
  private final DiagnosticBag diagnostics;
  private final DiagnosticCode invalidCall;
  private final BiFunction<Syntax.Expression, SemanticType, SemanticType> expressionTypes;
  private final TypeRelations.DeclarationGraph typeRelations;

  OverloadResolver(
      DiagnosticBag diagnostics,
      DiagnosticCode invalidCall,
      TypeRelations.DeclarationGraph typeRelations,
      BiFunction<Syntax.Expression, SemanticType, SemanticType> expressionTypes) {
    this.diagnostics = diagnostics;
    this.invalidCall = invalidCall;
    this.typeRelations = typeRelations;
    this.expressionTypes = expressionTypes;
  }

  Candidate select(List<Candidate> candidates, Syntax.Call call, SourceSpan span) {
    if (candidates.isEmpty()) return null;
    List<Candidate> structural =
        candidates.stream()
            .map(
                candidate ->
                    new Candidate(
                        candidate.target(),
                        candidate.parameters(),
                        argumentIndices(call, candidate.parameters(), false)))
            .filter(candidate -> candidate.indices() != null)
            .toList();
    if (structural.size() == 1) return structural.getFirst();
    if (structural.isEmpty()) {
      if (candidates.size() == 1) {
        argumentIndices(call, candidates.getFirst().parameters(), true);
        return null;
      }
      diagnostics.error(
          invalidCall, "no overload accepts the supplied argument labels and count", call.span());
      return null;
    }
    List<SemanticType> argumentTypes = new ArrayList<>();
    for (Syntax.CallArgument argument : call.arguments()) {
      argumentTypes.add(
          argument.value() instanceof Syntax.NullLiteral
              ? SemanticType.NULL
              : expressionTypes.apply(argument.value(), null));
    }
    int bestScore = Integer.MAX_VALUE;
    List<Candidate> best = new ArrayList<>();
    for (Candidate candidate : structural) {
      int score = score(candidate, argumentTypes);
      if (score < 0 || score > bestScore) continue;
      if (score < bestScore) {
        bestScore = score;
        best.clear();
      }
      best.add(candidate);
    }
    if (best.size() == 1) return best.getFirst();
    diagnostics.error(
        invalidCall,
        best.isEmpty()
            ? "no overload accepts the supplied argument types"
            : "call is ambiguous between multiple overloads",
        span);
    return null;
  }

  List<Integer> argumentIndices(Syntax.Call call, List<ParameterInfo> parameters, boolean report) {
    boolean valid = call.arguments().size() <= parameters.size();
    if (report && call.arguments().size() > parameters.size()) {
      diagnostics.error(
          invalidCall,
          "call expects " + parameters.size() + " argument(s), found " + call.arguments().size(),
          call.span());
    }
    List<Integer> result = new ArrayList<>();
    boolean[] supplied = new boolean[parameters.size()];
    for (int index = 0; index < call.arguments().size(); index++) {
      Syntax.CallArgument argument = call.arguments().get(index);
      int parameterIndex = parameterIndex(argument, index, parameters, report);
      if (parameterIndex < 0) {
        valid = false;
      } else if (supplied[parameterIndex]) {
        valid = false;
        if (report) {
          diagnostics.error(
              invalidCall,
              "argument '" + parameters.get(parameterIndex).name() + "' is supplied more than once",
              argument.span());
        }
      } else {
        supplied[parameterIndex] = true;
      }
      result.add(parameterIndex);
    }
    for (int index = 0; index < supplied.length; index++) {
      if (supplied[index] || parameters.get(index).hasDefault()) continue;
      valid = false;
      if (report) {
        diagnostics.error(
            invalidCall, "missing argument '" + parameters.get(index).name() + "'", call.span());
      }
    }
    return valid || report ? List.copyOf(result) : null;
  }

  private int score(Candidate candidate, List<SemanticType> argumentTypes) {
    int score = candidate.parameters().size() - argumentTypes.size();
    for (int index = 0; index < argumentTypes.size(); index++) {
      SemanticType actual = argumentTypes.get(index);
      SemanticType parameter = candidate.parameters().get(candidate.indices().get(index)).type();
      if (actual.equals(SemanticType.NULL)) {
        if (!parameter.mayContainNull()) return -1;
        score += parameter.kind() == SemanticType.Kind.TYPE_PARAMETER ? 4 : 2;
      } else if (parameter.kind() == SemanticType.Kind.TYPE_PARAMETER) {
        score += 3;
      } else if (!parameter.equals(actual)) {
        if (!typeRelations.isAssignable(parameter, actual)) return -1;
        score++;
      }
    }
    return score;
  }

  private int parameterIndex(
      Syntax.CallArgument argument, int index, List<ParameterInfo> parameters, boolean report) {
    if (argument.label().isPresent()) {
      String label = argument.label().orElseThrow().name();
      for (int candidate = 0; candidate < parameters.size(); candidate++) {
        if (parameters.get(candidate).name().equals(label)) return candidate;
      }
      if (report) {
        diagnostics.error(
            invalidCall,
            "unknown named argument '" + label + "'",
            argument.label().orElseThrow().span());
      }
      return -1;
    }
    if (parameters.size() <= 1 && index < parameters.size()) return index;
    if (index < parameters.size()
        && argument.value() instanceof Syntax.Name shorthand
        && shorthand.value().equals(parameters.get(index).name())) {
      return index;
    }
    if (report) {
      diagnostics.error(
          invalidCall,
          "argument '"
              + (index < parameters.size() ? parameters.get(index).name() : index)
              + "' must be named",
          argument.span());
    }
    return -1;
  }

  record Candidate(Object target, List<ParameterInfo> parameters, List<Integer> indices) {
    Candidate(Object target, List<ParameterInfo> parameters) {
      this(target, List.copyOf(parameters), null);
    }
  }
}
