package dev.w0fv1.norm.builtin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class BuiltinCatalogTest {
  private final BuiltinCatalog catalog = BuiltinCatalog.standard();

  @Test
  void ownsEveryBuiltinSymbolAndIntrinsicExactlyOnce() {
    Set<String> symbolIds = new HashSet<>();
    catalog.symbols().keySet().forEach(id -> assertTrue(symbolIds.add(id.value())));

    Set<IntrinsicId> declared = catalog.declaredIntrinsics();
    assertEquals(Set.copyOf(java.util.EnumSet.allOf(IntrinsicId.class)), declared);
  }

  @Test
  void derivesArityParametersAndCapabilitiesFromTypeDefinitions() {
    BuiltinCatalog.TypeDefinition map = catalog.type("Map").orElseThrow();
    BuiltinCatalog.TypeDefinition stack = catalog.type("Stack").orElseThrow();

    assertEquals(java.util.List.of("K", "V"), map.typeParameters());
    assertEquals(2, map.arity());
    assertTrue(map.constructor().isPresent());
    assertTrue(map.iterable().isPresent());
    assertTrue(map.index().isPresent());
    assertEquals(IntrinsicId.MAP_ITERATOR, map.iterable().orElseThrow().intrinsic());
    assertEquals(IntrinsicId.MAP_INDEX_READ, map.index().orElseThrow().readIntrinsic());
    assertEquals(
        IntrinsicId.MAP_INDEX_WRITE, map.index().orElseThrow().writeIntrinsic().orElseThrow());
    assertEquals(IntrinsicId.STACK_ITERATOR, stack.iterable().orElseThrow().intrinsic());
  }

  @Test
  void resolvesMemberIntrinsicsWithoutSpellingBasedDispatch() {
    assertEquals(IntrinsicId.LIST_ADD, catalog.member("List", "add").orElseThrow().intrinsic());
    assertEquals(IntrinsicId.SIZE, catalog.member("Map", "size").orElseThrow().intrinsic());
    assertEquals(
        IntrinsicId.PAIR_FIRST_READ, catalog.member("Pair", "first").orElseThrow().intrinsic());
  }

  @Test
  void exposesLineOrientedOutputAndExplicitTestExpectations() {
    assertTrue(catalog.global("printLine").isPresent());
    assertTrue(catalog.global("expectedOutputLine").isPresent());
    assertTrue(catalog.global("print").isEmpty());
  }
}
