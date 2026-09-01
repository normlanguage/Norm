package dev.w0fv1.norm.jvm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

final class JavaPlatformCallbacks {
  private static final JavaClassTypeSignature OBJECT =
      JavaClassTypeSignature.raw("java.lang.Object");

  private JavaPlatformCallbacks() {}

  static Optional<JavaCallbackType> project(
      JavaClassTypeSignature type, Function<JavaTypeSignature, JavaBindingType> mapper) {
    CallbackShape shape = shape(type.binaryName());
    if (shape == null) return Optional.empty();
    List<JavaTypeArgument> arguments =
        type.segments().stream().flatMap(segment -> segment.arguments().stream()).toList();
    if (!arguments.isEmpty() && arguments.size() != shape.arity()) {
      return Optional.empty();
    }
    List<JavaBindingType> parameters = new ArrayList<>();
    for (int index : shape.inputArguments()) {
      JavaTypeSignature input = argument(arguments, index, JavaTypeVariance.SUPER);
      if (input == null) return Optional.empty();
      JavaBindingType parameter = mapper.apply(input);
      if (parameter == null) return Optional.empty();
      parameters.add(parameter);
    }
    JavaBindingType returnType = shape.fixedReturnType();
    if (shape.outputArgument() >= 0) {
      JavaTypeSignature output =
          argument(arguments, shape.outputArgument(), JavaTypeVariance.EXTENDS);
      if (output == null) return Optional.empty();
      JavaBindingType projected = mapper.apply(output);
      if (projected == null) return Optional.empty();
      returnType = projected;
    }
    return Optional.of(
        new JavaCallbackType(type.binaryName(), shape.method(), parameters, returnType));
  }

  private static JavaTypeSignature argument(
      List<JavaTypeArgument> arguments, int index, JavaTypeVariance supportedVariance) {
    if (arguments.isEmpty()) return OBJECT;
    JavaTypeArgument argument = arguments.get(index);
    if (argument.variance() == JavaTypeVariance.UNBOUNDED) return OBJECT;
    if (argument.variance() != JavaTypeVariance.EXACT && argument.variance() != supportedVariance) {
      return null;
    }
    return argument.type().orElseThrow();
  }

  private static CallbackShape shape(String binaryName) {
    return switch (binaryName) {
      case "java.lang.Runnable" -> CallbackShape.voidCallback("run", 0);
      case "java.util.concurrent.Callable" -> CallbackShape.returning("call", 1, List.of(), 0);
      case "java.util.function.Supplier" -> CallbackShape.returning("get", 1, List.of(), 0);
      case "java.util.function.Function" -> CallbackShape.returning("apply", 2, List.of(0), 1);
      case "java.util.function.BiFunction" -> CallbackShape.returning("apply", 3, List.of(0, 1), 2);
      case "java.util.function.Consumer" -> CallbackShape.voidCallback("accept", 1, 0);
      case "java.util.function.BiConsumer" -> CallbackShape.voidCallback("accept", 2, 0, 1);
      case "java.util.function.Predicate" -> CallbackShape.booleanCallback("test", 1, 0);
      case "java.util.function.BiPredicate" -> CallbackShape.booleanCallback("test", 2, 0, 1);
      case "java.util.function.UnaryOperator" -> CallbackShape.returning("apply", 1, List.of(0), 0);
      case "java.util.function.BinaryOperator" ->
          CallbackShape.returning("apply", 1, List.of(0, 0), 0);
      default -> null;
    };
  }

  private record CallbackShape(
      String method,
      int arity,
      List<Integer> inputArguments,
      int outputArgument,
      JavaBindingType fixedReturnType) {
    private CallbackShape {
      inputArguments = List.copyOf(inputArguments);
    }

    private static CallbackShape returning(
        String method, int arity, List<Integer> inputs, int output) {
      return new CallbackShape(method, arity, inputs, output, null);
    }

    private static CallbackShape voidCallback(String method, int arity, Integer... inputs) {
      return new CallbackShape(method, arity, List.of(inputs), -1, JavaPrimitiveType.VOID);
    }

    private static CallbackShape booleanCallback(String method, int arity, Integer... inputs) {
      return new CallbackShape(method, arity, List.of(inputs), -1, JavaPrimitiveType.BOOLEAN);
    }
  }
}
