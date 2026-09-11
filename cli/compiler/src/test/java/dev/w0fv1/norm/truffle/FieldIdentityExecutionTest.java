package dev.w0fv1.norm.truffle;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.testing.NormTestKit;
import org.junit.jupiter.api.Test;

final class FieldIdentityExecutionTest {
  @Test
  void derivesStableTypedIdentitiesFromAnnotatedFields() {
    assertEquals(
        "keys" + System.lineSeparator(),
        NormTestKit.run(
            """
            import std.annotation.IdentityField
            import std.annotation.RuntimeRetention
            import std.annotation.BinaryRetention
            import std.core.Exception
            annotation Id implements IdentityField, RuntimeRetention {}
            annotation HiddenId implements IdentityField, BinaryRetention {}
            class Hidden { @HiddenId() Long id }
            class Item { @Id() Long? id = null }
            class Other { @Id() Long? id = null }
            Void main() {
              require(condition: !Hidden.id.field.hasAnnotation(IdentityField.class), message: "binary metadata is not exposed")
              require(condition: Item.id.field.hasAnnotation(Id.class), message: "exact annotation query")
              var item = Item(id: 42)
              List<FieldIdentity> identities = []
              for field : classOf(item).fields() {
                if field.hasAnnotation(IdentityField.class) { identities.add(field.identity(item)) }
              }
              require(condition: identities.size() == 1, message: "identity marker")
              var fresh = Item.id.field.identity(Item(id: 42))
              require(condition: identities[0] == fresh, message: "reloaded entity keeps identity")
              require(condition: identities[0] != Other.id.field.identity(Other(id: 42)), message: "entity types separate identities")
              Map<FieldIdentity, Boolean> indexed = Map<>()
              indexed.put(key: identities[0], value: true)
              require(condition: indexed.containsKey(fresh), message: "identity hashing")
              var rejected = false
              try { Item.id.field.identity(Item()) }
              catch Exception failure { rejected = true }
              require(condition: rejected, message: "null identity is rejected")
              printLine("keys")
            }
            """));
  }
}
