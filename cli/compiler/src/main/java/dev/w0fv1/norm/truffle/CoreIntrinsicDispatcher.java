package dev.w0fv1.norm.truffle;

import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.execution.RuntimeErrorCode;
import dev.w0fv1.norm.value.JarBinding;
import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.LocalJarTarget;
import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.MavenJarTarget;
import dev.w0fv1.norm.value.ModuleDeclaration;
import dev.w0fv1.norm.value.ModuleDependency;
import dev.w0fv1.norm.value.Sha256Digest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class CoreIntrinsicDispatcher {
  private CoreIntrinsicDispatcher() {}

  static IntrinsicOperation resolve(IntrinsicId intrinsic) {
    return switch (intrinsic) {
      case CONTEXT_CURRENT ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              execution.contexts().get(((RuntimeValues.ClassValue) arguments[0]).reflectedType());
      case OBSERVE_FIELDS, OBSERVE_FIELD ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            RuntimeValues.ObjectValue target;
            java.util.OptionalInt selected = java.util.OptionalInt.empty();
            java.util.function.IntConsumer readObserver;
            java.util.function.Consumer<FieldObservations.Change> changedObserver;
            if (intrinsic == IntrinsicId.OBSERVE_FIELD) {
              var handle =
                  (AnnotationRuntime.FieldHandle) ((RuntimeValues.OpaqueValue) arguments[0]).value;
              target = handle.receiver();
              selected = java.util.OptionalInt.of(handle.field().index());
              var changed = (RuntimeValues.Closure) arguments[1];
              readObserver = field -> {};
              changedObserver =
                  change -> {
                    RuntimeInvocation.invoke(
                        execution,
                        changed,
                        RuntimeValues.copy(change.previous()),
                        RuntimeValues.copy(change.current()));
                  };
            } else if (arguments[0] instanceof RuntimeValues.ObjectValue object) {
              target = object;
              var read = (RuntimeValues.Closure) arguments[1];
              var changed = (RuntimeValues.Closure) arguments[2];
              readObserver = field -> RuntimeInvocation.invoke(execution, read, field);
              changedObserver =
                  change -> RuntimeInvocation.invoke(execution, changed, change.field());
            } else {
              throw execution
                  .values()
                  .javaException(
                      new IllegalArgumentException("field observation requires a Norm object"),
                      execution,
                      location);
            }
            if (target.observations == null) target.observations = new FieldObservations(target);
            var subscription =
                target.observations.subscribe(selected, readObserver, changedObserver);
            var resource = execution.resources().register("field observation", subscription);
            var close =
                new com.oracle.truffle.api.nodes.RootNode(null) {
                  @Override
                  public Object execute(com.oracle.truffle.api.frame.VirtualFrame frame) {
                    resource.close();
                    return null;
                  }
                };
            return new RuntimeValues.Closure(
                close.getCallTarget(),
                java.util.Optional.empty(),
                false,
                null,
                new Object[0],
                new Object[0],
                new Object[0],
                type);
          };
      case CONTEXT_WITH ->
          (receiver, arguments, type, context, location, annotations, execution) ->
              execution
                  .contexts()
                  .with(
                      ((RuntimeValues.ClassValue) arguments[1]).reflectedType(),
                      arguments[0],
                      () ->
                          RuntimeInvocation.invoke(
                              execution, (RuntimeValues.Closure) arguments[2]));
      case PRINT_LINE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            context.output().println(RuntimeText.stringify(first));
            return null;
          };
      case EXPECTED_OUTPUT_LINE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];

            context.expectedOutput().println(RuntimeText.stringify(first));
            return null;
          };
      case AWAIT_CANCELLATION ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            if (execution == null)
              throw new IllegalStateException("execution runtime is unavailable");
            execution.callbacks().runUntilCancellation();
            return null;
          };
      case DELAY ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            try {
              execution
                  .callbacks()
                  .hostCall(
                      () -> {
                        dev.w0fv1.norm.platform.CancellableDelay.await(
                            context.cancellation(),
                            new dev.w0fv1.norm.platform.PlatformDuration(
                                (Long) arguments[0], (Integer) arguments[1]));
                        return null;
                      });
              return null;
            } catch (InterruptedException error) {
              Thread.currentThread().interrupt();
              throw execution.values().javaException(error, execution, location);
            }
          };
      case APPLICATION_PACKAGE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return context.applicationPackage();
          };
      case APPLICATION_DIRECTORY ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            return context
                .applicationDirectory()
                .orElseThrow(
                    () -> new IllegalStateException("Application directory is unavailable"))
                .toString();
          };
      case REQUIRE_ARGUMENT ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];

            if (!(Boolean) first) {
              throw new NormGuestException(
                  RuntimeErrorCode.INVALID_ARGUMENT, (String) second, location);
            }
            return null;
          };
      case PUBLISH_MODULE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            Object third = arguments.length <= 2 ? null : arguments[2];
            Object fourth = arguments.length <= 3 ? null : arguments[3];
            Object fifth = arguments.length <= 4 ? null : arguments[4];

            RuntimeValues.ListValue exportedValues = (RuntimeValues.ListValue) third;
            List<String> exports = exportedValues.values.stream().map(String.class::cast).toList();
            List<Object> dependencyRepositories = ((RuntimeValues.ListValue) fourth).values;
            List<Object> dependencyNames = ((RuntimeValues.ListValue) fifth).values;
            List<Object> dependencyVersions = ((RuntimeValues.ListValue) arguments[5]).values;
            List<Object> dependencyExports = ((RuntimeValues.ListValue) arguments[6]).values;
            if (dependencyRepositories.size() != dependencyNames.size()
                || dependencyNames.size() != dependencyVersions.size()
                || dependencyNames.size() != dependencyExports.size()) {
              throw new IllegalStateException("module dependency coordinates are inconsistent");
            }
            List<ModuleDependency> dependencies = new ArrayList<>(dependencyNames.size());
            for (int index = 0; index < dependencyNames.size(); index++) {
              dependencies.add(
                  new ModuleDependency(
                      (String) dependencyRepositories.get(index),
                      (String) dependencyNames.get(index),
                      dependencyVersions.get(index) == RuntimeValues.NullValue.INSTANCE
                          ? null
                          : (Integer) dependencyVersions.get(index),
                      (Boolean) dependencyExports.get(index)));
            }
            String bindingSource = (String) arguments[7];
            Optional<Sha256Digest> digest =
                ((String) arguments[12]).isEmpty()
                    ? Optional.empty()
                    : Optional.of(Sha256Digest.parse((String) arguments[12]));
            List<Object> bindingApiTypes = ((RuntimeValues.ListValue) arguments[13]).values;
            List<Object> bindingApiMembers = ((RuntimeValues.ListValue) arguments[14]).values;
            List<Object> bindingApiOverloadNames = ((RuntimeValues.ListValue) arguments[15]).values;
            List<Object> bindingApiOverloadParameterTypes =
                ((RuntimeValues.ListValue) arguments[16]).values;
            if (bindingApiTypes.size() != bindingApiMembers.size()
                || bindingApiTypes.size() != bindingApiOverloadNames.size()
                || bindingApiTypes.size() != bindingApiOverloadParameterTypes.size()) {
              throw new IllegalStateException("JAR binding API declarations are inconsistent");
            }
            List<JarBindingType> api = new ArrayList<>(bindingApiTypes.size());
            for (int index = 0; index < bindingApiTypes.size(); index++) {
              RuntimeValues.ListValue members =
                  (RuntimeValues.ListValue) bindingApiMembers.get(index);
              RuntimeValues.ListValue overloadNames =
                  (RuntimeValues.ListValue) bindingApiOverloadNames.get(index);
              RuntimeValues.ListValue overloadParameterTypes =
                  (RuntimeValues.ListValue) bindingApiOverloadParameterTypes.get(index);
              if (overloadNames.values.size() != overloadParameterTypes.values.size()) {
                throw new IllegalStateException(
                    "JAR binding overload declarations are inconsistent");
              }
              List<JarBindingOverload> overloads = new ArrayList<>(overloadNames.values.size());
              for (int overloadIndex = 0;
                  overloadIndex < overloadNames.values.size();
                  overloadIndex++) {
                RuntimeValues.ListValue parameterTypes =
                    (RuntimeValues.ListValue) overloadParameterTypes.values.get(overloadIndex);
                overloads.add(
                    new JarBindingOverload(
                        (String) overloadNames.values.get(overloadIndex),
                        parameterTypes.values.stream().map(String.class::cast).toList()));
              }
              api.add(
                  new JarBindingType(
                      (String) bindingApiTypes.get(index),
                      members.values.stream().map(String.class::cast).toList(),
                      overloads));
            }
            Optional<JarBinding> binding =
                switch (bindingSource) {
                  case "" -> Optional.empty();
                  case "local" ->
                      Optional.of(
                          new JarBinding(new LocalJarTarget((String) arguments[8], digest), api));
                  case "maven" ->
                      Optional.of(
                          new JarBinding(
                              new MavenJarTarget(
                                  new MavenArtifactCoordinate(
                                      (String) arguments[9],
                                      (String) arguments[10],
                                      (String) arguments[11]),
                                  digest),
                              api));
                  default ->
                      throw new IllegalStateException(
                          "unknown JAR binding source " + bindingSource);
                };
            context
                .modulePublisher()
                .orElseThrow(
                    () -> new IllegalStateException("module publication capability is unavailable"))
                .publish(
                    new ModuleDeclaration(
                        first == RuntimeValues.NullValue.INSTANCE ? null : (String) first,
                        second == RuntimeValues.NullValue.INSTANCE ? null : (Integer) second,
                        exports,
                        dependencies,
                        binding,
                        new dev.w0fv1.norm.value.ModuleSourceLayout(
                            ((RuntimeValues.ListValue) arguments[17])
                                .values.stream().map(String.class::cast).toList(),
                            ((RuntimeValues.ListValue) arguments[18])
                                .values.stream().map(String.class::cast).toList())));
            return null;
          };
      case JAR_INPUT_STREAM_READ,
          JAR_OUTPUT_STREAM_WRITE,
          JAR_OUTPUT_STREAM_FLUSH,
          JAR_STREAM_CLOSE ->
          (receiver, arguments, type, context, location, annotations, execution) -> {
            Object first = arguments.length <= 0 ? null : arguments[0];
            Object second = arguments.length <= 1 ? null : arguments[1];
            return JarStreamIntrinsicDispatcher.execute(
                intrinsic, first, second, type, execution, location);
          };
      case TIME_SYSTEM_CLOCK, TIME_CLOCK_NOW -> SystemIntrinsicDispatcher.resolve(intrinsic);
      default -> throw new IllegalArgumentException("Unsupported core intrinsic: " + intrinsic);
    };
  }
}
