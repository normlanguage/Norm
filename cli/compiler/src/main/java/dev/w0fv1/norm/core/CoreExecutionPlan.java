package dev.w0fv1.norm.core;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record CoreExecutionPlan(Set<DefinitionId> callables, Set<DefinitionId> dispatchSlots) {
  public CoreExecutionPlan {
    callables = Set.copyOf(callables);
    dispatchSlots = Set.copyOf(dispatchSlots);
  }

  public static CoreExecutionPlan forArtifact(CoreArtifact artifact) {
    return forArtifact(artifact, Set.of());
  }

  public static CoreExecutionPlan forArtifact(
      CoreArtifact artifact, Set<DefinitionId> entryPoints) {
    var analysis = new Analysis(artifact);
    analysis.require(artifact.entryDefinition());
    entryPoints.forEach(analysis::require);
    for (var record : artifact.program().definitions()) {
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
    return new CoreExecutionPlan(analysis.required, analysis.slots);
  }

  private static final class Analysis {
    private final CoreProgram program;
    private final Map<DefinitionId, Set<DefinitionId>> representatives = new HashMap<>();
    private final Set<DefinitionId> required = new HashSet<>();
    private final ArrayDeque<DefinitionId> pending = new ArrayDeque<>();
    private final Map<DefinitionId, Set<CoreWitnessTarget>> dispatch = new HashMap<>();
    private final Set<DefinitionId> slots = new HashSet<>();
    private final ArrayDeque<DefinitionId> pendingSlots = new ArrayDeque<>();
    private boolean annotationLifecycle;

    private Analysis(CoreArtifact artifact) {
      program = artifact.program();
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
            edge(
                record.id(),
                method.slot(),
                new CoreWitnessTarget.Callable(method.implementation()));
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
        if (required.add(representative)) pending.addLast(representative);
      }
    }

    private CoreWalker walker(DefinitionId owner) {
      return new CoreWalker() {
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
            case TYPE, FIELD, CONSTRUCTION, DECLARED_MEMBER, IMPLEMENTATION -> {}
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
