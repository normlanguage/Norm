package dev.w0fv1.norm.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class CoreDefaultArgumentVerifier {
  private CoreDefaultArgumentVerifier() {}

  static void verify(CoreProgram program, CoreAuthoringMap authoring, CoreBinding binding) {
    var parameters =
        binding.shape().parameters().stream()
            .filter(parameter -> parameter.defaultValue().isPresent())
            .toList();
    if (parameters.isEmpty()) return;
    var declaration = program.definition(binding.definition()).orElseThrow();
    Optional<CoreType> receiver =
        switch (declaration) {
          case CoreDefinition.Callable callable -> callable.receiverType();
          case CoreDefinition.MethodSignature method -> Optional.of(method.receiverType());
          default -> Optional.empty();
        };
    receiver = receiver.map(type -> CoreTypes.absolute(type, binding.definition(), program));
    var typeParameters = new ArrayList<CoreTypeParameter>();
    if (receiver.isPresent()) {
      var declared = (CoreType.Declared) receiver.orElseThrow();
      var owner =
          ((DefinitionReference.External)
                  ((CoreTypeConstructor.User) declared.constructor()).definition())
              .definition();
      typeParameters.addAll(typeParameters(program, owner));
    }
    typeParameters.addAll(typeParameters(program, binding.definition()));
    for (var parameter : parameters) {
      if (!(parameter.defaultValue().orElseThrow()
          instanceof CoreDefaultArgument.Resolved resolved)) {
        throw new IllegalArgumentException("parameter default implementation is unresolved");
      }
      var occurrence =
          authoring
              .occurrence(resolved.occurrence())
              .orElseThrow(
                  () -> new IllegalArgumentException("parameter default occurrence is absent"));
      var target = occurrence.id().representative();
      var definition = program.definition(target).orElseThrow();
      if (occurrence.role() != CoreDefinitionRole.DEFAULT_ARGUMENT
          || !(definition instanceof CoreDefinition.Callable factory)
          || factory.hasReceiver()
          || !factory.captureTypes().isEmpty()
          || !factory.interceptors().isEmpty()
          || factory.parameters().stream().anyMatch(value -> !value.interceptors().isEmpty())
          || factory.parameters().size() != (receiver.isPresent() ? 1 : 0)
          || !typeParameters.equals(typeParameters(program, target))
          || !CoreTypes.absolute(parameter.type(), binding.definition(), program)
              .equals(CoreTypes.absolute(factory.returnType(), target, program))
          || receiver.isPresent()
              && !receiver
                  .orElseThrow()
                  .equals(
                      CoreTypes.absolute(
                          factory.parameters().getFirst().type(), target, program))) {
        throw new IllegalArgumentException(
            "parameter default implementation does not match its declaration");
      }
    }
  }

  private static List<CoreTypeParameter> typeParameters(CoreProgram program, DefinitionId owner) {
    List<CoreTypeParameter> parameters =
        switch (program.definition(owner).orElseThrow()) {
          case CoreDefinition.Callable callable -> callable.typeParameters();
          case CoreDefinition.MethodSignature method -> method.typeParameters();
          case CoreDefinition.Aggregate aggregate -> aggregate.typeParameters();
          case CoreDefinition.Enum enumeration -> enumeration.typeParameters();
          case CoreDefinition.Interface contract -> contract.typeParameters();
          case CoreDefinition.BuiltinConformance conformance -> conformance.typeParameters();
        };
    return parameters.stream()
        .map(
            parameter ->
                new CoreTypeParameter(
                    parameter.index(),
                    parameter.upperBound().map(type -> CoreTypes.absolute(type, owner, program)),
                    parameter.defaultType().map(type -> CoreTypes.absolute(type, owner, program))))
        .toList();
  }
}
