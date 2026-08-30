package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.ExceptionAbi;
import dev.w0fv1.norm.execution.RuntimeErrorCode;

final class NormThrownException extends NormGuestException {
  private static final long serialVersionUID = 1L;
  final transient RuntimeValues.ObjectValue value;

  NormThrownException(RuntimeValues.ObjectValue value, Node location) {
    super(RuntimeErrorCode.UNCAUGHT_EXCEPTION, message(value), location);
    this.value = java.util.Objects.requireNonNull(value, "value");
  }

  private static String message(RuntimeValues.ObjectValue value) {
    if (value.fields.length > ExceptionAbi.MESSAGE_FIELD_ORDINAL
        && value.fields[ExceptionAbi.MESSAGE_FIELD_ORDINAL] instanceof String message) {
      return message;
    }
    return value.objectInfo.name();
  }
}
