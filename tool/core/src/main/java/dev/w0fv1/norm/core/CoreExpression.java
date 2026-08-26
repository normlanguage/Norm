package dev.w0fv1.norm.core;

import dev.w0fv1.norm.builtin.IntrinsicId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface CoreExpression extends CoreNode
    permits CoreExpression.Literal,
        CoreExpression.NullLiteral,
        CoreExpression.CollectionLiteral,
        CoreExpression.LocalRead,
        CoreExpression.FieldRead,
        CoreExpression.AddressLocal,
        CoreExpression.AddressField,
        CoreExpression.Dereference,
        CoreExpression.EnumConstruct,
        CoreExpression.Unary,
        CoreExpression.Binary,
        CoreExpression.Switch,
        CoreExpression.Index,
        CoreExpression.CopyObject,
        CoreExpression.Closure,
        CoreExpression.Invoke,
        CoreExpression.Call,
        CoreExpression.InterfaceCall,
        CoreExpression.Construct,
        CoreExpression.Intrinsic {
  CoreType type();

  record Literal(int nodeIndex, Object value, CoreType type) implements CoreExpression {
    public Literal {
      requireNode(nodeIndex);
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(type, "type");
      if (!(value instanceof Long
          || value instanceof Integer
          || value instanceof Float
          || value instanceof Double
          || value instanceof Boolean
          || value instanceof String)) {
        throw new IllegalArgumentException("unsupported core literal value");
      }
    }
  }

  record NullLiteral(int nodeIndex, CoreType type) implements CoreExpression {
    public NullLiteral {
      requireNode(nodeIndex);
      Objects.requireNonNull(type, "type");
    }
  }

  record CollectionLiteral(
      int nodeIndex,
      List<CoreExpression> elements,
      IntrinsicId materializer,
      CoreRuntimeType runtimeType,
      CoreType type)
      implements CoreExpression {
    public CollectionLiteral {
      requireNode(nodeIndex);
      elements = List.copyOf(elements);
      Objects.requireNonNull(materializer, "materializer");
      Objects.requireNonNull(runtimeType, "runtimeType");
      Objects.requireNonNull(type, "type");
    }
  }

  record LocalRead(int nodeIndex, int localIndex, CoreType type) implements CoreExpression {
    public LocalRead {
      requireNode(nodeIndex);
      if (localIndex < 0) throw new IllegalArgumentException("local index must not be negative");
      Objects.requireNonNull(type, "type");
    }
  }

  record FieldRead(
      int nodeIndex,
      CoreExpression receiver,
      CoreFieldReference field,
      boolean nullSafe,
      CoreType type)
      implements CoreExpression {
    public FieldRead {
      requireNode(nodeIndex);
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(type, "type");
    }
  }

  record AddressLocal(int nodeIndex, int localIndex, CoreType type) implements CoreExpression {
    public AddressLocal {
      requireNode(nodeIndex);
      if (localIndex < 0) throw new IllegalArgumentException("local index must not be negative");
      Objects.requireNonNull(type, "type");
    }
  }

  record AddressField(
      int nodeIndex, CoreExpression receiver, CoreFieldReference field, CoreType type)
      implements CoreExpression {
    public AddressField {
      requireNode(nodeIndex);
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(type, "type");
    }
  }

  record Dereference(int nodeIndex, CoreExpression reference, CoreType type)
      implements CoreExpression {
    public Dereference {
      requireNode(nodeIndex);
      Objects.requireNonNull(reference, "reference");
      Objects.requireNonNull(type, "type");
    }
  }

  record EnumConstruct(
      int nodeIndex,
      CoreDefinitionLink target,
      String variantKey,
      CoreRuntimeType runtimeType,
      List<CoreArgument> arguments,
      CoreType type)
      implements CoreExpression {
    public EnumConstruct {
      requireNode(nodeIndex);
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(variantKey, "variantKey");
      if (variantKey.isBlank()) throw new IllegalArgumentException("variant key must not be blank");
      Objects.requireNonNull(runtimeType, "runtimeType");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(type, "type");
    }
  }

  record Unary(int nodeIndex, CoreUnaryOperator operator, CoreExpression operand, CoreType type)
      implements CoreExpression {
    public Unary {
      requireNode(nodeIndex);
      Objects.requireNonNull(operator, "operator");
      Objects.requireNonNull(operand, "operand");
      Objects.requireNonNull(type, "type");
    }
  }

  record Binary(
      int nodeIndex,
      CoreExpression left,
      CoreBinaryOperator operator,
      CoreExpression right,
      CoreType type)
      implements CoreExpression {
    public Binary {
      requireNode(nodeIndex);
      Objects.requireNonNull(left, "left");
      Objects.requireNonNull(operator, "operator");
      Objects.requireNonNull(right, "right");
      Objects.requireNonNull(type, "type");
    }
  }

  record Switch(int nodeIndex, CoreExpression value, List<CoreSwitchCase> cases, CoreType type)
      implements CoreExpression {
    public Switch {
      requireNode(nodeIndex);
      Objects.requireNonNull(value, "value");
      cases = List.copyOf(cases);
      if (cases.isEmpty()) throw new IllegalArgumentException("switch must declare a case");
      Objects.requireNonNull(type, "type");
    }
  }

  record Index(
      int nodeIndex,
      CoreExpression receiver,
      CoreExpression index,
      IntrinsicId readIntrinsic,
      Optional<IntrinsicId> writeIntrinsic,
      CoreType type)
      implements CoreExpression {
    public Index {
      requireNode(nodeIndex);
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(index, "index");
      Objects.requireNonNull(readIntrinsic, "readIntrinsic");
      writeIntrinsic = Objects.requireNonNull(writeIntrinsic, "writeIntrinsic");
      Objects.requireNonNull(type, "type");
    }
  }

  record CopyObject(int nodeIndex, CoreExpression receiver, boolean nullSafe, CoreType type)
      implements CoreExpression {
    public CopyObject {
      requireNode(nodeIndex);
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(type, "type");
    }
  }

  record Closure(
      int nodeIndex,
      CoreDefinitionLink target,
      Optional<CoreExpression> receiver,
      List<CoreExpression> captures,
      List<CoreRuntimeType> reifiedArguments,
      List<CoreRuntimeType> receiverTypeArguments,
      boolean virtual,
      CoreType type)
      implements CoreExpression {
    public Closure {
      requireNode(nodeIndex);
      Objects.requireNonNull(target, "target");
      receiver = Objects.requireNonNull(receiver, "receiver");
      captures = List.copyOf(captures);
      reifiedArguments = List.copyOf(reifiedArguments);
      receiverTypeArguments = List.copyOf(receiverTypeArguments);
      Objects.requireNonNull(type, "type");
    }

    public Closure(
        int nodeIndex,
        CoreDefinitionLink target,
        Optional<CoreExpression> receiver,
        List<CoreExpression> captures,
        List<CoreRuntimeType> reifiedArguments,
        CoreType type) {
      this(nodeIndex, target, receiver, captures, reifiedArguments, List.of(), false, type);
    }
  }

  record Invoke(int nodeIndex, CoreExpression callee, List<CoreArgument> arguments, CoreType type)
      implements CoreExpression {
    public Invoke {
      requireNode(nodeIndex);
      Objects.requireNonNull(callee, "callee");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(type, "type");
    }
  }

  record Call(
      int nodeIndex,
      CoreDefinitionLink target,
      Optional<CoreExpression> receiver,
      List<CoreArgument> arguments,
      List<CoreRuntimeType> reifiedArguments,
      List<CoreRuntimeType> receiverTypeArguments,
      boolean virtual,
      boolean nullSafe,
      CoreType type)
      implements CoreExpression {
    public Call {
      requireNode(nodeIndex);
      Objects.requireNonNull(target, "target");
      receiver = Objects.requireNonNull(receiver, "receiver");
      arguments = List.copyOf(arguments);
      reifiedArguments = List.copyOf(reifiedArguments);
      receiverTypeArguments = List.copyOf(receiverTypeArguments);
      Objects.requireNonNull(type, "type");
    }

    public Call(
        int nodeIndex,
        CoreDefinitionLink target,
        Optional<CoreExpression> receiver,
        List<CoreArgument> arguments,
        List<CoreRuntimeType> reifiedArguments,
        boolean nullSafe,
        CoreType type) {
      this(
          nodeIndex,
          target,
          receiver,
          arguments,
          reifiedArguments,
          List.of(),
          false,
          nullSafe,
          type);
    }
  }

  record InterfaceCall(
      int nodeIndex,
      CoreDefinitionLink requirement,
      CoreExpression receiver,
      List<CoreArgument> arguments,
      List<CoreRuntimeType> reifiedArguments,
      boolean nullSafe,
      CoreType type)
      implements CoreExpression {
    public InterfaceCall {
      requireNode(nodeIndex);
      Objects.requireNonNull(requirement, "requirement");
      Objects.requireNonNull(receiver, "receiver");
      arguments = List.copyOf(arguments);
      reifiedArguments = List.copyOf(reifiedArguments);
      Objects.requireNonNull(type, "type");
    }
  }

  record Construct(
      int nodeIndex,
      CoreDefinitionLink target,
      CoreDefinitionLink initializer,
      CoreRuntimeType runtimeType,
      List<CoreArgument> arguments,
      CoreType type)
      implements CoreExpression {
    public Construct {
      requireNode(nodeIndex);
      Objects.requireNonNull(target, "target");
      Objects.requireNonNull(initializer, "initializer");
      Objects.requireNonNull(runtimeType, "runtimeType");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(type, "type");
    }
  }

  record Intrinsic(
      int nodeIndex,
      IntrinsicId intrinsic,
      Optional<CoreExpression> receiver,
      List<CoreArgument> arguments,
      Optional<CoreRuntimeType> runtimeType,
      boolean nullSafe,
      CoreType type)
      implements CoreExpression {
    public Intrinsic {
      requireNode(nodeIndex);
      Objects.requireNonNull(intrinsic, "intrinsic");
      receiver = Objects.requireNonNull(receiver, "receiver");
      arguments = List.copyOf(arguments);
      runtimeType = Objects.requireNonNull(runtimeType, "runtimeType");
      Objects.requireNonNull(type, "type");
    }
  }

  private static void requireNode(int index) {
    if (index < 0) throw new IllegalArgumentException("node index must not be negative");
  }
}
