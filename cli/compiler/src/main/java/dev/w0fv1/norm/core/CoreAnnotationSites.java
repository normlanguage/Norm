package dev.w0fv1.norm.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CoreAnnotationSites {
  private CoreAnnotationSites() {}

  public static Map<CoreAnnotationPath, CoreDefinitionLink> references(CoreDefinition definition) {
    var result = new LinkedHashMap<CoreAnnotationPath, CoreDefinitionLink>();
    switch (definition) {
      case CoreDefinition.Callable callable -> {
        interceptors(result, CoreAnnotationPath.ROOT, callable.interceptors());
        for (int index = 0; index < callable.parameters().size(); index++)
          interceptors(
              result,
              CoreAnnotationPath.ROOT.child(CoreAnnotationPath.Kind.PARAMETER, index),
              callable.parameters().get(index).interceptors());
      }
      case CoreDefinition.Aggregate aggregate -> {
        for (var field : aggregate.fields())
          interceptors(
              result,
              CoreAnnotationPath.ROOT.child(CoreAnnotationPath.Kind.FIELD, field.ordinal()),
              field.interceptors());
      }
      case CoreDefinition.Enum enumeration -> {
        for (int index = 0; index < enumeration.variants().size(); index++) {
          var variant = enumeration.variants().get(index);
          for (var field : variant.fields())
            interceptors(
                result,
                CoreAnnotationPath.ROOT
                    .child(CoreAnnotationPath.Kind.VARIANT, index)
                    .child(CoreAnnotationPath.Kind.FIELD, field.ordinal()),
                field.interceptors());
        }
      }
      default -> {}
    }
    return Map.copyOf(result);
  }

  public static Map<CoreAnnotationPath, CoreDefinitionLink> references(
      CoreAnnotationApplication application) {
    var result = new LinkedHashMap<CoreAnnotationPath, CoreDefinitionLink>();
    result.put(CoreAnnotationPath.ROOT, new DefinitionReference.External(application.annotation()));
    values(result, CoreAnnotationPath.ROOT, application.values());
    return Map.copyOf(result);
  }

  public static Map<CoreAnnotationPath, CoreDefinitionLink> references(
      List<CoreAnnotationValue> values) {
    var result = new LinkedHashMap<CoreAnnotationPath, CoreDefinitionLink>();
    values(result, CoreAnnotationPath.ROOT, values);
    return Map.copyOf(result);
  }

  private static void interceptors(
      Map<CoreAnnotationPath, CoreDefinitionLink> result,
      CoreAnnotationPath prefix,
      List<CoreInterceptor> interceptors) {
    for (int index = 0; index < interceptors.size(); index++) {
      var path = prefix.child(CoreAnnotationPath.Kind.INTERCEPTOR, index);
      var interceptor = interceptors.get(index);
      result.put(path, interceptor.annotation());
      values(result, path, interceptor.values());
    }
  }

  private static void values(
      Map<CoreAnnotationPath, CoreDefinitionLink> result,
      CoreAnnotationPath prefix,
      List<CoreAnnotationValue> values) {
    for (int index = 0; index < values.size(); index++)
      value(result, prefix.child(CoreAnnotationPath.Kind.VALUE, index), values.get(index));
  }

  private static void value(
      Map<CoreAnnotationPath, CoreDefinitionLink> result,
      CoreAnnotationPath path,
      CoreAnnotationValue value) {
    switch (value.value()) {
      case CoreAnnotationValue.ListValue list -> {
        for (int index = 0; index < list.values().size(); index++)
          value(
              result,
              path.child(CoreAnnotationPath.Kind.LIST_ITEM, index),
              list.values().get(index));
      }
      case CoreAnnotationReference.CallableReference callable ->
          result.put(path, callable.callable());
      case CoreAnnotationReference.ClassReference type ->
          nominal(result, path, type.reflectedType());
      case CoreAnnotationReference.FieldReference field -> nominal(result, path, field.ownerType());
      case CoreAnnotationReference.EnumReference ignored -> nominal(result, path, value.type());
      default -> {}
    }
  }

  private static void nominal(
      Map<CoreAnnotationPath, CoreDefinitionLink> result, CoreAnnotationPath path, CoreType type) {
    if (type instanceof CoreType.Declared declared
        && declared.constructor() instanceof CoreTypeConstructor.User user)
      result.put(path, user.definition());
  }
}
