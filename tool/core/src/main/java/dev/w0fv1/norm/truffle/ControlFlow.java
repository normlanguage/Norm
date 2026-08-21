package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.nodes.ControlFlowException;

final class ControlFlow {
  private ControlFlow() {}

  static final class Return extends ControlFlowException {
    private static final long serialVersionUID = 1L;
    final transient Object value;

    Return(Object value) {
      this.value = value;
    }
  }

  static final class Break extends ControlFlowException {
    private static final long serialVersionUID = 1L;
    static final Break INSTANCE = new Break();
  }

  static final class Continue extends ControlFlowException {
    private static final long serialVersionUID = 1L;
    static final Continue INSTANCE = new Continue();
  }
}
