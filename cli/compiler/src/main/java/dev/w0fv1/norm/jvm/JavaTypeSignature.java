package dev.w0fv1.norm.jvm;

public sealed interface JavaTypeSignature
    permits JavaArrayTypeSignature,
        JavaClassTypeSignature,
        JavaPrimitiveTypeSignature,
        JavaTypeVariableSignature {}
