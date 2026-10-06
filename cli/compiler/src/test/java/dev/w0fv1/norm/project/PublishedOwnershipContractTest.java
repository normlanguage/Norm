package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PublishedOwnershipContractTest {
  @Test
  void rejectsReleasedAbi2BindingsWithoutInferringOwnership() throws Exception {
    Path archive = Path.of(getClass().getResource("/fixtures/binding-abi2/fx-base-1.nar").toURI());
    var failure = assertThrows(IOException.class, () -> new ModuleArchiveReader().read(archive));
    assertEquals("unsupported published Java binding ABI", failure.getMessage());
  }
}
