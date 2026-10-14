package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import dev.w0fv1.norm.core.CoreLiveSchema;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.execution.ExecutionRevision;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class LiveBindings implements AutoCloseable {
  record Key(String declaration, String location) {}

  record Block(StatementNode node, List<FrameBinding> required) {}

  record Expression(ExpressionNode node, List<FrameBinding> required) {}

  record Initializer(StatementNode node, List<FrameBinding> required) {}

  record Callable(
      com.oracle.truffle.api.RootCallTarget target,
      dev.w0fv1.norm.core.CoreRuntimeSchema.CallableContract contract) {}

  static final class Version {
    final Map<String, Callable> callables;
    final Map<Key, Block> blocks;
    final Map<Key, Expression> expressions;
    final Map<Key, Initializer> initializers;
    private ExecutionRevision revision;
    private ExecutableProgram program;
    private dev.w0fv1.norm.bridge.JavaApplicationBridge.Registration bridge;
    private JavaApplicationDispatch dispatch;
    private final CoreLiveSchema schema;

    Version(
        Map<String, Callable> callables,
        Map<Key, Block> blocks,
        Map<Key, Expression> expressions,
        Map<Key, Initializer> initializers,
        CoreLiveSchema schema) {
      this.callables = Map.copyOf(callables);
      this.initializers = Map.copyOf(initializers);
      this.schema = schema;
      this.blocks = Map.copyOf(blocks);
      this.expressions = Map.copyOf(expressions);
    }

    void attach(ExecutionRevision revision) {
      if (this.revision != null) throw new IllegalStateException("Revision already attached");
      this.revision = revision;
    }

    void attachRuntime(ExecutableProgram program) {
      if (this.program != null) throw new IllegalStateException("Runtime already attached");
      this.program = program;
      program.annotations().attachVersion(this);
    }

    ExecutableProgram program() {
      return program;
    }

    AnnotationRuntime annotations() {
      return program.annotations();
    }

    ExecutionRevision revision() {
      return revision;
    }

    void installBridge(ExecutionState shared) {
      ExecutionContext context = revision == null ? shared.context() : revision.context();
      if (!(context.jarBindingRuntime()
          instanceof dev.w0fv1.norm.execution.JavaApplicationRuntime runtime)) return;
      var state =
          new ExecutionState(
              context,
              shared.annotationExecution(),
              program.values(),
              shared.resources(),
              shared.callbacks(),
              shared.contexts(),
              shared.liveBindings());
      dispatch =
          new JavaApplicationDispatch(
              program.program(), program.bridgeTargets(), program.values(), state, runtime, this);
      bridge =
          dev.w0fv1.norm.bridge.JavaApplicationBridge.replace(
              runtime.applicationClassLoaders(), dispatch);
    }

    void closeBridge() {
      if (bridge != null) {
        bridge.close();
        bridge = null;
      }
    }
  }

  static final class Builder {
    private final Map<String, Callable> callables = new LinkedHashMap<>();
    private final Map<Key, Block> blocks = new LinkedHashMap<>();
    private final Map<Key, Expression> expressions = new LinkedHashMap<>();
    private final Map<Key, Initializer> initializers = new LinkedHashMap<>();
    private Version owner;
    private final CoreLiveSchema schema;

    Builder(CoreLiveSchema schema) {
      this.schema = schema;
    }

    com.oracle.truffle.api.RootCallTarget callable(
        dev.w0fv1.norm.core.DefinitionOccurrenceId occurrence,
        com.oracle.truffle.api.RootCallTarget target) {
      String key = schema.key(occurrence);
      if (callables.putIfAbsent(key, new Callable(target, schema.callableContract(occurrence)))
          != null) throw new IllegalStateException("duplicate callable binding");
      return new CallableBinding(key, this).getCallTarget();
    }

    StatementNode block(Key key, StatementNode node, List<FrameBinding> required) {
      if (blocks.put(key, new Block(node, List.copyOf(required))) != null)
        throw new IllegalStateException("duplicate block binding");
      return new BlockBinding(key, this, node);
    }

    ExpressionNode expression(Key key, ExpressionNode node, List<FrameBinding> required) {
      if (expressions.put(key, new Expression(node, List.copyOf(required))) != null)
        throw new IllegalStateException("duplicate expression binding");
      return new ExpressionBinding(key, this, node);
    }

    void initializer(Key key, StatementNode node, List<FrameBinding> required) {
      if (initializers.put(key, new Initializer(node, List.copyOf(required))) != null)
        throw new IllegalStateException("duplicate local initializer");
    }

    Version build() {
      if (owner == null) owner = new Version(callables, blocks, expressions, initializers, schema);
      return owner;
    }
  }

  private static final Object FAILED_MIGRATION = new Object();
  private volatile Version version;
  private long generation;
  private final ThreadLocal<Version> pinnedVersion = new ThreadLocal<>();

  private record Fallback(Version retained, Version rejected) {}

  private final ThreadLocal<Fallback> fallback = new ThreadLocal<>();
  private final ThreadLocal<Version> expressionVersion = new ThreadLocal<>();
  private final ThreadLocal<Version> executionVersion = new ThreadLocal<>();
  private static final Object ACTIVATION = new Object();
  private final Map<Object, Set<Key>> pending = new ConcurrentHashMap<>();

  void enter(VirtualFrame frame) {
    Object activation = new Object();
    frame.setAuxiliarySlot(
        frame.getFrameDescriptor().findOrAddAuxiliarySlot(ACTIVATION), activation);
    pending.put(activation, ConcurrentHashMap.newKeySet());
  }

  void leave(VirtualFrame frame) {
    int slot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(ACTIVATION);
    Object activation = frame.getAuxiliarySlot(slot);
    if (activation != null) pending.remove(activation);
    frame.setAuxiliarySlot(slot, null);
  }

  private void mark(VirtualFrame frame, Key key) {
    Object activation =
        frame.getAuxiliarySlot(frame.getFrameDescriptor().findOrAddAuxiliarySlot(ACTIVATION));
    Set<Key> locations = activation == null ? null : pending.get(activation);
    if (locations != null) locations.add(key);
  }

  private void clearRegion(VirtualFrame frame, Key key) {
    Object activation =
        frame.getAuxiliarySlot(frame.getFrameDescriptor().findOrAddAuxiliarySlot(ACTIVATION));
    Set<Key> locations = activation == null ? null : pending.get(activation);
    if (locations != null)
      locations.removeIf(
          location ->
              location.declaration().equals(key.declaration())
                  && location.location().startsWith(key.location()));
  }

  private final List<WeakReference<Version>> revisions = new ArrayList<>();
  private ExecutionState execution;

  LiveBindings(Version version) {
    publish(version);
  }

  synchronized void publish(Version candidate) {
    if (execution != null) candidate.installBridge(execution);
    revisions.removeIf(reference -> reference.get() == null);
    revisions.add(new WeakReference<>(candidate));
    pending.values().forEach(Set::clear);
    version = candidate;
    generation++;
  }

  synchronized void beginExecution(ExecutionState state) {
    if (execution != null) throw new IllegalStateException("Live execution already attached");
    version.installBridge(state);
    execution = state;
  }

  synchronized void endExecution() {
    execution = null;
    for (var reference : revisions) {
      var retained = reference.get();
      if (retained != null) retained.closeBridge();
    }
  }

  synchronized dev.w0fv1.norm.execution.LiveExecutionStatus status() {
    return new dev.w0fv1.norm.execution.LiveExecutionStatus(
        generation,
        pending.values().stream()
            .flatMap(Set::stream)
            .map(key -> key.declaration() + " at " + key.location())
            .distinct()
            .sorted()
            .toList());
  }

  synchronized void requireCompatible(CoreLiveSchema candidate) {
    for (var reference : revisions) {
      Version retained = reference.get();
      if (retained != null) retained.schema.requireCompatible(candidate);
    }
  }

  private Version selected() {
    Version pinned = pinnedVersion.get();
    if (pinned != null) return pinned;
    Version current = version;
    Fallback retained = fallback.get();
    return retained != null && retained.rejected() == current ? retained.retained() : current;
  }

  Version capture() {
    Version pinned = pinnedVersion.get();
    if (pinned != null) return pinned;
    Version executing = executionVersion.get();
    return executing == null ? version : executing;
  }

  Scope pin(Version selected) {
    Version previous = pinnedVersion.get();
    if (selected == null) pinnedVersion.remove();
    else pinnedVersion.set(selected);
    return new Scope(previous);
  }

  Scope unpin() {
    return pin(null);
  }

  final class Scope implements AutoCloseable {
    private final Version previous;

    private Scope(Version previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (previous == null) pinnedVersion.remove();
      else pinnedVersion.set(previous);
    }
  }

  ExecutionRevision latestRevision() {
    return version.revision;
  }

  AnnotationRuntime runtime() {
    return capture().annotations();
  }

  private static boolean maps(Version origin, Version target, Key key) {
    if (origin == target) return true;
    var original = origin.callables.get(key.declaration());
    var candidate = target.callables.get(key.declaration());
    if (original == null || candidate == null || !original.contract().equals(candidate.contract()))
      return false;
    var before = origin.schema.layout(key.declaration());
    var after = target.schema.layout(key.declaration());
    return before != null && after != null && before.maps(key.location(), after);
  }

  ExecutionRevision revision() {
    return capture().revision;
  }

  ExecutionContext context(ExecutionContext fallback) {
    ExecutionRevision revision = revision();
    return revision == null ? fallback : revision.context();
  }

  @Override
  public synchronized void close() {
    endExecution();
    RuntimeException failure = null;
    for (var reference : revisions) {
      Version retained = reference.get();
      if (retained == null || retained.revision == null) continue;
      try {
        retained.revision.close();
      } catch (RuntimeException closeFailure) {
        if (failure == null) failure = closeFailure;
        else failure.addSuppressed(closeFailure);
      }
    }
    revisions.clear();
    if (failure != null) throw failure;
  }

  private boolean prepare(
      VirtualFrame frame, Version candidate, Key region, List<FrameBinding> required) {
    int failureSlot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(FAILED_MIGRATION);
    if (frame.getAuxiliarySlot(failureSlot) == candidate) return false;
    var ordered = new LinkedHashMap<Key, Initializer>();
    var visiting = new java.util.HashSet<Key>();
    for (FrameBinding local : required) {
      if (!plan(frame, candidate, region.declaration(), local, visiting, ordered)) return false;
    }
    boolean committed = false;
    try (var transaction = new FrameBinding.Transaction(frame);
        var scope = pin(candidate);
        var migration =
            RuntimeValues.prepareState(List.of(), ExecutionContextAccess.state(frame))) {
      for (Initializer initializer : ordered.values()) initializer.node().executeVoid(frame);
      migration.include(required.stream().map(local -> local.read(frame)).toList());
      migration.commit();
      transaction.commit();
      frame.setAuxiliarySlot(failureSlot, null);
      committed = true;
      return true;
    } catch (NormGuestException | RuntimeValues.StateMigrationFailure failure) {
      if (committed
          || failure instanceof NormGuestException guest
              && guest.code() == dev.w0fv1.norm.execution.RuntimeErrorCode.CANCELLED) throw failure;
      frame.setAuxiliarySlot(failureSlot, candidate);
      mark(
          frame,
          new Key(
              region.declaration(),
              region.location() + ": migration failed: " + failure.getMessage()));
      return false;
    }
  }

  private boolean plan(
      VirtualFrame frame,
      Version candidate,
      String declaration,
      FrameBinding local,
      Set<Key> visiting,
      Map<Key, Initializer> ordered) {
    if (local.initialized(frame)) return true;
    Key key = new Key(declaration, local.identity());
    if (ordered.containsKey(key)) return true;
    if (!visiting.add(key)) return false;
    Initializer initializer = candidate.initializers.get(key);
    if (initializer == null) return false;
    for (FrameBinding required : initializer.required()) {
      if (!plan(frame, candidate, declaration, required, visiting, ordered)) return false;
    }
    visiting.remove(key);
    ordered.put(key, initializer);
    return true;
  }

  private static final class CallableBinding extends com.oracle.truffle.api.nodes.RootNode {
    private final String key;
    private final Builder origin;

    @Child
    private com.oracle.truffle.api.nodes.IndirectCallNode call =
        com.oracle.truffle.api.nodes.IndirectCallNode.create();

    private CallableBinding(String key, Builder origin) {
      super(null);
      this.key = key;
      this.origin = origin;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      var execution = (ExecutionState) frame.getArguments()[0];
      var bindings = execution.liveBindings();
      Version original = origin.build();
      Callable before = original.callables.get(key);
      if (bindings == null) return call.call(before.target(), frame.getArguments());
      Version pinned = bindings.pinnedVersion.get();
      Version expression = bindings.expressionVersion.get();
      Version selected =
          pinned != null ? pinned : expression != null ? expression : bindings.selected();
      Callable candidate = selected.callables.get(key);
      if (candidate == null || !before.contract().equals(candidate.contract())) {
        selected = original;
        candidate = before;
      }
      Version previousExecution = bindings.executionVersion.get();
      Version previousExpression = bindings.expressionVersion.get();
      bindings.executionVersion.set(selected);
      bindings.expressionVersion.remove();
      try {
        return call.call(candidate.target(), frame.getArguments());
      } finally {
        if (previousExecution == null) bindings.executionVersion.remove();
        else bindings.executionVersion.set(previousExecution);
        if (previousExpression == null) bindings.expressionVersion.remove();
        else bindings.expressionVersion.set(previousExpression);
      }
    }
  }

  private static final class BlockBinding extends StatementNode {
    private final Key key;
    private final Builder origin;

    @Child private StatementNode initial;

    BlockBinding(Key key, Builder origin, StatementNode initial) {
      this.initial = initial;
      this.key = key;
      this.origin = origin;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      LiveBindings bindings = ExecutionContextAccess.state(frame).liveBindings();
      Version selected = bindings == null ? origin.owner : bindings.selected();
      Version attempted = selected;
      Block block = selected.blocks.get(key);
      boolean retained = false;
      if (block == null
          || !maps(origin.owner, selected, key)
          || (bindings != null && !bindings.prepare(frame, selected, key, block.required()))) {
        if (bindings != null) bindings.mark(frame, key);
        selected = origin.owner;
        retained = true;
        block = selected.blocks.get(key);
      } else if (bindings != null) bindings.clearRegion(frame, key);
      if (bindings == null) {
        block.node().executeVoid(frame);
        return;
      }
      Version previousExpression = bindings.expressionVersion.get();
      Version previousExecution = bindings.executionVersion.get();
      bindings.expressionVersion.remove();
      bindings.executionVersion.set(selected);
      Fallback previousFallback = bindings.fallback.get();
      if (retained) bindings.fallback.set(new Fallback(selected, attempted));
      try {
        block.node().executeVoid(frame);
      } finally {
        if (previousExpression == null) bindings.expressionVersion.remove();
        else bindings.expressionVersion.set(previousExpression);
        if (previousExecution == null) bindings.executionVersion.remove();
        else bindings.executionVersion.set(previousExecution);
        if (previousFallback == null) bindings.fallback.remove();
        else bindings.fallback.set(previousFallback);
      }
    }
  }

  private static final class ExpressionBinding extends ExpressionNode {
    private final Key key;
    private final Builder origin;

    @Child private ExpressionNode initial;

    ExpressionBinding(Key key, Builder origin, ExpressionNode initial) {
      this.initial = initial;
      this.key = key;
      this.origin = origin;
    }

    @Override
    Object execute(VirtualFrame frame) {
      LiveBindings bindings = ExecutionContextAccess.state(frame).liveBindings();
      if (bindings == null) return origin.owner.expressions.get(key).node().execute(frame);
      Version previous = bindings.expressionVersion.get();
      Version selected =
          bindings.pinnedVersion.get() != null
              ? bindings.pinnedVersion.get()
              : previous == null ? bindings.selected() : previous;
      Expression expression = selected.expressions.get(key);
      if (expression == null
          || !maps(origin.owner, selected, key)
          || expression.required().stream().anyMatch(local -> !local.initialized(frame))) {
        selected = origin.owner;
        expression = selected.expressions.get(key);
        bindings.mark(frame, key);
      }
      Version previousExecution = bindings.executionVersion.get();
      bindings.expressionVersion.set(selected);
      bindings.executionVersion.set(selected);
      try {
        return expression.node().execute(frame);
      } finally {
        if (previous == null) bindings.expressionVersion.remove();
        else bindings.expressionVersion.set(previous);
        if (previousExecution == null) bindings.executionVersion.remove();
        else bindings.executionVersion.set(previousExecution);
      }
    }
  }
}
