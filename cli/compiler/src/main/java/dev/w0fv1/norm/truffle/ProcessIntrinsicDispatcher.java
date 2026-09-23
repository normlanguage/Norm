package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import dev.w0fv1.norm.platform.OperationControl;
import dev.w0fv1.norm.platform.PlatformDuration;
import dev.w0fv1.norm.platform.process.PlatformProcessException;
import dev.w0fv1.norm.platform.process.ProcessRequest;
import dev.w0fv1.norm.platform.process.ProcessResult;
import dev.w0fv1.norm.platform.process.ProcessRunner;
import java.nio.file.Path;
import java.util.LinkedHashMap;

final class ProcessIntrinsicDispatcher {
  private ProcessIntrinsicDispatcher() {}

  static IntrinsicOperation resolve() {
    return (receiver, arguments, type, context, location, annotations, execution) -> {
      var environment = new LinkedHashMap<String, String>();
      ((RuntimeValues.MapValue) arguments[3])
          .values.forEach((key, value) -> environment.put((String) key.value, (String) value));
      try {
        var request =
            new ProcessRequest(
                (String) arguments[0],
                ((RuntimeValues.ListValue) arguments[1])
                    .values.stream().map(String.class::cast).toList(),
                context.workingDirectory().resolve(Path.of((String) arguments[2])),
                environment,
                ((ByteSequence) ((RuntimeValues.OpaqueValue) arguments[4]).value).toArray(),
                (Integer) arguments[7]);
        var control =
            new OperationControl(
                context.cancellation(),
                new PlatformDuration((Long) arguments[5], (Integer) arguments[6]));
        ProcessResult result =
            execution
                .callbacks()
                .hostCall(() -> run(context.platform().processes(), request, control));
        return execution
            .values()
            .construct(
                type,
                execution,
                result.outcome().name(),
                result.exitCode() == null ? RuntimeValues.NullValue.INSTANCE : result.exitCode(),
                execution.values().bytes(new ByteSequence(result.stdout())),
                execution.values().bytes(new ByteSequence(result.stderr())),
                result.stdoutTruncated(),
                result.stderrTruncated());
      } catch (PlatformProcessException | java.nio.file.InvalidPathException failure) {
        return execution
            .values()
            .construct(
                type,
                execution,
                failure instanceof PlatformProcessException processFailure
                    ? processFailure.reason().name()
                    : "INVALID_REQUEST",
                RuntimeValues.NullValue.INSTANCE,
                execution.values().bytes(new ByteSequence(new byte[0])),
                execution.values().bytes(new ByteSequence(new byte[0])),
                false,
                false);
      }
    };
  }

  @TruffleBoundary
  private static ProcessResult run(
      ProcessRunner runner, ProcessRequest request, OperationControl control) {
    return runner.run(request, control);
  }
}
