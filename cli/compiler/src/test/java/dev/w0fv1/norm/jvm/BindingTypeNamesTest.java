package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BindingTypeNamesTest {
  @Test
  void preservesNestedTypeParametersInNominalRelations() {
    var variable =
        new JavaBindingTypeVariable(
            "V", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT));
    var list =
        new JavaReferenceType(
            "sample.MutableList",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(variable)));
    var multimap =
        new JavaReferenceType(
            "sample.MutableMultimap",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(list)));
    var names =
        new BindingTypeNames(
            Map.of(
                "sample.MutableList", "MutableList", "sample.MutableMultimap", "MutableMultimap"),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());

    assertEquals(
        "MutableMultimap<MutableList<V>>", BindingTypeNames.normBoundType(multimap, names));
    assertEquals(
        "MutableMultimap<MutableList<V?>?>?", BindingTypeNames.normType(multimap, names, false));
  }

  @Test
  void preservesTypeParametersInStandardCollectionAndComparableBounds() {
    var variable =
        new JavaBindingTypeVariable(
            "V", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT));
    var list =
        new JavaReferenceType(
            "java.util.List",
            JavaReferenceKind.LIST,
            List.of(JavaBindingTypeArgument.exact(variable)));
    var map =
        new JavaReferenceType(
            "java.util.Map",
            JavaReferenceKind.MAP,
            List.of(
                JavaBindingTypeArgument.exact(variable), JavaBindingTypeArgument.exact(variable)));
    var comparable =
        new JavaReferenceType(
            "java.lang.Comparable",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(list)));
    var names = new BindingTypeNames(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

    assertEquals("MutableList<V>", BindingTypeNames.normBoundType(list, names));
    assertEquals("MutableMap<V, V>", BindingTypeNames.normBoundType(map, names));
    assertEquals("Comparable<MutableList<V>>", BindingTypeNames.normBoundType(comparable, names));
    assertEquals("MutableList<V?>?", BindingTypeNames.normType(list, names, false));
    assertEquals("MutableMap<V?, V?>?", BindingTypeNames.normType(map, names, false));
  }

  @Test
  void preservesTypeParametersThroughPublisherAndCallbackRelations() {
    var variable =
        new JavaBindingTypeVariable(
            "V", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT));
    var publisher =
        new JavaReferenceType(
            "org.reactivestreams.Publisher",
            JavaReferenceKind.PUBLISHER,
            List.of(JavaBindingTypeArgument.exact(variable)));
    var callback = new JavaCallbackType("sample.Mapper", "apply", List.of(variable), variable);
    var callbackHolder =
        new JavaReferenceType(
            "sample.CallbackHolder",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(callback)));
    var optional =
        new JavaReferenceType(
            "java.util.Optional",
            JavaReferenceKind.OPTIONAL,
            List.of(JavaBindingTypeArgument.exact(variable)));
    var optionalHolder =
        new JavaReferenceType(
            "sample.OptionalHolder",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(optional)));
    var names =
        new BindingTypeNames(
            Map.of(
                "sample.CallbackHolder",
                "CallbackHolder",
                "sample.OptionalHolder",
                "OptionalHolder"),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());

    assertEquals("Publisher<V>", BindingTypeNames.normBoundType(publisher, names));
    assertEquals("Publisher<V?>?", BindingTypeNames.normType(publisher, names, false));
    assertEquals(
        "CallbackHolder<Function<V(V)>>", BindingTypeNames.normBoundType(callbackHolder, names));
    assertEquals(
        "CallbackHolder<Function<V?(V?)>?>?",
        BindingTypeNames.normType(callbackHolder, names, false));
    assertEquals("OptionalHolder<V?>", BindingTypeNames.normBoundType(optionalHolder, names));
  }
}
