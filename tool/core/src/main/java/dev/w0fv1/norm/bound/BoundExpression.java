package dev.w0fv1.norm.bound;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.value.SourceSpan;
import java.util.List;
import java.util.Objects;

public sealed interface BoundExpression extends BoundNode
    permits BoundExpression.Literal,
        BoundExpression.NullLiteral,
        BoundExpression.ArrayLiteral,
        BoundExpression.LocalRead,
        BoundExpression.FieldRead,
        BoundExpression.EnumMember,
        BoundExpression.Unary,
        BoundExpression.Binary,
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

  record ArrayLiteral(
      List<BoundExpression> elements,
      BoundRuntimeType runtimeType,
      SemanticType type,
      SourceSpan span)
      implements BoundExpression {
    public ArrayLiteral {
      elements = List.copyOf(elements);
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

  record EnumMember(
      BoundEnumId enumId,
      BoundEnumMemberId memberId,
      String enumName,
      String memberName,
      SemanticType type,
      SourceSpan span)
      implements BoundExpression {
    public EnumMember {
      Objects.requireNonNull(enumId, "enumId");
      Objects.requireNonNull(memberId, "memberId");
      Objects.requireNonNull(enumName, "enumName");
      Objects.requireNonNull(memberName, "memberName");
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
