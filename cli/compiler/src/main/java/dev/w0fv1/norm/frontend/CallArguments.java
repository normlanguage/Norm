package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;
import dev.w0fv1.norm.semantic.ParameterInfo;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.List;

final class CallArguments {
  private final DiagnosticBag diagnostics;
  private final DiagnosticCode invalidCall;

  CallArguments(DiagnosticBag diagnostics, DiagnosticCode invalidCall) {
    this.diagnostics = diagnostics;
    this.invalidCall = invalidCall;
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
    if (index == 0
        && parameters.size() > 1
        && parameters.subList(1, parameters.size()).stream().allMatch(ParameterInfo::hasDefault)) {
      return index;
    }
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
}
