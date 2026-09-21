package dev.w0fv1.norm.platform.process;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ProcessRequest(
    String executable,
    List<String> arguments,
    Path directory,
    Map<String, String> environment,
    byte[] input,
    int maximumOutputBytes) {
  public ProcessRequest {
    Objects.requireNonNull(executable);
    arguments = List.copyOf(arguments);
    directory = directory.toAbsolutePath().normalize();
    environment = Map.copyOf(environment);
    input = input.clone();
    if (executable.isBlank() || maximumOutputBytes < 0)
      throw new IllegalArgumentException("invalid process request");
  }

  @Override
  public byte[] input() {
    return input.clone();
  }
}
