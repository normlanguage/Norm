package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundCallableId;
import dev.w0fv1.norm.semantic.SemanticModel;
import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.semantic.Symbol;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.Syntax;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DefaultArgumentDeclarations {
  private final Map<SymbolId, Declaration> declarations = new LinkedHashMap<>();

  DefaultArgumentDeclarations(List<Syntax.Program> programs, SemanticModel semantics) {
    for (var program : programs) {
      for (var function : program.functions()) {
        add(
            semantics,
            function.nameSpan(),
            List.of(),
            function.typeParameters(),
            Optional.empty(),
            function.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
      }
      for (var owner : program.aggregates()) {
        var symbol = semantics.symbolOf(owner.nameSpan()).orElseThrow();
        var receiver = receiver(semantics, symbol, owner.typeParameters());
        if (owner.constructors().isEmpty()) {
          add(
              semantics,
              owner.nameSpan(),
              owner.typeParameters(),
              List.of(),
              Optional.empty(),
              owner.fields().stream()
                  .filter(
                      field ->
                          symbol.parameters().stream()
                              .anyMatch(parameter -> parameter.name().equals(field.name())))
                  .map(Syntax.FieldDecl::defaultValue)
                  .toList());
        } else {
          for (var constructor : owner.constructors()) {
            add(
                semantics,
                constructor.nameSpan(),
                owner.typeParameters(),
                List.of(),
                Optional.empty(),
                constructor.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
          }
        }
        for (var method : owner.methods()) {
          add(
              semantics,
              method.nameSpan(),
              owner.typeParameters(),
              method.typeParameters(),
              Optional.of(receiver),
              method.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
        }
      }
      for (var owner : program.interfaces()) {
        var receiver =
            receiver(
                semantics,
                semantics.symbolOf(owner.nameSpan()).orElseThrow(),
                owner.typeParameters());
        for (var method : owner.methods()) {
          add(
              semantics,
              method.nameSpan(),
              owner.typeParameters(),
              method.typeParameters(),
              Optional.of(receiver),
              method.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
        }
      }
      for (var owner : program.enums()) {
        for (var variant : owner.variants()) {
          add(
              semantics,
              variant.nameSpan(),
              owner.typeParameters(),
              List.of(),
              Optional.empty(),
              variant.parameters().stream().map(Syntax.Parameter::defaultValue).toList());
        }
      }
    }
  }

  List<Declaration> declarations() {
    return List.copyOf(declarations.values());
  }

  Optional<Declaration> declaration(SymbolId target) {
    return Optional.ofNullable(declarations.get(target));
  }

  Optional<BoundCallableId> defaultValue(SymbolId target, int parameter) {
    return declaration(target)
        .flatMap(
            value -> value.defaults().get(parameter).map(expression -> value.factory(parameter)));
  }

  private void add(
      SemanticModel semantics,
      SourceSpan name,
      List<Syntax.TypeParameter> ownerParameters,
      List<Syntax.TypeParameter> callableParameters,
      Optional<SemanticType> receiver,
      List<Optional<Syntax.Expression>> defaults) {
    if (defaults.stream().noneMatch(Optional::isPresent)) return;
    var symbol = semantics.symbolOf(name).orElseThrow();
    var parameters = new ArrayList<>(ownerParameters);
    parameters.addAll(callableParameters);
    declarations.put(symbol.id(), new Declaration(symbol, parameters, receiver, defaults));
  }

  private static SemanticType receiver(
      SemanticModel semantics, Symbol owner, List<Syntax.TypeParameter> parameters) {
    return SemanticType.declared(
        owner.type().identity(),
        owner.name(),
        parameters.stream()
            .map(parameter -> semantics.symbolOf(parameter.nameSpan()).orElseThrow().type())
            .toList(),
        owner.type().category());
  }

  record Declaration(
      Symbol symbol,
      List<Syntax.TypeParameter> typeParameters,
      Optional<SemanticType> receiver,
      List<Optional<Syntax.Expression>> defaults) {
    Declaration {
      typeParameters = List.copyOf(typeParameters);
      defaults = List.copyOf(defaults);
    }

    BoundCallableId factory(int parameter) {
      return new BoundCallableId(symbol.id().value() + "/argument/" + parameter);
    }
  }
}
