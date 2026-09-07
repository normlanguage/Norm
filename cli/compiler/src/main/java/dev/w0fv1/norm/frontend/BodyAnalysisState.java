package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.LexicalLifetime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BodyAnalysisState {
  final Map<SourceSpan, LexicalLifetime> referenceLifetimes = new LinkedHashMap<>();
  final FlowScopes flowScopes = new FlowScopes();
  SymbolId currentCallable;
  SemanticType expectedReturnType = SemanticType.VOID;
  boolean implicitSelfReturn;
  Syntax.AggregateDecl currentAggregate;
  final Deque<ControlContext> controls = new ArrayDeque<>();
  final Set<SymbolId> assignedLocals = new HashSet<>();
  final Set<SymbolId> capturedLocals = new HashSet<>();
  final Set<SymbolId> reportedMutableCaptures = new HashSet<>();
  final Deque<Set<SymbolId>> lambdaLocals = new ArrayDeque<>();
  final Deque<Set<SymbolId>> flowWriteCollectors = new ArrayDeque<>();

  enum ControlKind {
    LOOP,
    SWITCH
  }

  static final class ControlContext {
    private final ControlKind kind;
    private SemanticType resultType;
    private LexicalLifetime referenceLifetime;

    private ControlContext(ControlKind kind, SemanticType resultType) {
      this.kind = kind;
      this.resultType = resultType;
    }

    static ControlContext loop() {
      return new ControlContext(ControlKind.LOOP, null);
    }

    static ControlContext switchExpression(SemanticType resultType) {
      return new ControlContext(ControlKind.SWITCH, resultType);
    }

    ControlKind kind() {
      return kind;
    }

    SemanticType resultType() {
      return resultType;
    }

    void setResultType(SemanticType resultType) {
      this.resultType = resultType;
    }

    LexicalLifetime referenceLifetime() {
      return referenceLifetime;
    }

    void mergeReferenceLifetime(LexicalLifetime lifetime) {
      referenceLifetime =
          referenceLifetime == null ? lifetime : referenceLifetime.narrowest(lifetime);
    }
  }

  Checkpoint checkpoint() {
    return new Checkpoint(
        Map.copyOf(referenceLifetimes),
        flowScopes.checkpoint(),
        currentCallable,
        expectedReturnType,
        implicitSelfReturn,
        currentAggregate,
        controls.stream()
            .map(
                control -> new ControlState(control, control.resultType, control.referenceLifetime))
            .toList(),
        Set.copyOf(assignedLocals),
        Set.copyOf(capturedLocals),
        Set.copyOf(reportedMutableCaptures),
        captureSets(lambdaLocals),
        captureSets(flowWriteCollectors));
  }

  void restore(Checkpoint checkpoint) {
    referenceLifetimes.clear();
    referenceLifetimes.putAll(checkpoint.referenceLifetimes());
    flowScopes.restore(checkpoint.flowScopes());
    currentCallable = checkpoint.currentCallable();
    expectedReturnType = checkpoint.expectedReturnType();
    implicitSelfReturn = checkpoint.implicitSelfReturn();
    currentAggregate = checkpoint.currentAggregate();
    controls.clear();
    for (var control : checkpoint.controls()) {
      control.target().resultType = control.resultType();
      control.target().referenceLifetime = control.referenceLifetime();
      controls.addLast(control.target());
    }
    assignedLocals.clear();
    assignedLocals.addAll(checkpoint.assignedLocals());
    capturedLocals.clear();
    capturedLocals.addAll(checkpoint.capturedLocals());
    reportedMutableCaptures.clear();
    reportedMutableCaptures.addAll(checkpoint.reportedMutableCaptures());
    restoreSets(lambdaLocals, checkpoint.lambdaLocals());
    restoreSets(flowWriteCollectors, checkpoint.flowWrites());
  }

  private static List<SetState> captureSets(Deque<Set<SymbolId>> groups) {
    return groups.stream().map(group -> new SetState(group, Set.copyOf(group))).toList();
  }

  private static void restoreSets(Deque<Set<SymbolId>> groups, List<SetState> states) {
    groups.clear();
    for (var state : states) {
      state.target().clear();
      state.target().addAll(state.values());
      groups.addLast(state.target());
    }
  }

  private record ControlState(
      ControlContext target, SemanticType resultType, LexicalLifetime referenceLifetime) {}

  private record SetState(Set<SymbolId> target, Set<SymbolId> values) {}

  record Checkpoint(
      Map<SourceSpan, LexicalLifetime> referenceLifetimes,
      FlowScopes.Checkpoint flowScopes,
      SymbolId currentCallable,
      SemanticType expectedReturnType,
      boolean implicitSelfReturn,
      Syntax.AggregateDecl currentAggregate,
      List<ControlState> controls,
      Set<SymbolId> assignedLocals,
      Set<SymbolId> capturedLocals,
      Set<SymbolId> reportedMutableCaptures,
      List<SetState> lambdaLocals,
      List<SetState> flowWrites) {}
}
