package dev.w0fv1.norm.bridge;

@FunctionalInterface
public interface JavaDirectCall {
  Object invoke(Object[] arguments) throws Throwable;
}
