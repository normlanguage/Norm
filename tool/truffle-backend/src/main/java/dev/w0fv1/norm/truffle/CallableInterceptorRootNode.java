package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import dev.w0fv1.norm.core.BuiltinTypeId;
import dev.w0fv1.norm.core.CoreCallableParameter;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreInterceptor;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.CoreValueCategory;
import dev.w0fv1.norm.core.DefinitionOccurrenceId;
import java.util.ArrayList;
import java.util.List;

final class CallableInterceptorRootNode extends RootNode {
  private static final CoreType FUNCTION_CONTEXT_TYPE =
      builtin("std.core.FunctionContext", List.of());
  private static final CoreType PARAMETER_CONTEXT_TYPE =
      builtin("std.core.ParameterContext", List.of());
  private static final CoreType COMPLETION_TYPE = builtin("std.core.FunctionCompletion", List.of());
  private final String name;
  private final SourceSection sourceSection;
  private final DefinitionOccurrenceId callable;
  private final CallTarget implementation;
  private final List<CoreInterceptor> functionInterceptors;
  private final List<ParameterLayer> parameterLayers;
  private final int parameterOffset;
  private final CoreType returnTypeTemplate;
  private final int reifiedTypeCount;
  private final AnnotationRuntime annotations;
  @Child private IndirectCallNode call = IndirectCallNode.create();
  @Child private AnnotationLifecycleNode lifecycle = new AnnotationLifecycleNode();

  CallableInterceptorRootNode(
      Language language,
      String name,
      SourceSection sourceSection,
      DefinitionOccurrenceId callable,
      CallTarget implementation,
      CoreDefinition.Callable declaration,
      CoreType returnType,
      AnnotationRuntime annotations) {
    super(language);
    this.name = name;
    this.sourceSection = sourceSection;
    this.callable = callable;
    this.implementation = implementation;
    functionInterceptors = declaration.interceptors();
    List<ParameterLayer> layers = new ArrayList<>();
    for (int index = 0; index < declaration.parameters().size(); index++) {
      CoreCallableParameter parameter = declaration.parameters().get(index);
      for (CoreInterceptor interceptor : parameter.interceptors()) {
        layers.add(new ParameterLayer(index, parameter.name(), interceptor));
      }
    }
    parameterLayers = List.copyOf(layers);
    parameterOffset = 1 + (declaration.hasReceiver() ? 1 : 0) + declaration.captureTypes().size();
    returnTypeTemplate = returnType;
    reifiedTypeCount = declaration.reifiedTypeLocals().size();
    this.annotations = annotations;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    ExecutionState execution = (ExecutionState) arguments[0];
    RuntimeValues.FunctionContextValue function =
        new RuntimeValues.FunctionContextValue(FUNCTION_CONTEXT_TYPE, name);
    int reifiedOffset = arguments.length - reifiedTypeCount;
    CoreType returnType =
        returnTypeTemplate.equals(CoreType.VOID)
            ? CoreType.DYNAMIC
            : returnTypeTemplate.substitute(index -> (CoreType) arguments[reifiedOffset + index]);
    return invokeFunction(0, execution, function, arguments, returnType);
  }

  private Object invokeFunction(
      int index,
      ExecutionState execution,
      RuntimeValues.FunctionContextValue function,
      Object[] arguments,
      CoreType returnType) {
    if (index == functionInterceptors.size()) {
      return invokeParameter(0, execution, function, arguments);
    }
    CoreInterceptor interceptor = functionInterceptors.get(index);
    RuntimeValues.ObjectValue annotation =
        annotations.functionAnnotation(callable, interceptor, execution);
    lifecycle.execute(
        annotations.functionBefore(annotation),
        execution,
        annotation,
        new Object[] {function},
        new CoreType[0]);
    boolean succeeded = false;
    try {
      RuntimeValues.FunctionInvocationValue invocation =
          new RuntimeValues.FunctionInvocationValue(
              builtin("std.core.FunctionInvocation", List.of(returnType)),
              () -> invokeFunction(index + 1, execution, function, arguments, returnType));
      Object result =
          lifecycle.execute(
              annotations.functionAround(annotation),
              execution,
              annotation,
              new Object[] {invocation},
              new CoreType[] {returnType});
      succeeded = true;
      return result;
    } finally {
      lifecycle.execute(
          annotations.functionAfter(annotation),
          execution,
          annotation,
          new Object[] {
            function, new RuntimeValues.FunctionCompletionValue(COMPLETION_TYPE, succeeded)
          },
          new CoreType[0]);
    }
  }

  private Object invokeParameter(
      int index,
      ExecutionState execution,
      RuntimeValues.FunctionContextValue function,
      Object[] arguments) {
    if (index == parameterLayers.size()) return call.call(implementation, arguments);
    ParameterLayer layer = parameterLayers.get(index);
    RuntimeValues.ParameterContextValue context =
        new RuntimeValues.ParameterContextValue(
            PARAMETER_CONTEXT_TYPE, function, layer.name(), layer.parameterIndex());
    RuntimeValues.ObjectValue annotation =
        annotations.parameterAnnotation(
            callable, layer.parameterIndex(), layer.interceptor(), execution);
    int argumentIndex = parameterOffset + layer.parameterIndex();
    Object transformed =
        lifecycle.execute(
            annotations.parameterBefore(annotation),
            execution,
            annotation,
            new Object[] {context, RuntimeValues.copy(arguments[argumentIndex])},
            new CoreType[0]);
    arguments[argumentIndex] = RuntimeValues.copy(transformed);
    boolean succeeded = false;
    try {
      Object result = invokeParameter(index + 1, execution, function, arguments);
      succeeded = true;
      return result;
    } finally {
      lifecycle.execute(
          annotations.parameterAfter(annotation),
          execution,
          annotation,
          new Object[] {
            context, new RuntimeValues.FunctionCompletionValue(COMPLETION_TYPE, succeeded)
          },
          new CoreType[0]);
    }
  }

  private static CoreType builtin(String identity, List<CoreType> arguments) {
    return new CoreType.Declared(
        new CoreTypeConstructor.Builtin(new BuiltinTypeId(identity)),
        arguments,
        CoreValueCategory.IDENTITY,
        CoreNullability.NON_NULL);
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  private record ParameterLayer(int parameterIndex, String name, CoreInterceptor interceptor) {}
}
