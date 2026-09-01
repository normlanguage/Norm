package dev.w0fv1.norm.jvm;

import java.util.List;

public record JavaClassTypeSignature(List<JavaClassTypeSegment> segments)
    implements JavaTypeSignature {
  public JavaClassTypeSignature {
    segments = List.copyOf(segments);
    if (segments.isEmpty()) throw new IllegalArgumentException("class type requires a segment");
  }

  public String binaryName() {
    return segments.stream()
        .map(JavaClassTypeSegment::name)
        .collect(java.util.stream.Collectors.joining("$"));
  }

  public static JavaClassTypeSignature raw(String binaryName) {
    return new JavaClassTypeSignature(List.of(new JavaClassTypeSegment(binaryName, List.of())));
  }
}
