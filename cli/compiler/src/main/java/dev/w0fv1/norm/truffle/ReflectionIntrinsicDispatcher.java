package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreValueCategory;

final class ReflectionIntrinsicDispatcher {
  private ReflectionIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return switch (intrinsic) {
      case VALUE_CLASS ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              new RuntimeValues.ClassValue(
                  type,
                  arguments[0] == null ? CoreType.NULL : RuntimeValues.runtimeType(arguments[0]),
                  annotations);
      case CLASS_IS_VALUE ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              ((RuntimeValues.ClassValue) receiver).reflectedType()
                      instanceof CoreType.Declared declared
                  && declared.category() == CoreValueCategory.VALUE;
      case FIELD_HAS_ANNOTATION ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            return field
                .binding()
                .select(field.ownerType(), execution)
                .hasFieldAnnotation(
                    field, ((RuntimeValues.ClassValue) arguments[0]).reflectedType());
          };
      case FIELD_IDENTITY ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            try {
              return field
                  .binding()
                  .select(field.ownerType(), execution)
                  .fieldIdentity(field, arguments[0], type, execution);
            } catch (IllegalArgumentException failure) {
              throw execution.values().javaException(failure, execution, location);
            }
          };
      case FIELD_IS_PUBLIC ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            return field.binding().select(field.ownerType(), execution).publicField(field);
          };
      case FIELD_BIND ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            try {
              return field
                  .binding()
                  .select(field.ownerType(), execution)
                  .bindField(field, arguments[0], type);
            } catch (IllegalArgumentException failure) {
              throw execution.values().javaException(failure, execution, location);
            }
          };
      case FIELD_HANDLE_READ ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              ((AnnotationRuntime.FieldHandle) ((RuntimeValues.OpaqueValue) receiver).value)
                  .read(execution);
      case FIELD_HANDLE_WRITE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            try {
              ((AnnotationRuntime.FieldHandle) ((RuntimeValues.OpaqueValue) receiver).value)
                  .write(arguments[0], execution);
            } catch (IllegalArgumentException failure) {
              throw execution.values().javaException(failure, execution, location);
            }
            return null;
          };
      case FIELD_WRITE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            try {
              field
                  .binding()
                  .select(field.ownerType(), execution)
                  .writeField(field, arguments[0], arguments[1], execution);
            } catch (IllegalArgumentException failure) {
              throw execution.values().javaException(failure, execution, location);
            }
            return null;
          };
      case FIELD_COPY ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            try {
              field
                  .binding()
                  .select(field.ownerType(), execution)
                  .copyField(field, arguments[0], arguments[1], execution);
            } catch (IllegalArgumentException failure) {
              throw execution.values().javaException(failure, execution, location);
            }
            return null;
          };
      case CLASS_LITERAL ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            if (annotations == null
                || !(type instanceof CoreType.Declared declared)
                || declared.arguments().size() != 1) {
              throw new IllegalStateException("class literal runtime type is unavailable");
            }
            return new RuntimeValues.ClassValue(type, declared.arguments().getFirst(), annotations);
          };
      case CLASS_NAME ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.ClassValue) receiver)
                .annotations()
                .name(((RuntimeValues.ClassValue) receiver).reflectedType());
          };
      case CLASS_ANNOTATION ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            if (execution == null) {
              throw new IllegalStateException("annotation execution is unavailable");
            }
            RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) receiver;
            return reflected
                .binding()
                .select(reflected.reflectedType(), execution)
                .annotation(reflected.reflectedType(), type, execution);
          };
      case CLASS_SNAPSHOT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            var reflected = (RuntimeValues.ClassValue) receiver;
            var selected = reflected.binding().select(reflected.reflectedType(), execution);
            return new RuntimeValues.ClassValue(
                reflected.type(), reflected.reflectedType(), new ReflectionBinding(selected, true));
          };
      case CLASS_FIELDS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) receiver;
            var result =
                reflected
                    .binding()
                    .select(reflected.reflectedType(), execution)
                    .fields(reflected.reflectedType(), type);
            if (!reflected.binding().frozen()) return result;
            return new RuntimeValues.ListValue(
                type,
                result.values.stream()
                    .map(field -> (Object) ((RuntimeValues.FieldValue) field).snapshot())
                    .toList());
          };
      case CLASS_FUNCTIONS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) receiver;
            var result =
                reflected
                    .binding()
                    .select(reflected.reflectedType(), execution)
                    .functions(reflected.reflectedType(), type);
            if (!reflected.binding().frozen()) return result;
            return new RuntimeValues.ListValue(
                type,
                result.values.stream()
                    .map(function -> (Object) ((RuntimeValues.Closure) function).snapshot())
                    .toList());
          };
      case CLASS_CONSTRUCTORS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.ClassValue reflected = (RuntimeValues.ClassValue) receiver;
            var result =
                reflected
                    .binding()
                    .select(reflected.reflectedType(), execution)
                    .constructors(reflected.reflectedType(), type);
            if (!reflected.binding().frozen()) return result;
            return new RuntimeValues.ListValue(
                type,
                result.values.stream()
                    .map(
                        constructor ->
                            (Object) ((RuntimeValues.ConstructorValue) constructor).snapshot())
                    .toList());
          };
      case FIELD_LITERAL ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            if (annotations == null || type == null) {
              throw new IllegalStateException("field literal runtime type is unavailable");
            }
            return annotations.field(type, (Integer) first);
          };
      case FIELD_NAME ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.FieldValue) receiver).name();
          };
      case FIELD_TYPE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            return new RuntimeValues.ClassValue(type, field.fieldType(), field.binding());
          };
      case FIELD_OWNER ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            return new RuntimeValues.ClassValue(type, field.ownerType(), field.binding());
          };
      case FIELD_ANNOTATION ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            if (execution == null) {
              throw new IllegalStateException("annotation execution is unavailable");
            }
            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            return field
                .binding()
                .select(field.ownerType(), execution)
                .fieldAnnotation(field, type, execution);
          };
      case FIELD_READ ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            RuntimeValues.FieldValue field = (RuntimeValues.FieldValue) receiver;
            return field
                .binding()
                .select(field.ownerType(), execution)
                .readField(field, first, execution);
          };
      case FUNCTION_SNAPSHOT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.Closure) receiver).snapshot(execution);
          };
      case FUNCTION_NAME ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.Closure function = (RuntimeValues.Closure) receiver;
            return function.annotations().functionName(function);
          };
      case FUNCTION_OWNER ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.Closure function = (RuntimeValues.Closure) receiver;
            Object owner = function.annotations().functionOwner(function, type);
            return owner instanceof RuntimeValues.ClassValue reflected
                    && function.reflectionBinding().frozen()
                ? reflected.snapshot()
                : owner;
          };
      case FUNCTION_PARAMETERS ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.Closure function = (RuntimeValues.Closure) receiver;
            return function.annotations().parameters(function, type);
          };
      case FUNCTION_ANNOTATION ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.Closure function = (RuntimeValues.Closure) receiver;
            return function.annotations().callableAnnotation(function, -1, type, execution);
          };
      case PARAMETER_ANNOTATION ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.ParameterValue parameter = (RuntimeValues.ParameterValue) receiver;
            var parameters = parameter.annotations().callable(parameter.function()).parameters();
            for (int index = 0; index < parameters.size(); index++) {
              if (parameters.get(index).name().equals(parameter.name()))
                return parameter
                    .annotations()
                    .callableAnnotation(parameter.function(), index, type, execution);
            }
            return RuntimeValues.NullValue.INSTANCE;
          };
      case PARAMETER_NAME ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.ParameterValue) receiver).name();
          };
      case PARAMETER_TYPE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.ParameterValue parameter = (RuntimeValues.ParameterValue) receiver;
            return new RuntimeValues.ClassValue(
                type, parameter.valueType(), parameter.function().reflectionBinding());
          };
      case PARAMETER_FUNCTION ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.ParameterValue) receiver).function();
          };
      case CONSTRUCTOR_OWNER ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.ConstructorValue constructor = (RuntimeValues.ConstructorValue) receiver;
            return new RuntimeValues.ClassValue(
                type, constructor.ownerType(), constructor.binding());
          };
      case FUNCTION_CONTEXT_FUNCTION ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.FunctionContextValue) receiver).function();
          };
      case PARAMETER_CONTEXT_PARAMETER ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.ParameterContextValue) receiver).parameter();
          };
      case FIELD_CONTEXT_FIELD ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.FieldContextValue) receiver).field();
          };
      case FUNCTION_INVOCATION_PROCEED ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.FunctionInvocationValue) receiver).proceed(location);
          };
      case FUNCTION_COMPLETION_SUCCEEDED ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return ((RuntimeValues.FunctionCompletionValue) receiver).succeeded();
          };
      default ->
          throw new IllegalArgumentException("Unsupported reflection intrinsic: " + intrinsic);
    };
  }
}
