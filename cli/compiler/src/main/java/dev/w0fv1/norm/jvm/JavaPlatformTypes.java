package dev.w0fv1.norm.jvm;

import java.util.Optional;
import java.util.Set;

final class JavaPlatformTypes {
  private static final Set<String> EXCEPTIONS =
      Set.of("java.lang.Throwable", "java.lang.Exception", "java.lang.RuntimeException");
  private static final Set<String> LISTS =
      Set.of(
          "java.util.AbstractList",
          "java.util.AbstractSequentialList",
          "java.util.ArrayList",
          "java.util.LinkedList",
          "java.util.List",
          "java.util.Stack",
          "java.util.Vector",
          "java.util.concurrent.CopyOnWriteArrayList");
  private static final Set<String> SETS =
      Set.of(
          "java.util.AbstractSet",
          "java.util.EnumSet",
          "java.util.HashSet",
          "java.util.LinkedHashSet",
          "java.util.Set",
          "java.util.SortedSet",
          "java.util.TreeSet",
          "java.util.concurrent.ConcurrentSkipListSet",
          "java.util.concurrent.CopyOnWriteArraySet");
  private static final Set<String> MAPS =
      Set.of(
          "java.util.AbstractMap",
          "java.util.EnumMap",
          "java.util.HashMap",
          "java.util.IdentityHashMap",
          "java.util.LinkedHashMap",
          "java.util.Map",
          "java.util.SortedMap",
          "java.util.TreeMap",
          "java.util.WeakHashMap",
          "java.util.concurrent.ConcurrentHashMap",
          "java.util.concurrent.ConcurrentMap",
          "java.util.concurrent.ConcurrentSkipListMap");
  private static final Set<String> COLLECTIONS =
      Set.of("java.util.AbstractCollection", "java.util.Collection");

  private JavaPlatformTypes() {}

  static Optional<JavaReferenceKind> referenceKind(String binaryName) {
    if (binaryName.equals("java.lang.Object")) return Optional.of(JavaReferenceKind.OBJECT);
    if (binaryName.equals("java.lang.Class")) return Optional.of(JavaReferenceKind.CLASS);
    if (binaryName.equals("java.util.Optional")) return Optional.of(JavaReferenceKind.OPTIONAL);
    if (binaryName.equals("java.util.OptionalInt")) {
      return Optional.of(JavaReferenceKind.OPTIONAL_INT);
    }
    if (binaryName.equals("java.util.OptionalLong")) {
      return Optional.of(JavaReferenceKind.OPTIONAL_LONG);
    }
    if (binaryName.equals("java.util.OptionalDouble")) {
      return Optional.of(JavaReferenceKind.OPTIONAL_DOUBLE);
    }
    if (binaryName.equals("java.lang.Iterable")) {
      return Optional.of(JavaReferenceKind.ITERABLE);
    }
    if (binaryName.equals("java.util.Iterator")) {
      return Optional.of(JavaReferenceKind.ITERATOR);
    }
    if (COLLECTIONS.contains(binaryName)) {
      return Optional.of(JavaReferenceKind.COLLECTION);
    }
    if (LISTS.contains(binaryName)) return Optional.of(JavaReferenceKind.LIST);
    if (SETS.contains(binaryName)) return Optional.of(JavaReferenceKind.SET);
    if (MAPS.contains(binaryName)) return Optional.of(JavaReferenceKind.MAP);
    if (binaryName.equals("java.lang.String")) return Optional.of(JavaReferenceKind.STRING);
    if (binaryName.equals("java.lang.Void")) return Optional.of(JavaReferenceKind.UNIT);
    if (binaryName.equals("java.lang.CharSequence")) {
      return Optional.of(JavaReferenceKind.CHAR_SEQUENCE);
    }
    if (binaryName.equals("java.lang.Number")) return Optional.of(JavaReferenceKind.NUMBER);
    if (binaryName.equals("java.nio.charset.Charset")) {
      return Optional.of(JavaReferenceKind.CHARSET);
    }
    if (EXCEPTIONS.contains(binaryName)) return Optional.of(JavaReferenceKind.EXCEPTION);
    if (binaryName.equals("java.io.InputStream")) {
      return Optional.of(JavaReferenceKind.INPUT_STREAM);
    }
    if (binaryName.equals("java.io.OutputStream")) {
      return Optional.of(JavaReferenceKind.OUTPUT_STREAM);
    }
    if (binaryName.equals("java.util.concurrent.Future")
        || binaryName.equals("java.util.concurrent.CompletionStage")
        || binaryName.equals("java.util.concurrent.CompletableFuture")) {
      return Optional.of(JavaReferenceKind.TASK);
    }
    if (binaryName.equals("java.time.Duration")) {
      return Optional.of(JavaReferenceKind.DURATION);
    }
    if (binaryName.equals("java.net.URI") || binaryName.equals("java.net.URL")) {
      return Optional.of(JavaReferenceKind.URI);
    }
    if (binaryName.equals("java.nio.file.Path")) return Optional.of(JavaReferenceKind.PATH);
    if (binaryName.equals("java.io.File")) return Optional.of(JavaReferenceKind.FILE);
    return Optional.empty();
  }

  static boolean isException(String binaryName) {
    return EXCEPTIONS.contains(binaryName);
  }

  static boolean classTokenCompatible(JavaBindingType type) {
    return switch (type) {
      case JavaArrayType ignored -> true;
      case JavaBindingTypeVariable ignored -> true;
      case JavaCallbackType ignored -> false;
      case JavaPrimitiveType ignored -> true;
      case JavaBoxedType ignored -> false;
      case JavaReferenceType reference ->
          switch (reference.kind()) {
            case OBJECT, STRING, NUMBER, ENUM, OPAQUE, RESOURCE -> true;
            case CHAR_SEQUENCE,
                CHARSET,
                CLASS,
                EXCEPTION,
                FILE,
                INPUT_STREAM,
                OUTPUT_STREAM,
                TASK,
                DURATION,
                URI,
                OPTIONAL,
                OPTIONAL_INT,
                OPTIONAL_LONG,
                OPTIONAL_DOUBLE,
                ITERABLE,
                ITERATOR,
                COLLECTION,
                LIST,
                SET,
                MAP,
                UNIT,
                PATH ->
                false;
          };
    };
  }
}
