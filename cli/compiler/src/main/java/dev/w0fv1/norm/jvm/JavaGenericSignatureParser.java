package dev.w0fv1.norm.jvm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

public final class JavaGenericSignatureParser {
  public JavaTypeSignature parseType(String signature) {
    Objects.requireNonNull(signature, "signature");
    List<JavaTypeSignature> result = new ArrayList<>(1);
    new SignatureReader(signature).acceptType(new TypeCollector(result::add));
    if (result.size() != 1) throw new IllegalArgumentException("invalid Java type signature");
    return result.getFirst();
  }

  public JavaClassSignature parseClass(String signature) {
    Objects.requireNonNull(signature, "signature");
    ClassCollector collector = new ClassCollector();
    new SignatureReader(signature).accept(collector);
    return collector.result();
  }

  public JavaMethodSignature parseMethod(String signature) {
    Objects.requireNonNull(signature, "signature");
    MethodCollector collector = new MethodCollector();
    new SignatureReader(signature).accept(collector);
    return collector.result();
  }

  private static JavaPrimitiveType primitive(char descriptor) {
    return switch (descriptor) {
      case 'Z' -> JavaPrimitiveType.BOOLEAN;
      case 'B' -> JavaPrimitiveType.BYTE;
      case 'S' -> JavaPrimitiveType.SHORT;
      case 'I' -> JavaPrimitiveType.INT;
      case 'J' -> JavaPrimitiveType.LONG;
      case 'F' -> JavaPrimitiveType.FLOAT;
      case 'D' -> JavaPrimitiveType.DOUBLE;
      case 'C' -> JavaPrimitiveType.CHAR;
      case 'V' -> JavaPrimitiveType.VOID;
      default -> throw new IllegalArgumentException("invalid primitive descriptor " + descriptor);
    };
  }

  private abstract static class FormalCollector extends SignatureVisitor {
    protected final List<TypeParameterBuilder> typeParameters = new ArrayList<>();
    private TypeParameterBuilder current;

    private FormalCollector() {
      super(Opcodes.ASM9);
    }

    @Override
    public final void visitFormalTypeParameter(String name) {
      current = new TypeParameterBuilder(name);
      typeParameters.add(current);
    }

    @Override
    public final SignatureVisitor visitClassBound() {
      return new TypeCollector(value -> current().classBound.add(value));
    }

    @Override
    public final SignatureVisitor visitInterfaceBound() {
      return new TypeCollector(value -> current().interfaceBounds.add(value));
    }

    protected final List<JavaTypeParameter> parameters() {
      return typeParameters.stream().map(TypeParameterBuilder::build).toList();
    }

    private TypeParameterBuilder current() {
      if (current == null) throw new IllegalArgumentException("type bound has no parameter");
      return current;
    }
  }

  private static final class ClassCollector extends FormalCollector {
    private final List<JavaClassTypeSignature> superclasses = new ArrayList<>(1);
    private final List<JavaClassTypeSignature> interfaces = new ArrayList<>();

    @Override
    public SignatureVisitor visitSuperclass() {
      return classType(superclasses::add);
    }

    @Override
    public SignatureVisitor visitInterface() {
      return classType(interfaces::add);
    }

    private JavaClassSignature result() {
      if (superclasses.size() != 1) {
        throw new IllegalArgumentException("Java class signature must have one superclass");
      }
      return new JavaClassSignature(
          parameters(), java.util.Optional.of(superclasses.getFirst()), interfaces);
    }
  }

  private static final class MethodCollector extends FormalCollector {
    private final List<JavaTypeSignature> parameters = new ArrayList<>();
    private final List<JavaTypeSignature> returns = new ArrayList<>(1);
    private final List<JavaTypeSignature> exceptions = new ArrayList<>();

    @Override
    public SignatureVisitor visitParameterType() {
      return new TypeCollector(parameters::add);
    }

    @Override
    public SignatureVisitor visitReturnType() {
      return new TypeCollector(returns::add);
    }

    @Override
    public SignatureVisitor visitExceptionType() {
      return new TypeCollector(exceptions::add);
    }

    private JavaMethodSignature result() {
      if (returns.size() != 1) {
        throw new IllegalArgumentException("Java method signature must have one return type");
      }
      return new JavaMethodSignature(parameters(), this.parameters, returns.getFirst(), exceptions);
    }
  }

  private static SignatureVisitor classType(Consumer<JavaClassTypeSignature> consumer) {
    return new TypeCollector(
        value -> {
          if (!(value instanceof JavaClassTypeSignature classType)) {
            throw new IllegalArgumentException("Java class signature requires a class type");
          }
          consumer.accept(classType);
        });
  }

  private static final class TypeCollector extends SignatureVisitor {
    private final Consumer<JavaTypeSignature> consumer;
    private final List<SegmentBuilder> segments = new ArrayList<>();

    private TypeCollector(Consumer<JavaTypeSignature> consumer) {
      super(Opcodes.ASM9);
      this.consumer = consumer;
    }

    @Override
    public void visitBaseType(char descriptor) {
      consumer.accept(new JavaPrimitiveTypeSignature(primitive(descriptor)));
    }

    @Override
    public void visitTypeVariable(String name) {
      consumer.accept(new JavaTypeVariableSignature(name));
    }

    @Override
    public SignatureVisitor visitArrayType() {
      return new TypeCollector(value -> consumer.accept(new JavaArrayTypeSignature(value)));
    }

    @Override
    public void visitClassType(String name) {
      segments.add(new SegmentBuilder(name.replace('/', '.')));
    }

    @Override
    public void visitInnerClassType(String name) {
      segments.add(new SegmentBuilder(name));
    }

    @Override
    public void visitTypeArgument() {
      current().arguments.add(JavaTypeArgument.unbounded());
    }

    @Override
    public SignatureVisitor visitTypeArgument(char wildcard) {
      JavaTypeVariance variance =
          switch (wildcard) {
            case SignatureVisitor.INSTANCEOF -> JavaTypeVariance.EXACT;
            case SignatureVisitor.EXTENDS -> JavaTypeVariance.EXTENDS;
            case SignatureVisitor.SUPER -> JavaTypeVariance.SUPER;
            default -> throw new IllegalArgumentException("invalid Java type variance " + wildcard);
          };
      return new TypeCollector(
          value -> current().arguments.add(JavaTypeArgument.of(variance, value)));
    }

    @Override
    public void visitEnd() {
      consumer.accept(
          new JavaClassTypeSignature(segments.stream().map(SegmentBuilder::build).toList()));
    }

    private SegmentBuilder current() {
      if (segments.isEmpty()) throw new IllegalArgumentException("type argument has no class");
      return segments.getLast();
    }
  }

  private static final class SegmentBuilder {
    private final String name;
    private final List<JavaTypeArgument> arguments = new ArrayList<>();

    private SegmentBuilder(String name) {
      this.name = name;
    }

    private JavaClassTypeSegment build() {
      return new JavaClassTypeSegment(name, arguments);
    }
  }

  private static final class TypeParameterBuilder {
    private final String name;
    private final List<JavaTypeSignature> classBound = new ArrayList<>(1);
    private final List<JavaTypeSignature> interfaceBounds = new ArrayList<>();

    private TypeParameterBuilder(String name) {
      this.name = name;
    }

    private JavaTypeParameter build() {
      if (classBound.size() > 1) {
        throw new IllegalArgumentException("Java type parameter has multiple class bounds");
      }
      return new JavaTypeParameter(name, classBound.stream().findFirst(), interfaceBounds);
    }
  }
}
