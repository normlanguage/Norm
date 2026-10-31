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

  record Expression(
      ExpressionNode node,
      List<FrameBinding> required,
      boolean writesState,
      boolean controlBoundary) {}

  record Statement(StatementNode node, List<FrameBinding> required, boolean writesState) {}

  record Initializer(StatementNode node, List<FrameBinding> required) {}

  record Callable(
      com.oracle.truffle.api.RootCallTarget target,
      dev.w0fv1.norm.core.CoreRuntimeSchema.CallableContract contract,
      dev.w0fv1.norm.core.DefinitionOccurrenceId occurrence) {}

  static final class Version {
    final Map<String, Callable> callables;
    final Map<Key, Block> blocks;
    final Map<Key, Expression> expressions;
    final Map<Key, Statement> statements;
    final Map<Key, Initializer> initializers;
    private ExecutionRevision revision;
    private long generation;
    private ExecutableProgram program;
    private dev.w0fv1.norm.bridge.JavaApplicationBridge.Registration bridge;
    private JavaApplicationDispatch dispatch;
    private final CoreLiveSchema schema;

    Version(
        Map<String, Callable> callables,
        Map<Key, Block> blocks,
        Map<Key, Expression> expressions,
        Map<Key, Statement> statements,
        Map<Key, Initializer> initializers,
        CoreLiveSchema schema) {
      this.callables = Map.copyOf(callables);
      this.initializers = Map.copyOf(initializers);
      this.schema = schema;
      this.blocks = Map.copyOf(blocks);
      this.expressions = Map.copyOf(expressions);
      this.statements = Map.copyOf(statements);
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
              shared.logs(),
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
    private final Map<Key, Statement> statements = new LinkedHashMap<>();
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
      if (callables.putIfAbsent(
              key, new Callable(target, schema.callableContract(occurrence), occurrence))
          != null) throw new IllegalStateException("duplicate callable binding");
      return new CallableBinding(key, this).getCallTarget();
    }

    StatementNode block(Key key, StatementNode node, List<FrameBinding> required) {
      if (blocks.put(key, new Block(node, List.copyOf(required))) != null)
        throw new IllegalStateException("duplicate block binding");
      return new BlockBinding(key, this, node);
    }

    ExpressionNode expression(
        Key key,
        ExpressionNode node,
        List<FrameBinding> required,
        boolean writesState,
        boolean controlBoundary) {
      if (expressions.put(
              key, new Expression(node, List.copyOf(required), writesState, controlBoundary))
          != null) throw new IllegalStateException("duplicate expression binding");
      return new ExpressionBinding(key, this, node, controlBoundary);
    }

    void initializer(Key key, StatementNode node, List<FrameBinding> required) {
      if (initializers.put(key, new Initializer(node, List.copyOf(required))) != null)
        throw new IllegalStateException("duplicate local initializer");
    }

    StatementNode statement(
        Key key, StatementNode node, List<FrameBinding> required, boolean writesState) {
      if (statements.put(key, new Statement(node, List.copyOf(required), writesState)) != null)
        throw new IllegalStateException("duplicate statement binding");
      return new StatementBinding(key, this, node);
    }

    Version build() {
      if (owner == null)
        owner = new Version(callables, blocks, expressions, statements, initializers, schema);
      return owner;
    }
  }

  private static final Object FAILED_MIGRATION = new Object();
  private static final Object FRAME_VERSION = new Object();
  private static final Object FRAME_BIRTH_VERSION = new Object();
  private volatile Version version;
  private long generation;
  private final ThreadLocal<Version> pinnedVersion = new ThreadLocal<>();

  private record Fallback(Version retained, Version rejected) {}

  private final ThreadLocal<Fallback> fallback = new ThreadLocal<>();
  private final ThreadLocal<Version> expressionVersion = new ThreadLocal<>();
  private final ThreadLocal<Version> executionVersion = new ThreadLocal<>();
  private final ThreadLocal<Version> writeVersion = new ThreadLocal<>();
  private final java.util.concurrent.locks.ReentrantReadWriteLock stateLock =
      new java.util.concurrent.locks.ReentrantReadWriteLock();
  private final Set<Continuation> continuations = ConcurrentHashMap.newKeySet();
  private final Set<RuntimeStateRoots> stateRoots = ConcurrentHashMap.newKeySet();
  private final ThreadLocal<java.util.Deque<Continuation>> activeBlocks =
      ThreadLocal.withInitial(java.util.ArrayDeque::new);

  private static final class Continuation {
    final Version origin;
    Version adopted;
    final Key region;
    final String declaration;
    final List<String> statements;
    final com.oracle.truffle.api.frame.Frame frame;
    volatile int position = -1;

    Continuation(Version origin, Key key, VirtualFrame frame) {
      this.origin = origin;
      adopted = origin;
      region = key;
      this.frame = frame.materialize();
      declaration = key.declaration();
      statements = origin.schema.layout(declaration).statements(key.location());
    }
  }

  final class StateTransition implements AutoCloseable {
    private boolean closed;

    void commit() {
      Version candidate = selected();
      for (Continuation continuation : continuations) {
        continuation.adopted = candidate;
        int slot =
            continuation
                .frame
                .getFrameDescriptor()
                .findOrAddAuxiliarySlot(new AdoptedRegion(continuation.region));
        continuation.frame.setAuxiliarySlot(slot, candidate);
      }
    }

    List<Object> roots() {
      Version candidate = selected();
      var roots = new ArrayList<Object>();
      for (Continuation continuation : continuations) {
        for (int index = Math.max(0, continuation.position);
            index < continuation.statements.size();
            index++) {
          var statement =
              candidate.statements.get(
                  new Key(continuation.declaration, continuation.statements.get(index)));
          if (statement == null) continue;
          for (FrameBinding binding : statement.required())
            if (binding.initialized(continuation.frame))
              roots.add(binding.read(continuation.frame));
        }
      }
      return roots;
    }

    @Override
    public void close() {
      if (!closed) {
        closed = true;
        stateLock.writeLock().unlock();
      }
    }
  }

  final class StateWrite implements AutoCloseable {
    private boolean closed;

    @Override
    public void close() {
      if (!closed) {
        closed = true;
        stateLock.readLock().unlock();
      }
    }
  }

  RootLease retain(RuntimeStateRoots roots) {
    stateRoots.add(roots);
    return new RootLease(roots);
  }

  List<Object> stateRoots() {
    return new ArrayList<>(stateRoots);
  }

  final class RootLease implements AutoCloseable {
    private final RuntimeStateRoots roots;

    private RootLease(RuntimeStateRoots roots) {
      this.roots = roots;
    }

    @Override
    public void close() {
      stateRoots.remove(roots);
    }
  }

  StateWrite stateWrite() {
    stateLock.readLock().lock();
    return new StateWrite();
  }

  StateTransition stateTransition() {
    if (stateLock.getReadHoldCount() != 0 && !stateLock.isWriteLockedByCurrentThread()
        || !stateLock.writeLock().tryLock())
      throw new RuntimeValues.StateMigrationPending(
          "State update awaits an active write continuation");
    var transition = new StateTransition();
    try {
      Version candidate = selected();
      for (Continuation continuation : continuations) {
        if (continuation.position >= 0) {
          var layout = continuation.origin.schema.layout(continuation.declaration);
          for (String expression :
              layout.expressions(continuation.statements.get(continuation.position))) {
            Key key = new Key(continuation.declaration, expression);
            if (!maps(continuation.origin, candidate, key))
              throw new RuntimeValues.StateMigrationPending(
                  "State update awaits expression continuation: " + key);
          }
        }
        for (int index = continuation.position + 1;
            index < continuation.statements.size();
            index++) {
          Key key = new Key(continuation.declaration, continuation.statements.get(index));
          if (!maps(continuation.origin, candidate, key))
            throw new RuntimeValues.StateMigrationPending(
                "State update awaits continuation: " + key);
        }
      }
      return transition;
    } catch (RuntimeException failure) {
      transition.close();
      throw failure;
    }
  }

  private static final Object ACTIVATION = new Object();
  private final Map<Object, Set<Key>> pending = new ConcurrentHashMap<>();

  void enter(VirtualFrame frame) {
    Object activation = new Object();
    frame.setAuxiliarySlot(
        frame.getFrameDescriptor().findOrAddAuxiliarySlot(ACTIVATION), activation);
    pending.put(activation, ConcurrentHashMap.newKeySet());
    var execution = ExecutionContextAccess.state(frame);
    Version owner = capture();
    for (Object argument : frame.getArguments())
      if (argument instanceof RuntimeValues.ObjectValue object
          && !object.state().schemaMatches(object.info(execution), object.type())) {
        owner = null;
        break;
      }
    frame.setAuxiliarySlot(frame.getFrameDescriptor().findOrAddAuxiliarySlot(FRAME_VERSION), owner);
    frame.setAuxiliarySlot(
        frame.getFrameDescriptor().findOrAddAuxiliarySlot(FRAME_BIRTH_VERSION), owner);
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
    candidate.generation = generation;
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
    Version writing = writeVersion.get();
    if (writing != null) return writing;
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

  synchronized long currentRevision() {
    return capture().generation;
  }

  RuntimeValues.Closure snapshot(RuntimeValues.Closure function) {
    if (function.snapshotVersion() != null) return function;
    Version selected = capture();
    var declaration = function.sourceDeclaration();
    AnnotationRuntime annotations = declaration.isEmpty() ? null : function.annotations();
    if (annotations != null && annotations.version() != null) {
      Version original = annotations.version();
      String key = original.schema.declarationKey(function.declaration());
      Callable before = original.callables.get(key);
      Callable candidate = selected.callables.get(key);
      if (before != null && candidate != null && before.contract().equals(candidate.contract())) {
        declaration = java.util.Optional.of(candidate.occurrence());
        annotations = selected.annotations();
      } else selected = original;
    }
    return new RuntimeValues.Closure(
        new RevisionFunctionRootNode(function.target(), selected).getCallTarget(),
        declaration,
        function.unbound(),
        function.receiver(),
        function.captures(),
        function.receiverTypeArguments(),
        function.reifiedArguments(),
        function.functionType(),
        annotations);
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

  private static final class FailedMigrations {
    final Set<Version> versions =
        java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
  }

  private Set<String> liveLocals(
      VirtualFrame frame, Version candidate, List<FrameBinding> required) {
    var live = new java.util.LinkedHashSet<String>();
    required.forEach(binding -> live.add(binding.identity()));
    int activationSlot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(ACTIVATION);
    Object activation = frame.getAuxiliarySlot(activationSlot);
    for (Continuation continuation : continuations) {
      int continuationSlot =
          continuation.frame.getFrameDescriptor().findOrAddAuxiliarySlot(ACTIVATION);
      if (continuation.frame.getAuxiliarySlot(continuationSlot) != activation) continue;
      for (int index = Math.max(0, continuation.position);
          index < continuation.statements.size();
          index++) {
        var statement =
            candidate.statements.get(
                new Key(continuation.declaration, continuation.statements.get(index)));
        if (statement != null)
          statement.required().forEach(binding -> live.add(binding.identity()));
      }
    }
    return live;
  }

  private boolean prepare(
      VirtualFrame frame, Version candidate, Key region, List<FrameBinding> required) {
    int versionSlot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(FRAME_VERSION);
    if (frame.getAuxiliarySlot(
            frame.getFrameDescriptor().findOrAddAuxiliarySlot(FRAME_BIRTH_VERSION))
        == candidate) return true;
    if (frame.getAuxiliarySlot(versionSlot) == candidate
        && required.stream().allMatch(binding -> binding.initialized(frame))) return true;
    int failureSlot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(FAILED_MIGRATION);
    Object cached = frame.getAuxiliarySlot(failureSlot);
    FailedMigrations failures =
        cached instanceof FailedMigrations retained ? retained : new FailedMigrations();
    frame.setAuxiliarySlot(failureSlot, failures);
    if (failures.versions.contains(candidate)) return false;
    var ordered = new LinkedHashMap<Key, Initializer>();
    var visiting = new java.util.HashSet<Key>();
    for (FrameBinding local : required) {
      if (!plan(frame, candidate, region.declaration(), local, visiting, ordered)) return false;
    }
    boolean committed = false;
    try (var transaction = new FrameBinding.Transaction(frame);
        var scope = pin(candidate);
        var transition = ordered.isEmpty() ? null : stateTransition();
        var migration =
            RuntimeValues.prepareState(List.of(), ExecutionContextAccess.state(frame))) {
      for (Initializer initializer : ordered.values()) initializer.node().executeVoid(frame);
      var roots = new ArrayList<Object>();
      var locals = new LinkedHashMap<FrameBinding, dev.w0fv1.norm.core.CoreType>();
      var live = liveLocals(frame, candidate, required);
      for (var local : candidate.schema.localTypes(region.declaration()).entrySet()) {
        if (!live.contains(local.getKey())) continue;
        var binding =
            new FrameBinding(0, com.oracle.truffle.api.frame.FrameSlotKind.Object, local.getKey());
        if (binding.initialized(frame)) {
          roots.add(binding.read(frame));
          locals.put(binding, local.getValue());
        }
      }
      migration.include(roots);
      var reified =
          candidate.schema.reifiedLocals(region.declaration()).stream()
              .map(
                  identity ->
                      new FrameBinding(
                              0, com.oracle.truffle.api.frame.FrameSlotKind.Object, identity)
                          .read(frame))
              .map(dev.w0fv1.norm.core.CoreType.class::cast)
              .toList();
      for (var local : locals.entrySet()) {
        var expected = local.getValue().substitute(reified::get);
        local.getKey().write(frame, migration.transform(local.getKey().read(frame), expected));
      }
      migration.commit();
      transaction.commit();
      if (transition != null) transition.commit();
      frame.setAuxiliarySlot(versionSlot, candidate);
      committed = true;
      return true;
    } catch (NormGuestException | RuntimeValues.StateMigrationFailure failure) {
      if (committed
          || failure instanceof NormGuestException guest
              && guest.code() == dev.w0fv1.norm.execution.RuntimeErrorCode.CANCELLED) throw failure;
      if (!(failure instanceof RuntimeValues.StateMigrationPending))
        failures.versions.add(candidate);
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
      Version resultVersion = RuntimeInvocation.resultVersion(getCallTarget(), bindings.capture());
      Version pinned = bindings.pinnedVersion.get();
      Version expression = bindings.expressionVersion.get();
      Version selected =
          pinned != null ? pinned : expression != null ? expression : bindings.selected();
      Callable candidate = selected.callables.get(key);
      boolean retained = candidate == null || !before.contract().equals(candidate.contract());
      if (retained) {
        selected = original;
        candidate = before;
      }
      Version previousExecution = bindings.executionVersion.get();
      Version previousExpression = bindings.expressionVersion.get();
      bindings.executionVersion.set(selected);
      bindings.expressionVersion.remove();
      if (retained) bindings.pinnedVersion.set(selected);
      Object result;
      try {
        Object[] arguments = frame.getArguments();
        if (arguments.length > 1
            && java.util.Arrays.stream(arguments)
                .noneMatch(ConstructionArguments.class::isInstance)) {
          var types = new ArrayList<dev.w0fv1.norm.core.CoreType>();
          candidate.contract().receiver().ifPresent(types::add);
          types.addAll(candidate.contract().captures());
          candidate.contract().arguments().forEach(parameter -> types.add(parameter.type()));
          boolean migrationRequired = false;
          for (int index = 1; index <= types.size(); index++)
            migrationRequired |=
                RuntimeStateTransaction.requiresMigration(arguments[index], execution);
          if (migrationRequired) {
            Object[] adopted = arguments.clone();
            final Object[] source = arguments;
            final int offset = types.size() + 1;
            try (var migration = RuntimeStateTransaction.open(execution)) {
              migration.include(java.util.Arrays.asList(arguments).subList(1, offset));
              for (int index = 0; index < types.size(); index++) {
                var expected =
                    types
                        .get(index)
                        .substitute(
                            parameter -> (dev.w0fv1.norm.core.CoreType) source[offset + parameter]);
                adopted[index + 1] = migration.transform(arguments[index + 1], expected);
              }
              migration.commit();
            }
            arguments = adopted;
          }
        }
        result = call.call(candidate.target(), arguments);
      } finally {
        if (retained) {
          if (pinned == null) bindings.pinnedVersion.remove();
          else bindings.pinnedVersion.set(pinned);
        }
        if (previousExecution == null) bindings.executionVersion.remove();
        else bindings.executionVersion.set(previousExecution);
        if (previousExpression == null) bindings.expressionVersion.remove();
        else bindings.expressionVersion.set(previousExpression);
      }
      return selected == resultVersion
          ? result
          : RuntimeInvocation.adaptResult(execution, result, resultVersion);
    }
  }

  private record AdoptedRegion(Key key) {}

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
      int adoptedSlot = frame.getFrameDescriptor().findOrAddAuxiliarySlot(new AdoptedRegion(key));
      Version adopted = (Version) frame.getAuxiliarySlot(adoptedSlot);
      if (adopted == null) adopted = origin.owner;
      Version selected = bindings == null ? origin.owner : bindings.selected();
      Version attempted = selected;
      Block block = selected.blocks.get(key);
      boolean retained = false;
      if (block == null
          || !maps(adopted, selected, key)
          || (bindings != null && !bindings.prepare(frame, selected, key, block.required()))) {
        if (bindings != null) bindings.mark(frame, key);
        selected = adopted;
        retained = true;
        block = selected.blocks.get(key);
      } else {
        frame.setAuxiliarySlot(adoptedSlot, selected);
        if (bindings != null) bindings.clearRegion(frame, key);
      }
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
      var continuation = new Continuation(selected, key, frame);
      bindings.continuations.add(continuation);
      bindings.activeBlocks.get().push(continuation);
      try {
        block.node().executeVoid(frame);
      } finally {
        bindings.activeBlocks.get().pop();
        bindings.continuations.remove(continuation);
        if (previousExpression == null) bindings.expressionVersion.remove();
        else bindings.expressionVersion.set(previousExpression);
        if (previousExecution == null) bindings.executionVersion.remove();
        else bindings.executionVersion.set(previousExecution);
        if (previousFallback == null) bindings.fallback.remove();
        else bindings.fallback.set(previousFallback);
      }
    }
  }

  private static final class StatementBinding extends StatementNode {
    private final Key key;
    private final Builder origin;
    @Child private StatementNode initial;

    StatementBinding(Key key, Builder origin, StatementNode initial) {
      this.key = key;
      this.origin = origin;
      this.initial = initial;
    }

    @Override
    void executeVoid(VirtualFrame frame) {
      LiveBindings bindings = ExecutionContextAccess.state(frame).liveBindings();
      if (bindings == null) {
        initial.executeVoid(frame);
        return;
      }
      Continuation continuation = bindings.activeBlocks.get().peek();
      if (continuation != null && continuation.declaration.equals(key.declaration())) {
        int position = continuation.statements.indexOf(key.location());
        if (position >= 0) continuation.position = position;
      }
      Version retained =
          continuation != null && continuation.declaration.equals(key.declaration())
              ? continuation.adopted
              : bindings.capture();
      Version pinned = bindings.pinnedVersion.get();
      Version selected =
          pinned != null
              ? pinned
              : continuation != null && continuation.declaration.equals(key.declaration())
                  ? retained
                  : bindings.selected();
      Statement statement = selected.statements.get(key);
      if (statement == null
          || !maps(origin.owner, selected, key)
          || selected != retained
              && bindings.pinnedVersion.get() == null
              && !bindings.prepare(frame, selected, key, statement.required())) {
        selected = retained;
        statement = selected.statements.get(key);
        bindings.mark(frame, key);
      }
      boolean writing = statement.writesState();
      if (writing) bindings.stateLock.readLock().lock();
      try {
        Version previousWrite = bindings.writeVersion.get();
        Version previousExecution = bindings.executionVersion.get();
        if (statement.writesState()) bindings.writeVersion.set(selected);
        bindings.executionVersion.set(selected);
        try {
          statement.node().executeVoid(frame);
        } finally {
          if (previousWrite == null) bindings.writeVersion.remove();
          else bindings.writeVersion.set(previousWrite);
          if (previousExecution == null) bindings.executionVersion.remove();
          else bindings.executionVersion.set(previousExecution);
        }
      } finally {
        if (writing) bindings.stateLock.readLock().unlock();
      }
    }
  }

  private static final class ExpressionBinding extends ExpressionNode {
    private final Key key;
    private final Builder origin;

    @Child private ExpressionNode initial;
    private final boolean controlBoundary;

    ExpressionBinding(Key key, Builder origin, ExpressionNode initial, boolean controlBoundary) {
      this.initial = initial;
      this.key = key;
      this.origin = origin;
      this.controlBoundary = controlBoundary;
    }

    @Override
    Object execute(VirtualFrame frame) {
      LiveBindings bindings = ExecutionContextAccess.state(frame).liveBindings();
      if (bindings == null) return origin.owner.expressions.get(key).node().execute(frame);
      Version previous = bindings.expressionVersion.get();
      Continuation continuation = bindings.activeBlocks.get().peek();
      Version retained =
          continuation != null && continuation.declaration.equals(key.declaration())
              ? continuation.adopted
              : bindings.capture();
      Version pinned = bindings.pinnedVersion.get();
      Version selected =
          pinned != null
              ? pinned
              : controlBoundary
                  ? bindings.selected()
                  : continuation != null && continuation.declaration.equals(key.declaration())
                      ? retained
                      : bindings.writeVersion.get() != null
                          ? bindings.writeVersion.get()
                          : previous == null ? bindings.selected() : previous;
      Expression expression = selected.expressions.get(key);
      if (expression == null
          || !maps(origin.owner, selected, key)
          || selected != retained
              && previous == null
              && bindings.pinnedVersion.get() == null
              && !bindings.prepare(frame, selected, key, expression.required())) {
        selected = previous == null ? retained : previous;
        expression = selected.expressions.get(key);
        bindings.mark(frame, key);
      }
      boolean writing = expression.writesState();
      if (writing) bindings.stateLock.readLock().lock();
      try {
        Version previousExecution = bindings.executionVersion.get();
        Version previousWrite = bindings.writeVersion.get();
        if (writing) bindings.writeVersion.set(selected);
        bindings.expressionVersion.set(selected);
        bindings.executionVersion.set(selected);
        try {
          return expression.node().execute(frame);
        } finally {
          if (previous == null) bindings.expressionVersion.remove();
          else bindings.expressionVersion.set(previous);
          if (previousExecution == null) bindings.executionVersion.remove();
          else bindings.executionVersion.set(previousExecution);
          if (previousWrite == null) bindings.writeVersion.remove();
          else bindings.writeVersion.set(previousWrite);
        }
      } finally {
        if (writing) bindings.stateLock.readLock().unlock();
      }
    }
  }
}
