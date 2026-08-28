package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationAbi;
import java.util.Optional;

public record CoreParameterInterceptorProtocol(
    DefinitionId interfaceId, DefinitionId before, DefinitionId after) {
  private static final String NAME = "ParameterInterceptor";

  public static Optional<CoreParameterInterceptorProtocol> resolve(CoreProgram program) {
    return CoreAnnotationProtocolSupport.typedInterceptor(
            program,
            NAME,
            nominal ->
                AnnotationAbi.isParameterInterceptor(
                    nominal.module(), nominal.packageName(), nominal.name()),
            AnnotationAbi.PARAMETER_TARGET,
            "std.core.ParameterContext")
        .map(
            protocol ->
                new CoreParameterInterceptorProtocol(
                    protocol.interfaceId(), protocol.before(), protocol.after()));
  }
}
