package dev.w0fv1.norm.jvm;

import static dev.w0fv1.norm.jvm.BindingTypeNames.normType;

import dev.w0fv1.norm.builtin.BuiltinCatalog;
import dev.w0fv1.norm.syntax.LanguageSyntax;
import dev.w0fv1.norm.value.Sha256Digest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class BindingNames {
  private BindingNames() {}

  private static final String BINDING_ABI = "java-v14";
  private static final Set<String> RESERVED_TYPE_NAMES =
      java.util.stream.Stream.concat(
              BuiltinCatalog.standard().typeNames().stream(),
              java.util.stream.Stream.of(
                  "Comparable",
                  "Exception",
                  "InputStream",
                  "Iterable",
                  "IterableView",
                  "Iterator",
                  "IteratorView",
                  "MutableCollection",
                  "MutableList",
                  "MutableMap",
                  "MutableSet",
                  "OutputStream",
                  "Path",
                  "Resource",
                  "Task",
                  "Unit",
                  "Uri"))
          .collect(java.util.stream.Collectors.toUnmodifiableSet());

  static String allocateTypePath(
      String preferred, String binaryName, Map<String, String> allocated) {
    int separator = preferred.lastIndexOf('.');
    String prefix = separator < 0 ? "" : preferred.substring(0, separator + 1);
    String name = simpleName(preferred);
    String safeName = RESERVED_TYPE_NAMES.contains(name) ? "Java" + name : name;
    String candidate = prefix + safeName;
    boolean occupied =
        allocated.entrySet().stream()
            .anyMatch(
                entry -> !entry.getKey().equals(binaryName) && entry.getValue().equals(candidate));
    if (!occupied) return candidate;
    String digest =
        Sha256Digest.compute(binaryName.getBytes(StandardCharsets.UTF_8)).value().substring(0, 8);
    return prefix + safeName + "X" + digest;
  }

  static void addSignature(
      Map<String, JavaBindingCallable> signatures,
      String name,
      JavaBindingCallable callable,
      BindingTypeNames normTypes) {
    if (signatures.putIfAbsent(signature(name, callable, normTypes), callable) != null) {
      throw new IllegalArgumentException(
          "Java overloads collapse to the same Norm signature: "
              + callable.owner()
              + "."
              + callable.name()
              + callable.descriptor());
    }
  }

  static Map<JavaBindingCallable, String> allocateNames(
      List<JavaBindingCallable> callables,
      Function<JavaBindingCallable, String> baseName,
      BindingTypeNames normTypes) {
    Map<String, List<JavaBindingCallable>> groups = new LinkedHashMap<>();
    for (JavaBindingCallable callable : callables) {
      String name = baseName.apply(callable);
      String signature = signature(name, callable, normTypes);
      groups.computeIfAbsent(signature, ignored -> new ArrayList<>()).add(callable);
    }
    Map<JavaBindingCallable, String> names = new LinkedHashMap<>();
    for (List<JavaBindingCallable> group : groups.values()) {
      Map<JavaBindingCallable, String> candidates = new LinkedHashMap<>();
      for (JavaBindingCallable callable : group) {
        String name = baseName.apply(callable);
        if (group.size() > 1) name += "Java" + parameterSuffix(callable);
        candidates.put(callable, name);
      }
      Map<String, Long> counts =
          candidates.values().stream()
              .collect(
                  java.util.stream.Collectors.groupingBy(
                      Function.identity(),
                      LinkedHashMap::new,
                      java.util.stream.Collectors.counting()));
      for (JavaBindingCallable callable : group) {
        String name = candidates.get(callable);
        if (counts.get(name) > 1) {
          name +=
              "Java"
                  + Sha256Digest.compute(
                          (callable.name() + callable.descriptor())
                              .getBytes(StandardCharsets.UTF_8))
                      .value()
                      .substring(0, 8);
        }
        names.put(callable, name);
      }
    }
    return Map.copyOf(names);
  }

  static Map<JavaBindingCallable, String> allocateEnumNames(
      List<JavaBindingCallable> callables, String prefix, BindingTypeNames normTypes) {
    Map<String, List<JavaBindingCallable>> groups = new LinkedHashMap<>();
    for (JavaBindingCallable callable : callables) {
      String name = enumFunctionName(prefix, callable);
      groups
          .computeIfAbsent(enumSignature(name, callable, normTypes), ignored -> new ArrayList<>())
          .add(callable);
    }
    Map<JavaBindingCallable, String> names = new LinkedHashMap<>();
    for (List<JavaBindingCallable> group : groups.values()) {
      for (JavaBindingCallable callable : group) {
        String name = enumFunctionName(prefix, callable);
        if (group.size() > 1) name += "Java" + parameterSuffix(callable);
        names.put(callable, name);
      }
    }
    return Map.copyOf(names);
  }

  static void addEnumSignature(
      Map<String, JavaBindingCallable> signatures,
      String name,
      JavaBindingCallable callable,
      BindingTypeNames normTypes) {
    if (signatures.putIfAbsent(enumSignature(name, callable, normTypes), callable) != null) {
      throw new IllegalArgumentException(
          "Java overloads collapse to the same Norm signature: "
              + callable.owner()
              + "."
              + callable.name()
              + callable.descriptor());
    }
  }

  static String enumSignature(
      String name, JavaBindingCallable callable, BindingTypeNames normTypes) {
    String receiver = callable.kind().requiresReceiver() ? "<enum>," : "";
    return name
        + callable.parameters().stream()
            .map(type -> normType(type, normTypes, false))
            .collect(java.util.stream.Collectors.joining(",", "(" + receiver, ")"));
  }

  static String signature(String name, JavaBindingCallable callable, BindingTypeNames normTypes) {
    return name
        + callable.parameters().stream()
            .map(type -> normType(type, normTypes, false))
            .collect(java.util.stream.Collectors.joining(",", "(", ")"));
  }

  static String parameterSuffix(JavaBindingCallable callable) {
    if (callable.parameters().isEmpty()) return "NoArguments";
    return callable.parameters().stream()
        .map(BindingNames::bindingTypeSuffix)
        .collect(java.util.stream.Collectors.joining("And"));
  }

  static String bindingTypeSuffix(JavaBindingType type) {
    return switch (type) {
      case JavaArrayType array -> bindingTypeSuffix(array.component()) + "Array";
      case JavaPrimitiveType primitive -> upperCamel(primitive.name().toLowerCase(Locale.ROOT));
      case JavaBoxedType boxed ->
          "Boxed" + upperCamel(boxed.primitive().name().toLowerCase(Locale.ROOT));
      case JavaBindingTypeVariable variable -> "Type" + variable.name();
      case JavaCallbackType callback -> simpleName(callback.binaryName()).replace('$', '_');
      case JavaReferenceType reference ->
          reference.kind() == JavaReferenceKind.OBJECT
              ? "Any"
              : simpleName(reference.binaryName()).replace('$', '_');
    };
  }

  static String functionName(String prefix, JavaBindingCallable callable) {
    return switch (callable.kind()) {
      case CONSTRUCTOR -> prefix + "New";
      case STATIC_METHOD -> prefix + upperCamel(callable.name());
      case STATIC_FIELD_GET -> prefix + "FieldGet" + fieldName(callable.name());
      case STATIC_FIELD_SET -> prefix + "FieldSet" + fieldName(callable.name());
      case ARRAY_CONSTRUCTOR, ARRAY_LENGTH, ARRAY_GET, ARRAY_SET ->
          throw new IllegalArgumentException("array binding is generated separately");
      case INSTANCE_METHOD, INSTANCE_FIELD_GET, INSTANCE_FIELD_SET ->
          throw new IllegalArgumentException("instance binding cannot generate a function");
    };
  }

  static String enumFunctionName(String prefix, JavaBindingCallable callable) {
    return callable.kind().requiresReceiver()
        ? prefix + upperCamel(memberName(callable))
        : functionName(prefix, callable);
  }

  static Map<String, String> enumVariants(JavaApiType owner) {
    Map<String, String> variants = new LinkedHashMap<>();
    owner.fields().stream()
        .filter(field -> (field.modifiers() & org.objectweb.asm.Opcodes.ACC_ENUM) != 0)
        .forEach(
            field -> {
              String name = enumVariantName(field.name());
              if (variants.containsKey(name)) {
                name +=
                    "_"
                        + Sha256Digest.compute(field.name().getBytes(StandardCharsets.UTF_8))
                            .value()
                            .substring(0, 8);
              }
              variants.put(name, field.name());
            });
    if (variants.isEmpty()) {
      throw new IllegalArgumentException(
          "Java enum has no public constants: " + owner.binaryName());
    }
    return java.util.Collections.unmodifiableMap(variants);
  }

  static String enumVariantName(String javaName) {
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < javaName.length(); index++) {
      char value = javaName.charAt(index);
      if (value >= 'A' && value <= 'Z'
          || value >= 'a' && value <= 'z'
          || value >= '0' && value <= '9'
          || value == '_') {
        result.append(value);
      } else {
        result.append("_u").append(String.format(Locale.ROOT, "%04X", (int) value)).append('_');
      }
    }
    return result.toString();
  }

  static String memberName(JavaBindingCallable callable) {
    return switch (callable.kind()) {
      case INSTANCE_METHOD -> normIdentifier(callable.name());
      case INSTANCE_FIELD_GET -> "fieldGet" + fieldName(callable.name());
      case INSTANCE_FIELD_SET -> "fieldSet" + fieldName(callable.name());
      case ARRAY_CONSTRUCTOR, ARRAY_LENGTH, ARRAY_GET, ARRAY_SET ->
          throw new IllegalArgumentException("array binding is generated separately");
      case CONSTRUCTOR, STATIC_METHOD, STATIC_FIELD_GET, STATIC_FIELD_SET ->
          throw new IllegalArgumentException("static binding cannot generate a member");
    };
  }

  static String normIdentifier(String value) {
    if (LanguageSyntax.isIdentifier(value)) return value;
    StringBuilder result = new StringBuilder();
    for (int offset = 0; offset < value.length(); ) {
      int character = value.codePointAt(offset);
      boolean valid =
          result.isEmpty()
              ? character == '_' || Character.isUnicodeIdentifierStart(character)
              : Character.isUnicodeIdentifierPart(character);
      if (valid) {
        result.appendCodePoint(character);
      } else {
        result.append("_u").append(String.format(Locale.ROOT, "%04X", character)).append('_');
      }
      offset += Character.charCount(character);
    }
    String identifier = result.toString();
    return LanguageSyntax.isIdentifier(identifier) ? identifier : identifier + "Value";
  }

  static String callId(Sha256Digest graphId, JavaBindingCallable callable) {
    String exposedSignature =
        callable.typeParameters().stream()
                .map(
                    parameter ->
                        parameter.name()
                            + parameter.bound().map(bound -> ":" + bound.displayName()).orElse(""))
                .collect(java.util.stream.Collectors.joining(",", "<", ">"))
            + callable.parameters().stream()
                .map(JavaBindingType::displayName)
                .collect(java.util.stream.Collectors.joining(",", "(", ")"))
            + callable.returnType().displayName()
            + ":"
            + callable.returnNullability().name();
    String exposedId =
        Sha256Digest.compute(exposedSignature.getBytes(StandardCharsets.UTF_8)).value();
    return BINDING_ABI
        + ":"
        + graphId.value()
        + ":"
        + exposedId
        + ":"
        + callable.kind().name().toLowerCase(Locale.ROOT)
        + ":"
        + callable.owner()
        + ":"
        + callable.name()
        + ":"
        + callable.descriptor();
  }

  static String exportPath(String selectedName, JavaApiType owner) {
    if (owner.enclosingType().isEmpty()) return selectedName;
    String binaryName = owner.binaryName();
    String localName = binaryName.substring(binaryName.lastIndexOf('.') + 1).replace('$', '.');
    if (!selectedName.endsWith(localName)) return selectedName;
    return selectedName.substring(0, selectedName.length() - localName.length())
        + localName.replace(".", "");
  }

  static String exportPackage(String exportedName) {
    int separator = exportedName.lastIndexOf('.');
    return separator < 0 ? "" : "." + exportedName.substring(0, separator);
  }

  static String simpleName(String value) {
    int separator = value.lastIndexOf('.');
    return separator < 0 ? value : value.substring(separator + 1);
  }

  static String lowerCamel(String value) {
    int uppercasePrefix = 0;
    while (uppercasePrefix < value.length()
        && Character.isUpperCase(value.charAt(uppercasePrefix))) {
      uppercasePrefix++;
    }
    int lowercaseLength =
        uppercasePrefix == value.length() ? uppercasePrefix : Math.max(1, uppercasePrefix - 1);
    return value.substring(0, lowercaseLength).toLowerCase(Locale.ROOT)
        + value.substring(lowercaseLength);
  }

  static String upperCamel(String value) {
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  static String fieldName(String value) {
    if (!value.contains("_")) {
      return value.equals(value.toUpperCase(Locale.ROOT))
          ? upperCamel(value.toLowerCase(Locale.ROOT))
          : upperCamel(value);
    }
    return java.util.Arrays.stream(value.split("_+"))
        .filter(part -> !part.isEmpty())
        .map(part -> upperCamel(part.toLowerCase(Locale.ROOT)))
        .collect(java.util.stream.Collectors.joining());
  }

  static Map<JavaAnnotationElementBinding, String> annotationElementNames(
      List<JavaAnnotationElementBinding> elements) {
    Map<JavaAnnotationElementBinding, String> names = new LinkedHashMap<>();
    Set<String> used = new java.util.LinkedHashSet<>();
    elements.stream()
        .map(JavaAnnotationElementBinding::name)
        .filter(LanguageSyntax::isIdentifier)
        .forEach(used::add);
    for (JavaAnnotationElementBinding element : elements) {
      if (LanguageSyntax.isIdentifier(element.name())) {
        names.put(element, element.name());
        continue;
      }
      String name = normIdentifier(element.name());
      if (!used.add(name)) {
        name +=
            "Java"
                + Sha256Digest.compute(element.name().getBytes(StandardCharsets.UTF_8))
                    .value()
                    .substring(0, 8);
        used.add(name);
      }
      names.put(element, name);
    }
    return Map.copyOf(names);
  }
}
