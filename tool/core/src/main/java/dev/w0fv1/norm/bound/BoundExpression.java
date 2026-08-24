package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.builtin.IntrinsicId;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public sealed interface BoundExpression extends BoundNode
    permits BoundExpression.Literal,
        BoundExpression.NullLiteral,
        BoundExpression.CollectionLiteral,
        BoundExpression.LocalRead,
        BoundExpression.FieldRead,
        BoundExpression.EnumConstruct,
        BoundExpression.InterfaceCall,
        BoundExpression.Unary,
        BoundExpression.Binary,
        BoundExpression.Switch,
        BoundExpression.Index,
        BoundExpression.CopyObject,
        BoundCall,
        BoundConstruct,
        BoundIntrinsic {
  SemanticType type();

  record Literal(Object value, SemanticType type, SourceSpan span) implements BoundExpression {
    public Literal {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record NullLiteral(SemanticType type, SourceSpan span) implements BoundExpression {
    public NullLiteral {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record CollectionLiteral(
      List<BoundExpression> elements,
      IntrinsicId materializer,
      BoundRuntimeType runtimeType,
      SemanticType type,
      SourceSpan span)
      implements BoundExpression {
    public CollectionLiteral {
      elements = List.copyOf(elements);
      Objects.requireNonNull(materializer, "materializer");
      Objects.requireNonNull(runtimeType, "runtimeType");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record LocalRead(BoundLocalId local, SemanticType type, SourceSpan span)
      implements BoundExpression {
    public LocalRead {
      Objects.requireNonNull(local, "local");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record FieldRead(
      BoundExpression receiver,
      BoundFieldId field,
      int ordinal,
      boolean nullSafe,
      SemanticType type,
      SourceSpan span)
      implements BoundExpression {
    public FieldRead {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(field, "field");
      if (ordinal < 0) throw new IllegalArgumentException("field ordinal must be non-negative");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }

    public FieldRead(
        BoundExpression receiver,
        BoundFieldId field,
        int ordinal,
        SemanticType type,
        SourceSpan span) {
      this(receiver, field, ordinal, false, type, span);
    }
  }

  record EnumConstruct(
      BoundEnumId enumId,
      BoundEnumVariantId variantId,
      String enumName,
      String variantName,
      List<BoundArgument> arguments,
      BoundRuntimeType runtimeType,
      SemanticType type,
      SourceSpan span)
      implements BoundExpression {
    public EnumConstruct {
      Objects.requireNonNull(enumId, "enumId");
      Objects.requireNonNull(variantId, "variantId");
      Objects.requireNonNull(enumName, "enumName");
      Objects.requireNonNull(variantName, "variantName");
      arguments = List.copyOf(arguments);
      Objects.requireNonNull(runtimeType, "runtimeType");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record InterfaceCall(
      BoundInterfaceMethodId requirement,
      SemanticType receiverInterfaceType,
      BoundExpression receiver,
      List<BoundArgument> arguments,
      List<BoundRuntimeType> reifiedArguments,
      boolean nullSafe,
      SemanticType type,
      SourceSpan span)
      implements BoundExpression {
    public InterfaceCall {
      Objects.requireNonNull(requirement, "requirement");
      Objects.requireNonNull(receiverInterfaceType, "receiverInterfaceType");
      Objects.requireNonNull(receiver, "receiver");
      arguments = List.copyOf(arguments);
      reifiedArguments = List.copyOf(reifiedArguments);
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record Unary(
      BoundUnaryOperator operator, BoundExpression operand, SemanticType type, SourceSpan span)
      implements BoundExpression {
    public Unary {
      Objects.requireNonNull(operator, "operator");
      Objects.requireNonNull(operand, "operand");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record Binary(
      BoundExpression left,
      BoundBinaryOperator operator,
      BoundExpression right,
      SemanticType type,
      SourceSpan span)
      implements BoundExpression {
    public Binary {
      Objects.requireNonNull(left, "left");
      Objects.requireNonNull(operator, "operator");
      Objects.requireNonNull(right, "right");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record Switch(
      BoundExpression value, List<BoundSwitchCase> cases, SemanticType type, SourceSpan span)
      implements BoundExpression {
    public Switch {
      Objects.requireNonNull(value, "value");
      cases = List.copyOf(cases);
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record Index(
      BoundExpression receiver,
      BoundExpression index,
      dev.w0fv1.norm.builtin.IntrinsicId readIntrinsic,
      java.util.Optional<dev.w0fv1.norm.builtin.IntrinsicId> writeIntrinsic,
      SemanticType type,
      SourceSpan span)
      implements BoundExpression {
    public Index {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(index, "index");
      Objects.requireNonNull(readIntrinsic, "readIntrinsic");
      Objects.requireNonNull(writeIntrinsic, "writeIntrinsic");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }
  }

  record CopyObject(BoundExpression receiver, boolean nullSafe, SemanticType type, SourceSpan span)
      implements BoundExpression {
    public CopyObject {
      Objects.requireNonNull(receiver, "receiver");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(span, "span");
    }

    public CopyObject(BoundExpression receiver, SemanticType type, SourceSpan span) {
      this(receiver, false, type, span);
    }
  }
}
