package dev.w0fv1.norm.jvm;

import static org.objectweb.asm.Opcodes.*;

import dev.w0fv1.norm.bridge.JavaDirectCall;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public final class JavaDirectCallGenerator {
  public byte[] generate(String binaryName, JavaCallTarget call, ClassLoader loader)
      throws ClassNotFoundException {
    var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    writer.visit(
        V17,
        ACC_PUBLIC | ACC_FINAL,
        binaryName.replace('.', '/'),
        null,
        "java/lang/Object",
        new String[] {Type.getInternalName(JavaDirectCall.class)});
    var constructor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(ALOAD, 0);
    constructor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitInsn(RETURN);
    constructor.visitMaxs(0, 0);
    constructor.visitEnd();
    var method =
        writer.visitMethod(
            ACC_PUBLIC,
            "invoke",
            "([Ljava/lang/Object;)Ljava/lang/Object;",
            null,
            new String[] {"java/lang/Throwable"});
    method.visitCode();
    Type result;
    String owner = call.owner().replace('.', '/');
    if (call.kind().name().startsWith("ARRAY_")) {
      Type array = Type.getType(owner);
      Type element = Type.getType(owner.substring(1));
      if (call.kind() != JavaCallableKind.ARRAY_CONSTRUCTOR) argument(method, 0, array);
      switch (call.kind()) {
        case ARRAY_CONSTRUCTOR -> {
          argument(method, 0, Type.INT_TYPE);
          if (element.getSort() == Type.OBJECT || element.getSort() == Type.ARRAY) {
            method.visitTypeInsn(ANEWARRAY, element.getInternalName());
          } else {
            int code =
                switch (element.getSort()) {
                  case Type.BOOLEAN -> T_BOOLEAN;
                  case Type.CHAR -> T_CHAR;
                  case Type.BYTE -> T_BYTE;
                  case Type.SHORT -> T_SHORT;
                  case Type.INT -> T_INT;
                  case Type.FLOAT -> T_FLOAT;
                  case Type.LONG -> T_LONG;
                  case Type.DOUBLE -> T_DOUBLE;
                  default ->
                      throw new IllegalArgumentException("invalid array component " + element);
                };
            method.visitIntInsn(NEWARRAY, code);
          }
          result = array;
        }
        case ARRAY_LENGTH -> {
          method.visitInsn(ARRAYLENGTH);
          result = Type.INT_TYPE;
        }
        case ARRAY_GET -> {
          argument(method, 1, Type.INT_TYPE);
          method.visitInsn(element.getOpcode(IALOAD));
          result = element;
        }
        case ARRAY_SET -> {
          argument(method, 1, Type.INT_TYPE);
          argument(method, 2, element);
          method.visitInsn(element.getOpcode(IASTORE));
          result = Type.VOID_TYPE;
        }
        default -> throw new IllegalStateException("not an array operation");
      }
    } else {
      boolean ownerInterface = Class.forName(call.owner(), false, loader).isInterface();
      if (call.kind() == JavaCallableKind.CONSTRUCTOR) {
        method.visitTypeInsn(NEW, owner);
        method.visitInsn(DUP);
      }
      int index = 0;
      if (call.kind().requiresReceiver()) argument(method, index++, Type.getObjectType(owner));
      if (call.kind().isField()) {
        Type field = Type.getType(call.descriptor());
        boolean setter =
            call.kind() == JavaCallableKind.INSTANCE_FIELD_SET
                || call.kind() == JavaCallableKind.STATIC_FIELD_SET;
        if (setter) argument(method, index, field);
        int opcode =
            switch (call.kind()) {
              case INSTANCE_FIELD_GET -> GETFIELD;
              case INSTANCE_FIELD_SET -> PUTFIELD;
              case STATIC_FIELD_GET -> GETSTATIC;
              case STATIC_FIELD_SET -> PUTSTATIC;
              default -> throw new IllegalStateException("not a field operation");
            };
        method.visitFieldInsn(opcode, owner, call.name(), field.getDescriptor());
        result = setter ? Type.VOID_TYPE : field;
      } else {
        Type[] parameters = Type.getArgumentTypes(call.descriptor());
        for (Type parameter : parameters) argument(method, index++, parameter);
        boolean creating = call.kind() == JavaCallableKind.CONSTRUCTOR;
        int opcode =
            creating
                ? INVOKESPECIAL
                : call.kind() == JavaCallableKind.STATIC_METHOD
                    ? INVOKESTATIC
                    : ownerInterface ? INVOKEINTERFACE : INVOKEVIRTUAL;
        method.visitMethodInsn(
            opcode,
            owner,
            creating ? "<init>" : call.name(),
            creating ? Type.getMethodDescriptor(Type.VOID_TYPE, parameters) : call.descriptor(),
            ownerInterface);
        result = creating ? Type.getObjectType(owner) : Type.getReturnType(call.descriptor());
      }
    }
    if (result.getSort() == Type.VOID) {
      method.visitInsn(ACONST_NULL);
    } else if (result.getSort() != Type.OBJECT && result.getSort() != Type.ARRAY) {
      String boxed = wrapper(result);
      method.visitMethodInsn(
          INVOKESTATIC, boxed, "valueOf", "(" + result.getDescriptor() + ")L" + boxed + ";", false);
    }
    method.visitInsn(ARETURN);
    method.visitMaxs(0, 0);
    method.visitEnd();
    writer.visitEnd();
    return writer.toByteArray();
  }

  private static void argument(MethodVisitor method, int index, Type type) {
    method.visitVarInsn(ALOAD, 1);
    method.visitLdcInsn(index);
    method.visitInsn(AALOAD);
    if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
      method.visitTypeInsn(CHECKCAST, type.getInternalName());
    } else {
      String boxed = wrapper(type);
      method.visitTypeInsn(CHECKCAST, boxed);
      method.visitMethodInsn(
          INVOKEVIRTUAL, boxed, type.getClassName() + "Value", "()" + type.getDescriptor(), false);
    }
  }

  private static String wrapper(Type type) {
    return "java/lang/"
        + switch (type.getSort()) {
          case Type.BOOLEAN -> "Boolean";
          case Type.CHAR -> "Character";
          case Type.BYTE -> "Byte";
          case Type.SHORT -> "Short";
          case Type.INT -> "Integer";
          case Type.FLOAT -> "Float";
          case Type.LONG -> "Long";
          case Type.DOUBLE -> "Double";
          default -> throw new IllegalArgumentException("not a primitive " + type);
        };
  }
}
