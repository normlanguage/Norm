package dev.w0fv1.norm.jvm;

public interface JavaCallTarget {
  String owner();

  String name();

  String descriptor();

  JavaCallableKind kind();
}
