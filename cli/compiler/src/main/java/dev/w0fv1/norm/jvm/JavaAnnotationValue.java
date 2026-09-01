package dev.w0fv1.norm.jvm;

public sealed interface JavaAnnotationValue
    permits JavaAnnotationArrayValue,
        JavaAnnotationClassValue,
        JavaAnnotationConstantValue,
        JavaAnnotationEnumValue,
        JavaAnnotationNestedValue {}
