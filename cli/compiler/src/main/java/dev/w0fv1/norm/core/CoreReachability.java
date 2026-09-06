package dev.w0fv1.norm.core;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CoreReachability {
  private CoreReachability() {}

  public static Set<dev.w0fv1.norm.abi.IntrinsicId> intrinsics(CoreDefinitionGroup group) {
    var intrinsics = java.util.EnumSet.noneOf(dev.w0fv1.norm.abi.IntrinsicId.class);
    var walker =
        new CoreWalker() {
          @Override
          protected void visitIntrinsic(dev.w0fv1.norm.abi.IntrinsicId intrinsic) {
            intrinsics.add(intrinsic);
          }
        };
    group.definitions().forEach(walker::walk);
    return java.util.Collections.unmodifiableSet(intrinsics);
  }

  public enum RetentionKind {
    APPLICATION_ENTRY,
    HOST_ENTRY,
    ANNOTATION,
    ANNOTATION_VALUE,
    SUBTYPE,
    DEFINITION_LINK,
    AUTHORING_GROUP,
    AUTHORING_REFERENCE,
    OWNED_MEMBER
  }

  public record RetentionCause(
      RetentionKind kind,
      Optional<DefinitionGroupId> source,
      Optional<CoreDependency.Kind> dependency) {
    public RetentionCause(RetentionKind kind, Optional<DefinitionGroupId> source) {
      this(kind, source, Optional.empty());
    }

    public RetentionCause {
      java.util.Objects.requireNonNull(kind, "kind");
      java.util.Objects.requireNonNull(source, "source");
      java.util.Objects.requireNonNull(dependency, "dependency");
      if (dependency.isPresent() != (kind == RetentionKind.DEFINITION_LINK))
        throw new IllegalArgumentException("definition links require a dependency kind");
      if (source.isEmpty()
          != (kind == RetentionKind.APPLICATION_ENTRY || kind == RetentionKind.HOST_ENTRY))
        throw new IllegalArgumentException("only entry roots may omit a predecessor");
    }
  }

  public record Analysis(CoreArtifact artifact, Map<DefinitionGroupId, RetentionCause> causes) {
    public Analysis {
      java.util.Objects.requireNonNull(artifact, "artifact");
      causes = Map.copyOf(causes);
    }
  }

  private static void retain(
      Map<DefinitionGroupId, RetentionCause> causes,
      DefinitionGroupId target,
      RetentionKind kind,
      DefinitionGroupId source) {
    if (!causes.containsKey(source))
      throw new IllegalArgumentException("predecessor is not retained");
    causes.putIfAbsent(target, new RetentionCause(kind, Optional.of(source)));
  }

  public static java.util.Optional<Set<String>> jarCalls(CoreArtifact artifact) {
    return jarCalls(artifact, CoreExecutionPlan.forArtifact(artifact));
  }

  public static java.util.Optional<Set<String>> jarCalls(
      CoreArtifact artifact, CoreExecutionPlan execution) {
    Set<String> calls = new HashSet<>();
    boolean[] dynamic = {false};
    var walker =
        new CoreWalker() {
          @Override
          protected void visitExpression(CoreExpression expression) {
            if (!(expression instanceof CoreExpression.Intrinsic intrinsic)) return;
            if (intrinsic.intrinsic() != dev.w0fv1.norm.abi.IntrinsicId.JAR_INVOKE
                && intrinsic.intrinsic() != dev.w0fv1.norm.abi.IntrinsicId.JAR_INVOKE_VOID) return;
            if (!intrinsic.arguments().isEmpty()
                && intrinsic.arguments().getFirst().value()
                    instanceof CoreExpression.Literal literal
                && literal.value() instanceof String name) {
              calls.add(name);
            } else {
              dynamic[0] = true;
            }
          }
        };
    artifact.program().definitions().stream()
        .filter(record -> execution.callables().contains(record.id()))
        .forEach(record -> walker.walkExecution(record.definition()));
    return dynamic[0] ? java.util.Optional.empty() : java.util.Optional.of(Set.copyOf(calls));
  }

  public static CoreArtifact retainApplication(CoreArtifact artifact) {
    return retainApplication(artifact, Set.of());
  }

  public static CoreArtifact retainApplication(
      CoreArtifact artifact, Set<DefinitionId> entryPoints) {
    return analyze(artifact, entryPoints).artifact();
  }

  public static Analysis analyze(CoreArtifact artifact, Set<DefinitionId> entryPoints) {
    CoreProgram program = artifact.program();
    var definitions = program.definitions();
    Map<DefinitionGroupId, RetentionCause> causes = new LinkedHashMap<>();
    Set<DefinitionGroupId> retained = causes.keySet();
    causes.put(
        artifact.entryDefinition().group(),
        new RetentionCause(RetentionKind.APPLICATION_ENTRY, Optional.empty()));
    for (DefinitionId entry : entryPoints) {
      program
          .definition(entry)
          .orElseThrow(() -> new IllegalArgumentException("entry definition is absent: " + entry));
      causes.putIfAbsent(
          entry.group(), new RetentionCause(RetentionKind.HOST_ENTRY, Optional.empty()));
    }
    int previous;
    do {
      previous = retained.size();
      for (var annotation : artifact.metadata().annotations()) {
        var target = retainedTarget(annotation.target(), artifact, retained);
        if (target.isEmpty()) continue;
        retain(
            causes,
            annotation.annotation().group(),
            RetentionKind.ANNOTATION,
            target.orElseThrow());
        var values =
            new CoreWalker() {
              @Override
              protected void visitLink(CoreDefinitionLink link) {
                retain(
                    causes,
                    program.resolve(annotation.annotation(), (DefinitionReference) link).group(),
                    RetentionKind.ANNOTATION_VALUE,
                    annotation.annotation().group());
              }
            };
        annotation.values().forEach(values::walkAnnotationValue);
      }
      for (var record : definitions) {
        var parents = new java.util.ArrayList<CoreType>();
        if (record.definition() instanceof CoreDefinition.Aggregate aggregate) {
          aggregate.parentType().ifPresent(parents::add);
          aggregate.conformances().forEach(conformance -> parents.add(conformance.interfaceType()));
        } else if (record.definition() instanceof CoreDefinition.Interface declared) {
          parents.addAll(declared.directParents());
        } else if (record.definition() instanceof CoreDefinition.BuiltinConformance conformance) {
          parents.add(conformance.interfaceType());
        }
        parents.stream()
            .flatMap(type -> CoreTypes.links(type).stream())
            .map(link -> program.resolve(record.id(), (DefinitionReference) link).group())
            .filter(retained::contains)
            .findFirst()
            .ifPresent(
                parent -> retain(causes, record.id().group(), RetentionKind.SUBTYPE, parent));
        if (!retained.contains(record.id().group())) continue;
        CoreTree.dependencies(record.definition())
            .forEach(
                dependency ->
                    causes.putIfAbsent(
                        program
                            .resolve(record.id(), (DefinitionReference) dependency.target())
                            .group(),
                        new RetentionCause(
                            RetentionKind.DEFINITION_LINK,
                            Optional.of(record.id().group()),
                            Optional.of(dependency.kind()))));
      }
      for (var occurrence : artifact.authoring().occurrences()) {
        var source =
            occurrence.representedDefinitions().stream()
                .map(DefinitionId::group)
                .filter(retained::contains)
                .findFirst();
        if (source.isEmpty()) continue;
        occurrence
            .representedDefinitions()
            .forEach(
                id ->
                    retain(
                        causes, id.group(), RetentionKind.AUTHORING_GROUP, source.orElseThrow()));
        occurrence
            .references()
            .values()
            .forEach(
                id ->
                    retain(
                        causes,
                        id.representative().group(),
                        RetentionKind.AUTHORING_REFERENCE,
                        source.orElseThrow()));
      }
      Map<String, DefinitionGroupId> owners = new LinkedHashMap<>();
      for (var binding : artifact.namespace().bindings()) {
        if (retained.contains(binding.definition().group()) && binding.ownerName().isEmpty()) {
          owners.putIfAbsent(
              binding.packageName() + "/" + binding.name(), binding.definition().group());
        }
      }
      for (var binding : artifact.namespace().bindings()) {
        if (binding.ownerName().isPresent()
            && owners.containsKey(
                binding.packageName() + "/" + binding.ownerName().orElseThrow())) {
          retain(
              causes,
              binding.definition().group(),
              RetentionKind.OWNED_MEMBER,
              owners.get(binding.packageName() + "/" + binding.ownerName().orElseThrow()));
        }
      }
    } while (retained.size() != previous);
    return new Analysis(
        new CoreArtifact(
            new CoreProgram(
                program.groups().stream().filter(group -> retained.contains(group.id())).toList()),
            CoreNamespace.create(
                artifact.namespace().bindings().stream()
                    .filter(binding -> retained.contains(binding.definition().group()))
                    .toList()),
            new CoreAuthoringMap(
                artifact.authoring().occurrences().stream()
                    .filter(
                        occurrence -> retained.contains(occurrence.id().representative().group()))
                    .toList(),
                artifact.entryPoint()),
            new CoreMetadata(
                artifact.metadata().annotations().stream()
                    .filter(
                        annotation ->
                            retainedTarget(annotation.target(), artifact, retained).isPresent())
                    .toList())),
        causes);
  }

  private static Optional<DefinitionGroupId> retainedTarget(
      CoreAnnotationTarget target, CoreArtifact artifact, Set<DefinitionGroupId> retained) {
    return switch (target) {
      case CoreAnnotationTarget.Package targetPackage ->
          artifact.namespace().bindings().stream()
              .filter(
                  binding ->
                      binding.packageName().equals(targetPackage.packageName())
                          && retained.contains(binding.definition().group()))
              .map(binding -> binding.definition().group())
              .findFirst();
      case CoreAnnotationTarget.Definition definition ->
          Optional.of(definition.occurrence().representative().group()).filter(retained::contains);
      case CoreAnnotationTarget.Field field ->
          Optional.of(field.owner().representative().group()).filter(retained::contains);
      case CoreAnnotationTarget.Parameter parameter ->
          Optional.of(parameter.callable().representative().group()).filter(retained::contains);
      case CoreAnnotationTarget.Local local ->
          Optional.of(local.callable().representative().group()).filter(retained::contains);
    };
  }
}
