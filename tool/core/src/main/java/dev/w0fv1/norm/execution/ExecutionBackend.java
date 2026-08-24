package dev.w0fv1.norm.execution;

import dev.w0fv1.norm.core.CoreCompilation;

public interface ExecutionBackend {
  void execute(CoreCompilation compilation, ExecutionContext context);
}
