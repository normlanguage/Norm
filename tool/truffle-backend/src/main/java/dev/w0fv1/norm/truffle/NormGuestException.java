package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.execution.RuntimeErrorCode;

class NormGuestException extends AbstractTruffleException {
  private static final long serialVersionUID = 1L;
  private final RuntimeErrorCode code;

  NormGuestException(RuntimeErrorCode code, String message, Node location) {
    super(message, location);
    this.code = code;
  }

  RuntimeErrorCode code() {
    return code;
  }
}
