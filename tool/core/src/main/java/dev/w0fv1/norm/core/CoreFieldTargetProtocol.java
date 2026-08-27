package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationAbi;
import java.util.Optional;

public record CoreFieldTargetProtocol(
    DefinitionId interfaceId, DefinitionId before, DefinitionId after) {
  private static final String NAME = "FieldTarget";

  public static Optional<CoreFieldTargetProtocol> resolve(CoreProgram program) {
    return CoreAnnotationProtocolSupport.typedTarget(
            program,
            NAME,
            nominal ->
                AnnotationAbi.isFieldTarget(
                    nominal.module(), nominal.packageName(), nominal.name()),
            "std.core.FieldContext")
        .map(
            protocol ->
                new CoreFieldTargetProtocol(
                    protocol.interfaceId(), protocol.before(), protocol.after()));
  }
}
