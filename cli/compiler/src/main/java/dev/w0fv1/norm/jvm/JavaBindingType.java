package dev.w0fv1.norm.jvm;

public sealed interface JavaBindingType
    permits JavaArrayType,
        JavaBindingTypeVariable,
        JavaBoxedType,
        JavaCallbackType,
        JavaPrimitiveType,
        JavaReferenceType {
  String descriptor();

  String displayName();
}
