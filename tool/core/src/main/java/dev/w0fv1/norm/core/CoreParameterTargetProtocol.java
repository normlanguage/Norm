package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationAbi;
import java.util.Optional;

public record CoreParameterTargetProtocol(
    DefinitionId interfaceId, DefinitionId before, DefinitionId after) {
  private static final String NAME = "ParameterTarget";

  public static Optional<CoreParameterTargetProtocol> resolve(CoreProgram program) {
    return CoreAnnotationProtocolSupport.typedTarget(
            program,
            NAME,
            nominal ->
                AnnotationAbi.isParameterTarget(
                    nominal.module(), nominal.packageName(), nominal.name()),
            "std.core.ParameterContext")
        .map(
            protocol ->
                new CoreParameterTargetProtocol(
                    protocol.interfaceId(), protocol.before(), protocol.after()));
  }
}
