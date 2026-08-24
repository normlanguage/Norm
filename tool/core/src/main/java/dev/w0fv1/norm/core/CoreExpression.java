package dev.w0fv1.norm.core;

import dev.w0fv1.norm.builtin.IntrinsicId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface CoreExpression extends CoreNode
    permits CoreExpression.Literal,
        CoreExpression.NullLiteral,
        CoreExpression.ArrayLiteral,
        CoreExpression.LocalRead,
        CoreExpression.FieldRead,
        CoreExpression.EnumMember,
        CoreExpression.Unary,
        CoreExpression.Binary,
        CoreExpression.Index,
        CoreExpression.CopyObject,
        CoreExpression.Call,
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

  record ArrayLiteral(
      int nodeIndex, List<CoreExpression> elements, CoreRuntimeType runtimeType, CoreType type)
      implements CoreExpression {
    public ArrayLiteral {
      requireNode(nodeIndex);
      elements = List.copyOf(elements);
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

  record EnumMember(int nodeIndex, CoreDefinitionLink target, int memberOrdinal, CoreType type)
      implements CoreExpression {
    public EnumMember {
      requireNode(nodeIndex);
      Objects.requireNonNull(target, "target");
      if (memberOrdinal < 0) {
        throw new IllegalArgumentException("enum member ordinal must not be negative");
      }
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

  record Call(
      int nodeIndex,
      CoreDefinitionLink target,
      Optional<CoreExpression> receiver,
      List<CoreArgument> arguments,
      List<CoreRuntimeType> reifiedArguments,
      boolean nullSafe,
      CoreType type)
      implements CoreExpression {
    public Call {
      requireNode(nodeIndex);
      Objects.requireNonNull(target, "target");
      receiver = Objects.requireNonNull(receiver, "receiver");
      arguments = List.copyOf(arguments);
      reifiedArguments = List.copyOf(reifiedArguments);
      Objects.requireNonNull(type, "type");
    }
  }

  record Construct(
      int nodeIndex,
      CoreDefinitionLink target,
      CoreRuntimeType runtimeType,
      List<CoreArgument> arguments,
      CoreType type)
      implements CoreExpression {
    public Construct {
      requireNode(nodeIndex);
      Objects.requireNonNull(target, "target");
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
