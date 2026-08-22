package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.bound.BoundProgram;

public interface ExecutionBackend {
  void execute(BoundProgram program, ExecutionContext context);
}
