package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.semantic.SymbolKind;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

record DeclarationIdentity(String value, String family) {
  DeclarationIdentity {
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(family, "family");
  }

  static DeclarationIdentity topLevel(Syntax.Program program, Object declaration) {
    String kind;
    String name;
    String discriminator;
    Syntax.Visibility visibility;
    if (declaration instanceof Syntax.EnumDecl value) {
      kind = "enum";
      name = value.name();
      discriminator = name;
      visibility = value.visibility();
    } else if (declaration instanceof Syntax.InterfaceDecl value) {
      kind = "interface";
      name = value.name();
      discriminator = name;
      visibility = value.visibility();
    } else if (declaration instanceof Syntax.AggregateDecl value) {
      kind = "aggregate";
      name = value.name();
      discriminator = name;
      visibility = value.visibility();
    } else if (declaration instanceof Syntax.FunctionDecl value) {
      kind = "function";
      name = value.name();
      discriminator = functionSignature(value);
      visibility = value.visibility();
    } else {
      throw new IllegalArgumentException("unsupported top-level declaration");
    }
    String author =
        encode(program.span().source().id().uri().toString(), program.packageName(), kind, name);
    String family =
        visibility == Syntax.Visibility.PRIVATE
            ? author
            : encode(program.packageName(), kind, name);
    return new DeclarationIdentity(author + encode(discriminator), family);
  }

  static String functionSignature(Syntax.FunctionDecl function) {
    return callableSignature(function.name(), function.typeParameters(), function.parameters());
  }

  static String member(SymbolId owner, SymbolKind kind, Object declaration, String name) {
    String discriminator;
    if (declaration instanceof Syntax.FunctionDecl function) {
      discriminator = functionSignature(function);
    } else if (declaration instanceof Syntax.InterfaceMethodDecl method) {
      discriminator =
          callableSignature(method.name(), method.typeParameters(), method.parameters());
    } else if (declaration instanceof Syntax.ConstructorDecl constructor) {
      discriminator =
          callableSignature(constructor.name(), java.util.List.of(), constructor.parameters());
    } else if (declaration instanceof Syntax.EnumVariant variant) {
      discriminator = variant.name();
    } else if (declaration instanceof Syntax.FieldDecl field) {
      discriminator = field.name();
    } else if (declaration instanceof Syntax.Parameter parameter) {
      discriminator = parameter.name();
    } else {
      throw new IllegalArgumentException("unsupported member declaration");
    }
    return encode(owner.value(), kind.name(), name, discriminator);
  }

  static String typeParameter(SymbolId owner, int index) {
    return encode(owner.value(), SymbolKind.TYPE_PARAMETER.name(), Integer.toString(index));
  }

  static String synthetic(SymbolId owner, String name) {
    return encode(owner.value(), "synthetic", name);
  }

  private static String callableSignature(
      String name,
      java.util.List<Syntax.TypeParameter> declaredTypeParameters,
      java.util.List<Syntax.Parameter> parameters) {
    Map<String, String> typeParameters = new HashMap<>();
    for (int index = 0; index < declaredTypeParameters.size(); index++) {
      typeParameters.put(declaredTypeParameters.get(index).name(), "$" + index);
    }
    return name
        + "("
        + parameters.stream()
            .map(parameter -> normalizedType(parameter.type(), typeParameters))
            .collect(Collectors.joining(","))
        + ")";
  }

  private static String normalizedType(Syntax.TypeRef type, Map<String, String> typeParameters) {
    if (type.isWildcard()) return "?";
    String name = typeParameters.getOrDefault(type.name(), type.name());
    String arguments =
        type.arguments().isEmpty()
            ? ""
            : type.arguments().stream()
                .map(argument -> normalizedType(argument, typeParameters))
                .collect(Collectors.joining(",", "<", ">"));
    return name + arguments + (type.nullable() ? "?" : "");
  }

  private static String encode(String... segments) {
    StringBuilder result = new StringBuilder();
    for (String segment : segments) result.append(segment.length()).append(':').append(segment);
    return result.toString();
  }
}
