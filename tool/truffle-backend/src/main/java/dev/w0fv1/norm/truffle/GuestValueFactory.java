package dev.w0fv1.norm.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.nodes.Node;
import dev.w0fv1.norm.abi.FileExceptionAbi;
import dev.w0fv1.norm.abi.HttpExceptionAbi;
import dev.w0fv1.norm.abi.IntrinsicId;
import dev.w0fv1.norm.abi.OpaqueValueAbi;
import dev.w0fv1.norm.abi.TimeExceptionAbi;
import dev.w0fv1.norm.core.CoreDefinition;
import dev.w0fv1.norm.core.CoreNominalTypeKey;
import dev.w0fv1.norm.core.CoreNullability;
import dev.w0fv1.norm.core.CoreType;
import dev.w0fv1.norm.core.CoreTypeConstructor;
import dev.w0fv1.norm.core.DefinitionId;
import dev.w0fv1.norm.core.DefinitionReference;
import dev.w0fv1.norm.execution.PlatformFileException;
import dev.w0fv1.norm.execution.PlatformHttpException;
import dev.w0fv1.norm.execution.PlatformTimeException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class GuestValueFactory {
  private final Map<Key, AggregatePlan> aggregates;
  private final Map<DefinitionId, AggregatePlan> aggregatesByDefinition;
  private final Map<Key, EnumPlan> enums;

  GuestValueFactory(List<AggregatePlan> aggregatePlans, List<EnumPlan> enumPlans) {
    Map<Key, AggregatePlan> indexedAggregates = new LinkedHashMap<>();
    for (AggregatePlan plan : aggregatePlans) {
      if (indexedAggregates.putIfAbsent(Key.of(plan.nominal()), plan) != null) {
        throw new IllegalStateException("duplicate runtime aggregate " + plan.nominal());
      }
    }
    Map<Key, EnumPlan> indexedEnums = new LinkedHashMap<>();
    for (EnumPlan plan : enumPlans) {
      if (indexedEnums.putIfAbsent(Key.of(plan.nominal()), plan) != null) {
        throw new IllegalStateException("duplicate runtime enum " + plan.nominal());
      }
    }
    aggregates = Map.copyOf(indexedAggregates);
    Map<DefinitionId, AggregatePlan> indexedDefinitions = new LinkedHashMap<>();
    aggregatePlans.forEach(plan -> indexedDefinitions.putIfAbsent(plan.info().definition(), plan));
    aggregatesByDefinition = Map.copyOf(indexedDefinitions);
    enums = Map.copyOf(indexedEnums);
  }

  NormThrownException fileException(
      PlatformFileException failure, ExecutionState execution, Node location) {
    validateFileContract();
    FileExceptionAbi.Failure mappedFailure = FileExceptionAbi.failure(failure.reason().name());
    Object operation =
        enumValue(
            FileExceptionAbi.MODULE_NAME,
            FileExceptionAbi.MODULE_VERSION,
            FileExceptionAbi.PACKAGE_NAME,
            FileExceptionAbi.OPERATION_TYPE_NAME,
            FileExceptionAbi.operationVariant(failure.operation().name()));
    Object failureReason =
        enumValue(
            FileExceptionAbi.MODULE_NAME,
            FileExceptionAbi.MODULE_VERSION,
            FileExceptionAbi.PACKAGE_NAME,
            FileExceptionAbi.FAILURE_TYPE_NAME,
            mappedFailure.variant());
    Object path =
        construct(
            FileExceptionAbi.MODULE_NAME,
            FileExceptionAbi.MODULE_VERSION,
            FileExceptionAbi.PACKAGE_NAME,
            FileExceptionAbi.PATH_TYPE_NAME,
            execution,
            failure.path());
    RuntimeValues.ObjectValue exception =
        construct(
            FileExceptionAbi.MODULE_NAME,
            FileExceptionAbi.MODULE_VERSION,
            FileExceptionAbi.PACKAGE_NAME,
            FileExceptionAbi.TYPE_NAME,
            execution,
            mappedFailure.code(),
            failure.getMessage(),
            operation,
            failureReason,
            path);
    return new NormThrownException(exception, location);
  }

  NormThrownException timeException(
      PlatformTimeException failure, ExecutionState execution, Node location) {
    validateTimeContract();
    TimeExceptionAbi.Failure mappedFailure = TimeExceptionAbi.failure(failure.reason().name());
    Object operation =
        enumValue(
            TimeExceptionAbi.MODULE_NAME,
            TimeExceptionAbi.MODULE_VERSION,
            TimeExceptionAbi.PACKAGE_NAME,
            TimeExceptionAbi.OPERATION_TYPE_NAME,
            TimeExceptionAbi.operationVariant(failure.operation().name()));
    Object failureReason =
        enumValue(
            TimeExceptionAbi.MODULE_NAME,
            TimeExceptionAbi.MODULE_VERSION,
            TimeExceptionAbi.PACKAGE_NAME,
            TimeExceptionAbi.FAILURE_TYPE_NAME,
            mappedFailure.variant());
    RuntimeValues.ObjectValue exception =
        construct(
            TimeExceptionAbi.MODULE_NAME,
            TimeExceptionAbi.MODULE_VERSION,
            TimeExceptionAbi.PACKAGE_NAME,
            TimeExceptionAbi.TYPE_NAME,
            execution,
            mappedFailure.code(),
            failure.getMessage(),
            operation,
            failureReason);
    return new NormThrownException(exception, location);
  }

  NormThrownException httpException(
      PlatformHttpException failure, ExecutionState execution, Node location) {
    validateHttpContract();
    HttpExceptionAbi.Failure mappedFailure = HttpExceptionAbi.failure(failure.reason().name());
    Object operation =
        enumValue(
            HttpExceptionAbi.MODULE_NAME,
            HttpExceptionAbi.MODULE_VERSION,
            HttpExceptionAbi.PACKAGE_NAME,
            HttpExceptionAbi.OPERATION_TYPE_NAME,
            HttpExceptionAbi.operationVariant(failure.operation().name()));
    Object failureReason =
        enumValue(
            HttpExceptionAbi.MODULE_NAME,
            HttpExceptionAbi.MODULE_VERSION,
            HttpExceptionAbi.PACKAGE_NAME,
            HttpExceptionAbi.FAILURE_TYPE_NAME,
            mappedFailure.variant());
    Object uri =
        construct(
            HttpExceptionAbi.MODULE_NAME,
            HttpExceptionAbi.MODULE_VERSION,
            HttpExceptionAbi.PACKAGE_NAME,
            HttpExceptionAbi.URI_TYPE_NAME,
            execution,
            failure.uri());
    RuntimeValues.ObjectValue exception =
        construct(
            HttpExceptionAbi.MODULE_NAME,
            HttpExceptionAbi.MODULE_VERSION,
            HttpExceptionAbi.PACKAGE_NAME,
            HttpExceptionAbi.TYPE_NAME,
            execution,
            mappedFailure.code(),
            failure.getMessage(),
            operation,
            failureReason,
            uri);
    return new NormThrownException(exception, location);
  }

  RuntimeValues.ObjectValue construct(
      CoreType type, ExecutionState execution, Object... arguments) {
    return construct(requireAggregate(type), type, execution, arguments);
  }

  RuntimeValues.OpaqueValue opaque(CoreType type, Object value, String displayName) {
    CoreType concrete = nonNullable(type);
    AggregatePlan plan = requireAggregate(concrete);
    return new RuntimeValues.OpaqueValue(concrete, value, displayName, plan.info());
  }

  RuntimeValues.OpaqueValue bytes(ByteSequence value) {
    OpaqueValueAbi.Identity identity = OpaqueValueAbi.BYTES;
    AggregatePlan plan =
        require(
            aggregates,
            identity.moduleName(),
            identity.moduleVersion(),
            identity.packageName(),
            identity.typeName());
    return new RuntimeValues.OpaqueValue(plan.type(), value, "Bytes", plan.info());
  }

  RuntimeValues.OpaqueResource resource(
      CoreType type, AutoCloseable value, String displayName, ExecutionState execution) {
    CoreType concrete = nonNullable(type);
    AggregatePlan plan = requireAggregate(concrete);
    try {
      ManagedResource managed = execution.resources().register(displayName, value);
      return new RuntimeValues.OpaqueResource(concrete, managed, displayName, plan.info());
    } catch (RuntimeException | Error failure) {
      try {
        value.close();
      } catch (Exception | Error closeFailure) {
        failure.addSuppressed(closeFailure);
      }
      throw failure;
    }
  }

  private RuntimeValues.ObjectValue construct(
      String module,
      int version,
      String packageName,
      String name,
      ExecutionState execution,
      Object... arguments) {
    AggregatePlan plan = require(aggregates, module, version, packageName, name);
    return construct(plan, plan.type(), execution, arguments);
  }

  private RuntimeValues.ObjectValue construct(
      AggregatePlan plan, CoreType type, ExecutionState execution, Object... arguments) {
    RuntimeValues.ObjectValue value = new RuntimeValues.ObjectValue(plan.info(), type);
    Object[] callArguments = new Object[arguments.length + 2];
    callArguments[0] = execution;
    callArguments[1] = value;
    System.arraycopy(arguments, 0, callArguments, 2, arguments.length);
    plan.initializer().call(callArguments);
    return value;
  }

  private AggregatePlan requireAggregate(CoreType type) {
    if (!(type instanceof CoreType.Declared declared)
        || !(declared.constructor() instanceof CoreTypeConstructor.User user)
        || !(user.definition() instanceof DefinitionReference.External external)) {
      throw new IllegalStateException("runtime opaque type is not an external nominal type");
    }
    AggregatePlan plan = aggregatesByDefinition.get(external.definition());
    if (plan == null) {
      throw new IllegalStateException("runtime aggregate is absent: " + external.definition());
    }
    return plan;
  }

  private static CoreType nonNullable(CoreType type) {
    return switch (type) {
      case CoreType.Declared declared ->
          new CoreType.Declared(
              declared.constructor(),
              declared.arguments(),
              declared.category(),
              CoreNullability.NON_NULL);
      case CoreType.Function function ->
          new CoreType.Function(
              function.returnType(), function.parameterTypes(), CoreNullability.NON_NULL);
      case CoreType.Parameter parameter ->
          new CoreType.Parameter(parameter.index(), CoreNullability.NON_NULL);
      case CoreType.Reference reference -> reference;
      case CoreType.Special special -> special;
    };
  }

  private RuntimeValues.EnumValue enumValue(
      String module, int version, String packageName, String name, String variant) {
    EnumPlan plan = require(enums, module, version, packageName, name);
    if (!plan.variants().contains(variant)) {
      throw new IllegalStateException("runtime enum variant is absent: " + name + "." + variant);
    }
    return new RuntimeValues.EnumValue(plan.definition(), plan.type(), name, variant, List.of());
  }

  private static <T> T require(
      Map<Key, T> values, String module, int version, String packageName, String name) {
    T value = values.get(new Key(module, version, packageName, name));
    if (value == null) {
      throw new IllegalStateException(
          "runtime nominal type is absent: " + packageName + "." + name);
    }
    return value;
  }

  private void validateFileContract() {
    validateIntrinsics(FileExceptionAbi.INTRINSIC_NAMES);
    AggregatePlan exception =
        require(
            aggregates,
            FileExceptionAbi.MODULE_NAME,
            FileExceptionAbi.MODULE_VERSION,
            FileExceptionAbi.PACKAGE_NAME,
            FileExceptionAbi.TYPE_NAME);
    requireField(
        exception, FileExceptionAbi.FIELD_MESSAGE_ORDINAL, FileExceptionAbi.FIELD_MESSAGE_NAME);
    requireField(exception, FileExceptionAbi.FIELD_CODE_ORDINAL, FileExceptionAbi.FIELD_CODE_NAME);
    requireField(
        exception, FileExceptionAbi.FIELD_OPERATION_ORDINAL, FileExceptionAbi.FIELD_OPERATION_NAME);
    requireField(
        exception, FileExceptionAbi.FIELD_REASON_ORDINAL, FileExceptionAbi.FIELD_REASON_NAME);
    requireField(exception, FileExceptionAbi.FIELD_PATH_ORDINAL, FileExceptionAbi.FIELD_PATH_NAME);
  }

  private void validateTimeContract() {
    validateIntrinsics(TimeExceptionAbi.INTRINSIC_NAMES);
    AggregatePlan exception =
        require(
            aggregates,
            TimeExceptionAbi.MODULE_NAME,
            TimeExceptionAbi.MODULE_VERSION,
            TimeExceptionAbi.PACKAGE_NAME,
            TimeExceptionAbi.TYPE_NAME);
    requireField(
        exception, TimeExceptionAbi.FIELD_MESSAGE_ORDINAL, TimeExceptionAbi.FIELD_MESSAGE_NAME);
    requireField(exception, TimeExceptionAbi.FIELD_CODE_ORDINAL, TimeExceptionAbi.FIELD_CODE_NAME);
    requireField(
        exception, TimeExceptionAbi.FIELD_OPERATION_ORDINAL, TimeExceptionAbi.FIELD_OPERATION_NAME);
    requireField(
        exception, TimeExceptionAbi.FIELD_REASON_ORDINAL, TimeExceptionAbi.FIELD_REASON_NAME);
  }

  private void validateHttpContract() {
    validateIntrinsics(HttpExceptionAbi.INTRINSIC_NAMES);
    AggregatePlan exception =
        require(
            aggregates,
            HttpExceptionAbi.MODULE_NAME,
            HttpExceptionAbi.MODULE_VERSION,
            HttpExceptionAbi.PACKAGE_NAME,
            HttpExceptionAbi.TYPE_NAME);
    requireField(
        exception, HttpExceptionAbi.FIELD_MESSAGE_ORDINAL, HttpExceptionAbi.FIELD_MESSAGE_NAME);
    requireField(exception, HttpExceptionAbi.FIELD_CODE_ORDINAL, HttpExceptionAbi.FIELD_CODE_NAME);
    requireField(
        exception, HttpExceptionAbi.FIELD_OPERATION_ORDINAL, HttpExceptionAbi.FIELD_OPERATION_NAME);
    requireField(
        exception, HttpExceptionAbi.FIELD_REASON_ORDINAL, HttpExceptionAbi.FIELD_REASON_NAME);
    requireField(exception, HttpExceptionAbi.FIELD_URI_ORDINAL, HttpExceptionAbi.FIELD_URI_NAME);
  }

  private static void requireField(AggregatePlan plan, int ordinal, String name) {
    List<RuntimeValues.FieldPlan> fields = plan.info().fields();
    if (ordinal < 0 || ordinal >= fields.size() || !fields.get(ordinal).name().equals(name)) {
      throw new IllegalStateException(
          "runtime aggregate field ABI is inconsistent: " + plan.nominal() + "." + name);
    }
  }

  private static void validateIntrinsics(java.util.Set<String> names) {
    names.forEach(IntrinsicId::valueOf);
  }

  record AggregatePlan(
      CoreNominalTypeKey nominal,
      RuntimeValues.AggregateInfo info,
      CoreType type,
      CallTarget initializer) {
    AggregatePlan {
      Objects.requireNonNull(nominal, "nominal");
      Objects.requireNonNull(info, "info");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(initializer, "initializer");
    }

    static AggregatePlan create(
        DefinitionId definition,
        CoreDefinition.Aggregate aggregate,
        RuntimeValues.AggregateInfo info,
        CallTarget initializer) {
      CoreType type =
          new CoreType.Declared(
              new CoreTypeConstructor.User(new DefinitionReference.External(definition)),
              List.of(),
              aggregate.valueCategory(),
              CoreNullability.NON_NULL);
      return new AggregatePlan(aggregate.nominalType(), info, type, initializer);
    }
  }

  record EnumPlan(
      CoreNominalTypeKey nominal, DefinitionId definition, CoreType type, List<String> variants) {
    EnumPlan {
      Objects.requireNonNull(nominal, "nominal");
      Objects.requireNonNull(definition, "definition");
      Objects.requireNonNull(type, "type");
      variants = List.copyOf(variants);
    }

    static EnumPlan create(DefinitionId definition, CoreDefinition.Enum declaration) {
      CoreType type =
          new CoreType.Declared(
              new CoreTypeConstructor.User(new DefinitionReference.External(definition)),
              List.of(),
              dev.w0fv1.norm.core.CoreValueCategory.VALUE,
              CoreNullability.NON_NULL);
      return new EnumPlan(
          declaration.nominalType(),
          definition,
          type,
          declaration.variants().stream().map(dev.w0fv1.norm.core.CoreEnumVariant::key).toList());
    }
  }

  private record Key(String module, int version, String packageName, String name) {
    private static Key of(CoreNominalTypeKey nominal) {
      return new Key(
          nominal.module().name(),
          nominal.module().version(),
          nominal.packageName(),
          nominal.name());
    }
  }
}
