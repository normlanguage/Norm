package dev.w0fv1.norm.jvm;

import dev.w0fv1.norm.core.*;
import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.value.CompilationScope;
import java.util.*;

final class JavaHostSurface {
  private JavaHostSurface() {}

  static Set<DefinitionId> roots(
      CoreArtifact artifact,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      CompilationScope scope,
      Set<DocumentId> bindingDocuments) {
    var roots = new LinkedHashSet<DefinitionId>();
    for (var binding : artifact.namespace().bindings()) {
      if (binding.ownerName().isPresent()) continue;
      var document = artifact.authoring().origin(binding.occurrence()).rootSpan().source().id();
      if (!scope.coordinates().containsKey(document) || bindingDocuments.contains(document))
        continue;
      if (managed(artifact, binding)
          || javaRelation(artifact.program(), binding.definition(), javaTypes, new HashSet<>()))
        roots.add(binding.definition());
    }
    var javaCalls =
        artifact.program().definitions().stream()
            .filter(
                record ->
                    CoreReachability.intrinsics(record.definition()).stream()
                        .anyMatch(
                            intrinsic ->
                                intrinsic == dev.w0fv1.norm.abi.IntrinsicId.JAR_INVOKE
                                    || intrinsic == dev.w0fv1.norm.abi.IntrinsicId.JAR_INVOKE_VOID))
            .map(CoreDefinitionRecord::id)
            .collect(java.util.stream.Collectors.toSet());
    for (var record : artifact.program().definitions()) {
      if (!(record.definition() instanceof CoreDefinition.Interface contract)) continue;
      var nominal = contract.nominalType();
      if (!javaTypes.containsKey(
          new JarBindingClassReference.Nominal(
              nominal.module(), nominal.packageName(), nominal.name()))) continue;
      contract
          .declaredMethods()
          .forEach(
              method ->
                  javaCalls.add(
                      artifact.program().resolve(record.id(), (DefinitionReference) method)));
    }
    Map<DefinitionId, Set<Integer>> calls = new LinkedHashMap<>();
    for (var id : javaCalls) {
      var definition = artifact.program().definition(id).orElseThrow();
      int count =
          switch (definition) {
            case CoreDefinition.Callable callable -> callable.parameters().size();
            case CoreDefinition.MethodSignature signature -> signature.parameterTypes().size();
            default -> 0;
          };
      var parameters = new LinkedHashSet<Integer>();
      for (int index = 0; index < count; index++) parameters.add(index);
      calls.put(id, parameters);
    }
    boolean expanded;
    do {
      int size = calls.values().stream().mapToInt(Set::size).sum();
      CoreReachability.callArgumentTypes(artifact.program(), calls)
          .forEach(
              (owner, types) -> {
                var occurrences = artifact.authoring().occurrences(owner);
                if (occurrences.stream()
                    .noneMatch(
                        occurrence -> {
                          var document =
                              artifact.authoring().origin(occurrence.id()).rootSpan().source().id();
                          return scope.coordinates().containsKey(document)
                              && !bindingDocuments.contains(document);
                        })) return;
                for (var argumentType : types) {
                  if (argumentType.type() instanceof CoreType.Declared declared
                      && declared.constructor() instanceof CoreTypeConstructor.Builtin builtin
                      && builtin.id().value().equals("std.core.Class")) {
                    calls
                        .computeIfAbsent(owner, ignored -> new LinkedHashSet<>())
                        .addAll(argumentType.callerParameters());
                    for (var argument : declared.arguments()) {
                      CoreTypes.links(argument)
                          .forEach(
                              link ->
                                  roots.add(
                                      artifact
                                          .program()
                                          .resolve(owner, (DefinitionReference) link)));
                    }
                  }
                }
              });
      expanded = calls.values().stream().mapToInt(Set::size).sum() != size;
    } while (expanded);
    return Set.copyOf(roots);
  }

  static boolean managed(CoreArtifact artifact, CoreBinding binding) {
    if (!(artifact.program().definition(binding.definition()).orElseThrow()
        instanceof CoreDefinition.Aggregate aggregate)) return false;
    return aggregate.dispatch().stream()
        .anyMatch(
            dispatch ->
                artifact
                        .program()
                        .definition(
                            artifact
                                .program()
                                .resolve(
                                    binding.definition(), (DefinitionReference) dispatch.target()))
                        .orElseThrow()
                    instanceof CoreDefinition.MethodSignature);
  }

  private static boolean javaRelation(
      CoreProgram program,
      DefinitionId definition,
      Map<JarBindingClassReference.Nominal, String> javaTypes,
      Set<DefinitionId> visited) {
    if (!visited.add(definition)) return false;
    var declaration = program.definition(definition).orElseThrow();
    CoreNominalTypeKey nominal =
        switch (declaration) {
          case CoreDefinition.Aggregate value -> value.nominalType();
          case CoreDefinition.Interface value -> value.nominalType();
          case CoreDefinition.Enum value -> value.nominalType();
          default -> null;
        };
    if (nominal != null
        && javaTypes.containsKey(
            new JarBindingClassReference.Nominal(
                nominal.module(), nominal.packageName(), nominal.name()))) return true;
    var parents = new ArrayList<CoreType>();
    if (declaration instanceof CoreDefinition.Aggregate aggregate) {
      aggregate.parentType().ifPresent(parents::add);
      aggregate.conformances().forEach(value -> parents.add(value.interfaceType()));
    } else if (declaration instanceof CoreDefinition.Interface contract)
      parents.addAll(contract.directParents());
    return parents.stream()
        .map(type -> CoreTypes.absolute(type, definition, program))
        .filter(CoreType.Declared.class::isInstance)
        .map(CoreType.Declared.class::cast)
        .map(CoreType.Declared::constructor)
        .filter(CoreTypeConstructor.User.class::isInstance)
        .map(CoreTypeConstructor.User.class::cast)
        .map(parent -> ((DefinitionReference.External) parent.definition()).definition())
        .anyMatch(parent -> javaRelation(program, parent, javaTypes, visited));
  }

  static Set<DefinitionId> references(
      CoreProgram program, DefinitionId owner, Collection<CoreType> types) {
    var result = new LinkedHashSet<DefinitionId>();
    types.stream()
        .flatMap(type -> CoreTypes.links(type).stream())
        .forEach(link -> result.add(program.resolve(owner, (DefinitionReference) link)));
    return Set.copyOf(result);
  }
}
