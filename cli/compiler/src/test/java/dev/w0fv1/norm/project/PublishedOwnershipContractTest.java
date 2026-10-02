package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.*;

import dev.w0fv1.norm.jvm.JavaResourceOwnership;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class PublishedOwnershipContractTest {
  @Test
  void releasedAbi2BindingsReadAsOwnedWithoutBorrowedMembers() throws Exception {
    Path archive = Path.of(getClass().getResource("/fixtures/binding-abi2/fx-base-1.nar").toURI());
    var module = new ModuleArchiveReader().read(archive);
    assertEquals("fx.base", module.descriptor().name());
    assertTrue(
        module.descriptor().binding().orElseThrow().api().stream()
            .allMatch(type -> type.borrowed().isEmpty()));
    var calls = module.binding().orElseThrow().generated().calls();
    assertFalse(calls.isEmpty());
    assertTrue(
        calls.values().stream().allMatch(call -> call.ownership() == JavaResourceOwnership.OWNED));
  }
}
