package dev.w0fv1.norm.core;

import dev.w0fv1.norm.value.AnnotationAbi;
import java.util.Optional;

public record CoreFieldInterceptorProtocol(
    DefinitionId interfaceId, DefinitionId before, DefinitionId after) {
  private static final String NAME = "FieldInterceptor";

  public static Optional<CoreFieldInterceptorProtocol> resolve(CoreProgram program) {
    return CoreAnnotationProtocolSupport.typedInterceptor(
            program,
            NAME,
            nominal ->
                AnnotationAbi.isFieldInterceptor(
                    nominal.module(), nominal.packageName(), nominal.name()),
            AnnotationAbi.FIELD_TARGET,
            "std.core.FieldContext")
        .map(
            protocol ->
                new CoreFieldInterceptorProtocol(
                    protocol.interfaceId(), protocol.before(), protocol.after()));
  }
}
