package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.SourceFile;
import java.net.URI;
import org.junit.jupiter.api.Test;

final class SourceHeaderTest {
  @Test
  void readsPackageAfterPackageAnnotation() {
    SourceFile source =
        SourceFile.of(
            new DocumentId(URI.create("memory:///header.norm")),
            "@Generated(name: \"test\") package sample.metadata");

    assertEquals("sample.metadata", SourceHeader.parse(source).packageName().orElseThrow());
  }
}
