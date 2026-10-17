package dev.w0fv1.norm.truffle;

final class RuntimeValueExecution {
  private static final ThreadLocal<ExecutionState> CURRENT = new ThreadLocal<>();

  private RuntimeValueExecution() {}

  static ExecutionState current() {
    return CURRENT.get();
  }

  static Scope enter(ExecutionState execution) {
    var previous = CURRENT.get();
    CURRENT.set(execution);
    return new Scope(previous);
  }

  record Scope(ExecutionState previous) implements AutoCloseable {
    @Override
    public void close() {
      if (previous == null) CURRENT.remove();
      else CURRENT.set(previous);
    }
  }
}
