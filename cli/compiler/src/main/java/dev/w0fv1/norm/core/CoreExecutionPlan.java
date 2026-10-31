package dev.w0fv1.norm.core;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record CoreExecutionPlan(
    Set<DefinitionId> callables,
    Set<DefinitionId> dispatchSlots,
    Map<CoreType, java.util.List<DefinitionId>> providers) {
  public CoreExecutionPlan {
    callables = Set.copyOf(callables);
    dispatchSlots = Set.copyOf(dispatchSlots);
    providers = Map.copyOf(providers);
  }

  public static CoreExecutionPlan forArtifact(CoreArtifact artifact) {
    return forArtifact(artifact, Set.of());
  }

  public static CoreExecutionPlan forArtifact(
      CoreArtifact artifact, Set<DefinitionId> entryPoints) {
    return forArtifact(artifact, entryPoints, false);
  }

  public static CoreExecutionPlan forLiveArtifact(CoreArtifact artifact) {
    return forArtifact(artifact, Set.of(), true);
  }

  private static CoreExecutionPlan forArtifact(
      CoreArtifact artifact, Set<DefinitionId> entryPoints, boolean retainDeclarations) {
    var analysis = new Analysis(artifact, retainDeclarations);
    analysis.require(artifact.entryDefinition());
    entryPoints.forEach(analysis::require);
    for (var record : artifact.program().definitions()) {
      if (retainDeclarations && record.definition() instanceof CoreDefinition.Callable) {
        analysis.require(record.id());
      }
      if (record.definition() instanceof CoreDefinition.Aggregate aggregate
          && analysis.runtimeConstructed(record.id(), aggregate)) {
        aggregate
            .constructors()
            .forEach(link -> analysis.require(analysis.resolve(record.id(), link)));
      }
      if (!(record.definition() instanceof CoreDefinition.Callable))
        analysis.walker(record.id()).walkDeclaration(record.definition());
    }
    for (var annotation : artifact.metadata().annotations()) {
      switch (annotation.target()) {
        case CoreAnnotationTarget.Definition target ->
            analysis.require(target.occurrence().representative());
        case CoreAnnotationTarget.Parameter target ->
            analysis.require(target.callable().representative());
        case CoreAnnotationTarget.Local target ->
            analysis.require(target.callable().representative());
        case CoreAnnotationTarget.Field ignored -> {}
        case CoreAnnotationTarget.Package ignored -> {}
      }
      var walker = analysis.walker(annotation.annotation());
      annotation.values().forEach(walker::walkAnnotationValue);
    }
    while (!analysis.pending.isEmpty() || !analysis.pendingSlots.isEmpty()) {
      while (!analysis.pending.isEmpty()) {
        var id = analysis.pending.removeFirst();
        analysis.walker(id).walk(artifact.program().definition(id).orElseThrow());
      }
      while (!analysis.pendingSlots.isEmpty()) {
        var slot = analysis.pendingSlots.removeFirst();
        for (var target : analysis.dispatch.getOrDefault(slot, Set.of())) {
          switch (target) {
            case CoreWitnessTarget.Callable callable -> {
              var id = ((DefinitionReference.External) callable.definition()).definition();
              analysis.require(id);
              analysis.activate(id);
            }
            case CoreWitnessTarget.Intrinsic intrinsic -> analysis.intrinsic(intrinsic.intrinsic());
          }
        }
      }
    }
    return new CoreExecutionPlan(analysis.required, analysis.slots, analysis.providers);
  }

  private static final class Analysis {
    private final CoreProgram program;
    private final boolean retainDeclarations;
    private final java.util.Optional<DefinitionId> providerQuery;
    private final Map<CoreType, java.util.List<DefinitionId>> providers = new HashMap<>();
    private CoreTypeRelations providerTypes;
    private final Map<DefinitionId, Set<DefinitionId>> representatives = new HashMap<>();
    private final Set<DefinitionId> required = new HashSet<>();
    private final ArrayDeque<DefinitionId> pending = new ArrayDeque<>();
    private final Map<DefinitionId, Set<CoreWitnessTarget>> dispatch = new HashMap<>();
    private final Set<DefinitionId> slots = new HashSet<>();
    private final ArrayDeque<DefinitionId> pendingSlots = new ArrayDeque<>();
    private boolean annotationLifecycle;
    private boolean dynamicDefaults;
    private final Map<DefinitionId, Set<DefinitionId>> defaults = new HashMap<>();

    private Analysis(CoreArtifact artifact, boolean retainDeclarations) {
      program = artifact.program();
      providerQuery = artifact.namespace().definition("std.application", "providers");
      this.retainDeclarations = retainDeclarations;
      for (var binding : artifact.namespace().bindings()) {
        binding.shape().parameters().stream()
            .flatMap(parameter -> parameter.defaultValue().stream())
            .map(value -> ((CoreDefaultArgument.Resolved) value).occurrence().representative())
            .forEach(
                target ->
                    defaults
                        .computeIfAbsent(
                            binding.occurrence().representative(), ignored -> new HashSet<>())
                        .add(target));
      }
      for (var occurrence : artifact.authoring().occurrences()) {
        for (var definition : occurrence.representedDefinitions()) {
          representatives
              .computeIfAbsent(definition, ignored -> new HashSet<>())
              .add(occurrence.id().representative());
        }
      }
      for (var record : program.definitions()) {
        if (record.definition() instanceof CoreDefinition.Aggregate aggregate) {
          for (var method : aggregate.dispatch()) {
            if (program.definition(resolve(record.id(), method.target())).orElseThrow()
                instanceof CoreDefinition.Callable) {
              edge(record.id(), method.slot(), new CoreWitnessTarget.Callable(method.target()));
            }
          }
          for (var conformance : aggregate.conformances()) {
            conformance
                .witnesses()
                .forEach(
                    witness -> edge(record.id(), witness.requirement(), witness.implementation()));
          }
        } else if (record.definition() instanceof CoreDefinition.BuiltinConformance conformance) {
          conformance
              .witnesses()
              .forEach(
                  witness -> edge(record.id(), witness.requirement(), witness.implementation()));
        }
      }
    }

    private DefinitionId resolve(DefinitionId owner, CoreDefinitionLink link) {
      return program.resolve(owner, (DefinitionReference) link);
    }

    private void edge(DefinitionId owner, CoreDefinitionLink slot, CoreWitnessTarget target) {
      var resolved =
          target instanceof CoreWitnessTarget.Callable callable
              ? new CoreWitnessTarget.Callable(
                  new DefinitionReference.External(resolve(owner, callable.definition())))
              : target;
      dispatch.computeIfAbsent(resolve(owner, slot), ignored -> new HashSet<>()).add(resolved);
    }

    private void activate(DefinitionId slot) {
      if (slots.add(slot)) pendingSlots.addLast(slot);
      for (var representative : representatives.getOrDefault(slot, Set.of())) {
        if (slots.add(representative)) pendingSlots.addLast(representative);
      }
    }

    private void intrinsic(dev.w0fv1.norm.abi.IntrinsicId intrinsic) {
      if (intrinsic == dev.w0fv1.norm.abi.IntrinsicId.JSON_FUNCTION_INVOKE && !dynamicDefaults) {
        dynamicDefaults = true;
        Set.copyOf(required)
            .forEach(id -> defaults.getOrDefault(id, Set.of()).forEach(this::require));
      }
      if (intrinsic == dev.w0fv1.norm.abi.IntrinsicId.CLASS_FUNCTIONS)
        dispatch.keySet().forEach(this::activate);
      if (intrinsic == dev.w0fv1.norm.abi.IntrinsicId.CLASS_CONSTRUCTORS) {
        for (var record : program.definitions()) {
          if (record.definition() instanceof CoreDefinition.Aggregate aggregate)
            aggregate.constructors().forEach(link -> require(resolve(record.id(), link)));
        }
      }
    }

    private boolean runtimeConstructed(DefinitionId owner, CoreDefinition.Aggregate aggregate) {
      if (aggregate.valueCategory() == CoreValueCategory.VALUE
          || aggregate.kind() == CoreAggregateKind.ANNOTATION) return true;
      var nominal = aggregate.nominalType();
      if (nominal.packageName().equals(dev.w0fv1.norm.abi.ExceptionAbi.PACKAGE_NAME)
          && nominal.name().equals(dev.w0fv1.norm.abi.ExceptionAbi.TYPE_NAME)) return true;
      if (aggregate.parentType().isEmpty()) return false;
      var parent = (CoreType.Declared) aggregate.parentType().orElseThrow();
      var parentId = resolve(owner, ((CoreTypeConstructor.User) parent.constructor()).definition());
      return runtimeConstructed(
          parentId, (CoreDefinition.Aggregate) program.definition(parentId).orElseThrow());
    }

    private void requireAnnotationLifecycle() {
      if (annotationLifecycle) return;
      annotationLifecycle = true;
      CoreFunctionInterceptorProtocol.resolve(program)
          .ifPresent(
              protocol -> {
                activate(protocol.before());
                activate(protocol.around());
                activate(protocol.after());
              });
      CoreParameterInterceptorProtocol.resolve(program)
          .ifPresent(
              protocol -> {
                activate(protocol.before());
                activate(protocol.after());
              });
      CoreFieldInterceptorProtocol.resolve(program)
          .ifPresent(
              protocol -> {
                activate(protocol.before());
                activate(protocol.after());
              });
    }

    private void require(DefinitionId definition) {
      if (!(program.definition(definition).orElseThrow() instanceof CoreDefinition.Callable))
        return;
      var occurrences = representatives.get(definition);
      if (occurrences == null)
        throw new IllegalStateException("callable has no authoring occurrence");
      for (var representative : occurrences) {
        if (required.add(representative)) {
          pending.addLast(representative);
          if (dynamicDefaults)
            defaults.getOrDefault(representative, Set.of()).forEach(this::require);
        }
      }
    }

    private java.util.List<DefinitionId> discover(CoreType service) {
      if (!(service instanceof CoreType.Declared declared)
          || !(declared.constructor() instanceof CoreTypeConstructor.User user)
          || !(user.definition() instanceof DefinitionReference.External external)
          || !(program.definition(external.definition()).orElseThrow()
              instanceof CoreDefinition.Interface))
        throw new IllegalArgumentException("application providers require an interface");
      if (providerTypes == null) providerTypes = new CoreTypeRelations(program.definitions());
      var matches = new java.util.TreeMap<String, DefinitionId>();
      for (var record : program.definitions()) {
        if (!(record.definition() instanceof CoreDefinition.Aggregate aggregate)
            || aggregate.nominalType().visibility() != CoreVisibility.PUBLIC
            || aggregate.kind() == CoreAggregateKind.ANNOTATION
            || !aggregate.typeParameters().isEmpty()) continue;
        var concrete =
            new CoreType.Declared(
                new CoreTypeConstructor.User(new DefinitionReference.External(record.id())),
                java.util.List.of(),
                aggregate.valueCategory(),
                CoreNullability.NON_NULL);
        if (!providerTypes.isAssignable(service, concrete)) continue;
        String name = aggregate.nominalType().qualifiedName();
        var factories =
            aggregate.constructors().stream()
                .map(link -> resolve(record.id(), link))
                .filter(
                    id ->
                        CoreConstructorParameters.forCallable(
                                (CoreDefinition.Callable) program.definition(id).orElseThrow())
                            .acceptsEmpty())
                .toList();
        if (factories.size() != 1)
          throw new IllegalArgumentException(
              "application provider requires one constructor accepting no arguments: " + name);
        if (matches.putIfAbsent(name, record.id()) != null)
          throw new IllegalArgumentException("ambiguous application provider: " + name);
        require(factories.getFirst());
      }
      return java.util.List.copyOf(matches.values());
    }

    private CoreWalker walker(DefinitionId owner) {
      return new CoreWalker() {
        @Override
        protected void visitExpression(CoreExpression expression) {
          if (expression instanceof CoreExpression.Call call
              && providerQuery.isPresent()
              && resolve(owner, call.target()).equals(providerQuery.orElseThrow())) {
            var service = call.reifiedArguments().getFirst();
            if (!service.captures().isEmpty())
              throw new IllegalArgumentException(
                  "application provider queries require a concrete interface");
            providers.computeIfAbsent(
                CoreTypes.absolute(service.template(), owner, program), Analysis.this::discover);
          }
        }

        @Override
        protected void visitDependency(CoreDependency.Kind kind, CoreDefinitionLink link) {
          var id = resolve(owner, link);
          switch (kind) {
            case CALL -> require(id);
            case VIRTUAL_CALL, CLOSURE, ANNOTATION_CALLABLE -> {
              require(id);
              activate(id);
            }
            case INTERFACE_CALL -> activate(id);
            case ANNOTATION -> requireAnnotationLifecycle();
            case DECLARED_MEMBER -> {
              if (retainDeclarations) activate(id);
            }
            case TYPE, FIELD, CONSTRUCTION, IMPLEMENTATION -> {}
          }
        }

        @Override
        protected void visitIntrinsic(dev.w0fv1.norm.abi.IntrinsicId intrinsic) {
          intrinsic(intrinsic);
        }
      };
    }
  }
}
