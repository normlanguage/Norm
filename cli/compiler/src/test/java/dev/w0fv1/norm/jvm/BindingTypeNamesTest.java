package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class BindingTypeNamesTest {
  @Test
  void nominalInstantiationPreservesCallerTypeArgumentsWhileJavaValuesRemainNullable() {
    var key =
        new JavaBindingTypeVariable(
            "K", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT));
    var value =
        new JavaBindingTypeVariable(
            "V", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT));
    var map =
        new JavaReferenceType(
            "java.util.LinkedHashMap",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(key), JavaBindingTypeArgument.exact(value)));
    var names =
        new BindingTypeNames(
            Map.of("java.util.LinkedHashMap", "LinkedHashMap"),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of("java.util.LinkedHashMap", 2),
            Map.of());
    assertEquals("LinkedHashMap<K, V>", BindingTypeNames.normType(map, names, true));
    assertEquals("V?", BindingTypeNames.normType(value, names, false));
  }

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
        "MutableMultimap<MutableList<V>>?", BindingTypeNames.normType(multimap, names, false));
  }

  @Test
  void preservesTypeParametersInStandardCollectionAndComparableBounds() {
    var variable =
        new JavaBindingTypeVariable(
            "V", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT));
    var list =
        new JavaReferenceType(
            "java.util.List",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(variable)));
    var map =
        new JavaReferenceType(
            "java.util.Map",
            JavaReferenceKind.OPAQUE,
            List.of(
                JavaBindingTypeArgument.exact(variable), JavaBindingTypeArgument.exact(variable)));
    var comparable =
        new JavaReferenceType(
            "java.lang.Comparable",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(list)));
    var names =
        new BindingTypeNames(
            Map.of(
                "java.util.List",
                "JavaList",
                "java.util.Map",
                "JavaMap",
                "java.lang.Comparable",
                "Comparable"),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());

    assertEquals("JavaList<V>", BindingTypeNames.normBoundType(list, names));
    assertEquals("JavaMap<V, V>", BindingTypeNames.normBoundType(map, names));
    assertEquals("Comparable<JavaList<V>>", BindingTypeNames.normBoundType(comparable, names));
    assertEquals("JavaList<V>?", BindingTypeNames.normType(list, names, false));
    assertEquals("JavaMap<V, V>?", BindingTypeNames.normType(map, names, false));
  }

  @Test
  void preservesTypeParametersThroughPublisherAndCallbackRelations() {
    var variable =
        new JavaBindingTypeVariable(
            "V", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT));
    var publisher =
        new JavaReferenceType(
            "org.reactivestreams.Publisher",
            JavaReferenceKind.OPAQUE,
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
            JavaReferenceKind.OPAQUE,
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
                "OptionalHolder",
                "java.util.Optional",
                "Optional",
                "org.reactivestreams.Publisher",
                "Publisher"),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());

    assertEquals("Publisher<V>", BindingTypeNames.normBoundType(publisher, names));
    assertEquals("Publisher<V>?", BindingTypeNames.normType(publisher, names, false));
    assertEquals(
        "CallbackHolder<Function<V(V)>>", BindingTypeNames.normBoundType(callbackHolder, names));
    assertEquals(
        "CallbackHolder<Function<V(V)>>?", BindingTypeNames.normType(callbackHolder, names, false));
    assertEquals(
        "OptionalHolder<Optional<V>>", BindingTypeNames.normBoundType(optionalHolder, names));
  }

  @Test
  void collectsNominalOwnersNestedInsideReferenceTypes() {
    var file = new JavaReferenceType("java.io.File", JavaReferenceKind.OPAQUE);
    var files =
        new JavaReferenceType(
            "java.util.List",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(file)));
    var references = new java.util.LinkedHashSet<String>();
    BindingTypeNames.collectReferences(files, references);
    assertEquals(java.util.Set.of("java.io.File", "java.util.List"), references);
  }
}
