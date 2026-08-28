package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationAbi;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CoreFunctionInterceptorProtocol(
    DefinitionId interfaceId, DefinitionId before, DefinitionId around, DefinitionId after) {
  private static final String NAME = "FunctionInterceptor";

  public static Optional<CoreFunctionInterceptorProtocol> resolve(CoreProgram program) {
    Optional<CoreAnnotationProtocolSupport.Protocol> found =
        CoreAnnotationProtocolSupport.locate(
            program,
            NAME,
            nominal ->
                AnnotationAbi.isFunctionInterceptor(
                    nominal.module(), nominal.packageName(), nominal.name()));
    if (found.isEmpty()) return Optional.empty();
    CoreAnnotationProtocolSupport.Protocol protocol = found.orElseThrow();
    CoreAnnotationProtocolSupport.requireParent(
        program, NAME, protocol, AnnotationAbi.FUNCTION_TARGET);
    Map<String, DefinitionId> methods =
        CoreAnnotationProtocolSupport.methods(program, NAME, protocol);
    if (methods.size() != 3) {
      throw new IllegalArgumentException(
          "FunctionInterceptor must declare three lifecycle methods");
    }
    DefinitionId before =
        CoreAnnotationProtocolSupport.require(methods, NAME, AnnotationAbi.BEFORE);
    DefinitionId around =
        CoreAnnotationProtocolSupport.require(methods, NAME, AnnotationAbi.AROUND);
    DefinitionId after = CoreAnnotationProtocolSupport.require(methods, NAME, AnnotationAbi.AFTER);
    verifyBefore(program, protocol, before);
    verifyAround(program, protocol, around);
    verifyAfter(program, protocol, after);
    return Optional.of(new CoreFunctionInterceptorProtocol(protocol.id(), before, around, after));
  }

  private static void verifyBefore(
      CoreProgram program, CoreAnnotationProtocolSupport.Protocol protocol, DefinitionId id) {
    CoreDefinition.InterfaceMethod method = CoreAnnotationProtocolSupport.method(program, NAME, id);
    if (!CoreAnnotationProtocolSupport.receiver(program, protocol, id, method, List.of())
        || !method.typeParameters().isEmpty()
        || !method.returnType().equals(CoreType.VOID)
        || method.parameterTypes().size() != 1
        || !CoreAnnotationProtocolSupport.builtin(
            method.parameterTypes().getFirst(), "std.core.FunctionContext", List.of())) {
      throw new IllegalArgumentException("FunctionInterceptor.before has an invalid ABI");
    }
  }

  private static void verifyAround(
      CoreProgram program, CoreAnnotationProtocolSupport.Protocol protocol, DefinitionId id) {
    CoreDefinition.InterfaceMethod method = CoreAnnotationProtocolSupport.method(program, NAME, id);
    CoreType parameter = new CoreType.Parameter(0, CoreNullability.NON_NULL);
    if (!CoreAnnotationProtocolSupport.receiver(program, protocol, id, method, List.of())
        || method.typeParameters().size() != 1
        || method.typeParameters().getFirst().upperBound().isPresent()
        || !method.returnType().equals(parameter)
        || method.parameterTypes().size() != 1
        || !CoreAnnotationProtocolSupport.builtin(
            method.parameterTypes().getFirst(),
            "std.core.FunctionInvocation",
            List.of(parameter))) {
      throw new IllegalArgumentException("FunctionInterceptor.around has an invalid ABI");
    }
  }

  private static void verifyAfter(
      CoreProgram program, CoreAnnotationProtocolSupport.Protocol protocol, DefinitionId id) {
    CoreDefinition.InterfaceMethod method = CoreAnnotationProtocolSupport.method(program, NAME, id);
    if (!CoreAnnotationProtocolSupport.receiver(program, protocol, id, method, List.of())
        || !method.typeParameters().isEmpty()
        || !method.returnType().equals(CoreType.VOID)
        || method.parameterTypes().size() != 2
        || !CoreAnnotationProtocolSupport.builtin(
            method.parameterTypes().get(0), "std.core.FunctionContext", List.of())
        || !CoreAnnotationProtocolSupport.builtin(
            method.parameterTypes().get(1), "std.core.FunctionCompletion", List.of())) {
      throw new IllegalArgumentException("FunctionInterceptor.after has an invalid ABI");
    }
  }
}
