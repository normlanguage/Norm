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
  private final Map<SourceSpan, LexicalLifetime> referenceLifetimes = new LinkedHashMap<>();
  private final FlowScopes flowScopes = new FlowScopes();
  private SymbolId currentCallable;
  private SemanticType expectedReturnType = SemanticType.VOID;
  private boolean implicitSelfReturn;
  private Syntax.AggregateDecl currentAggregate;
  private final Deque<ControlContext> controls = new ArrayDeque<>();
  private final Set<SymbolId> assignedLocals = new HashSet<>();
  private final Set<SymbolId> capturedLocals = new HashSet<>();
  private final Set<SymbolId> reportedMutableCaptures = new HashSet<>();
  private final Deque<Set<SymbolId>> lambdaLocals = new ArrayDeque<>();
  private final Deque<Set<SymbolId>> flowWriteCollectors = new ArrayDeque<>();

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

  void restore(Checkpoint checkpoint) {
    restoreState(checkpoint);
    flowScopes.restore(checkpoint.flowScopes());
  }

  private void restoreState(Checkpoint checkpoint) {
    referenceLifetimes.clear();
    referenceLifetimes.putAll(checkpoint.referenceLifetimes());
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

  FlowScopes scopes() {
    return flowScopes;
  }

  SymbolId currentCallable() {
    return currentCallable;
  }

  SemanticType expectedReturnType() {
    return expectedReturnType;
  }

  boolean implicitSelfReturn() {
    return implicitSelfReturn;
  }

  Syntax.AggregateDecl currentAggregate() {
    return currentAggregate;
  }

  LexicalLifetime referenceLifetime(SourceSpan span) {
    return referenceLifetimes.get(span);
  }

  void recordReferenceLifetime(SourceSpan span, LexicalLifetime lifetime) {
    referenceLifetimes.put(span, lifetime);
  }

  void recordAssignment(SymbolId symbol) {
    assignedLocals.add(symbol);
    flowWriteCollectors.forEach(writes -> writes.add(symbol));
  }

  boolean assigned(SymbolId symbol) {
    return assignedLocals.contains(symbol);
  }

  void recordCapture(SymbolId symbol) {
    capturedLocals.add(symbol);
  }

  boolean captured(SymbolId symbol) {
    return capturedLocals.contains(symbol);
  }

  boolean markCaptureReported(SymbolId symbol) {
    return reportedMutableCaptures.add(symbol);
  }

  boolean inLambda() {
    return !lambdaLocals.isEmpty();
  }

  boolean externalToLambda(SymbolId symbol) {
    return inLambda() && !lambdaLocals.getFirst().contains(symbol);
  }

  void declareLambdaLocal(SymbolId symbol) {
    if (inLambda()) lambdaLocals.getFirst().add(symbol);
  }

  ControlContext currentControl() {
    return controls.peekFirst();
  }

  boolean hasLoop() {
    return controls.stream().anyMatch(control -> control.kind() == ControlKind.LOOP);
  }

  ControlScope enterControl(ControlContext control) {
    controls.addFirst(control);
    return new ControlScope(control);
  }

  WriteScope collectWrites() {
    return new WriteScope();
  }

  CallableScope enterCallable(
      SymbolId callable,
      SemanticType returnType,
      boolean implicitReturn,
      Syntax.AggregateDecl aggregate) {
    var scope = new CallableScope(checkpoint());
    currentCallable = callable;
    expectedReturnType = returnType;
    implicitSelfReturn = implicitReturn;
    currentAggregate = aggregate;
    referenceLifetimes.clear();
    flowScopes.clear();
    controls.clear();
    assignedLocals.clear();
    capturedLocals.clear();
    reportedMutableCaptures.clear();
    lambdaLocals.clear();
    flowWriteCollectors.clear();
    return scope;
  }

  LambdaScope enterLambda(SourceSpan span, SemanticType returnType) {
    return new LambdaScope(span, returnType);
  }

  final class CallableScope implements AutoCloseable {
    private final Checkpoint previous;
    private boolean closed;

    private CallableScope(Checkpoint previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (closed) return;
      restoreState(previous);
      flowScopes.restoreLocals(previous.flowScopes());
      closed = true;
    }
  }

  final class LambdaScope implements AutoCloseable {
    private final SemanticType previousReturn = expectedReturnType;
    private final boolean previousImplicitReturn = implicitSelfReturn;
    private final List<ControlContext> previousControls = List.copyOf(controls);
    private final Set<SymbolId> locals = new HashSet<>();
    private final FlowScopes.Frame frame;
    private boolean closed;

    private LambdaScope(SourceSpan span, SemanticType returnType) {
      frame = flowScopes.enter(span);
      expectedReturnType = returnType;
      implicitSelfReturn = false;
      controls.clear();
      lambdaLocals.addFirst(locals);
    }

    @Override
    public void close() {
      if (closed) return;
      if (lambdaLocals.peekFirst() != locals)
        throw new IllegalStateException("Lambda scopes must close in nesting order");
      frame.close();
      lambdaLocals.removeFirst();
      controls.clear();
      controls.addAll(previousControls);
      expectedReturnType = previousReturn;
      implicitSelfReturn = previousImplicitReturn;
      closed = true;
    }
  }

  final class ControlScope implements AutoCloseable {
    private final ControlContext control;
    private boolean closed;

    private ControlScope(ControlContext control) {
      this.control = control;
    }

    @Override
    public void close() {
      if (closed) return;
      if (controls.peekFirst() != control)
        throw new IllegalStateException("Control scopes must close in nesting order");
      controls.removeFirst();
      closed = true;
    }
  }

  final class WriteScope implements AutoCloseable {
    private final Set<SymbolId> writes = new HashSet<>();
    private boolean closed;

    private WriteScope() {
      flowWriteCollectors.addFirst(writes);
    }

    Set<SymbolId> writes() {
      return Set.copyOf(writes);
    }

    @Override
    public void close() {
      if (closed) return;
      if (flowWriteCollectors.peekFirst() != writes)
        throw new IllegalStateException("Write collectors must close in nesting order");
      flowWriteCollectors.removeFirst();
      closed = true;
    }
  }
}
