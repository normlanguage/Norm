package dev.w0fv1.norm.platform.process;

import dev.w0fv1.norm.platform.OperationControl;

public interface ProcessRunner {
  ProcessResult run(ProcessRequest request, OperationControl control);
}
