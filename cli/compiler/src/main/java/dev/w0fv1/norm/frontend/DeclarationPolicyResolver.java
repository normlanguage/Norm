package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.semantic.SemanticType;
import dev.w0fv1.norm.syntax.Syntax;
import dev.w0fv1.norm.value.AnnotationAbi;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DeclarationPolicyResolver {
  private final DeclarationCatalog catalog;
  private final TypeResolutionState resolution;
  private final TypeResolver typeResolver;
  private final DiagnosticBag diagnostics;

  DeclarationPolicyResolver(
      DeclarationCatalog catalog,
      TypeResolutionState resolution,
      TypeResolver typeResolver,
      DiagnosticBag diagnostics) {
    this.catalog = catalog;
    this.resolution = resolution;
    this.typeResolver = typeResolver;
    this.diagnostics = diagnostics;
  }

  private enum Policy {
    MANAGED_FIELD(AnnotationAbi.MANAGED_FIELD),
    MANAGED_IMPLEMENTATION(AnnotationAbi.MANAGED_IMPLEMENTATION);

    private final String identity;

    Policy(String name) {
      identity = AnnotationAbi.PACKAGE + "." + name;
    }
  }

  Optional<SemanticType> resultBuilder(
      Syntax.Parameter parameter, Map<String, SemanticType> declarationTypes) {

    try (var selectedProgram =
        resolution.enterProgram(catalog.ownerOr(parameter, resolution.program()))) {
      for (Syntax.AnnotationUse use : parameter.annotations()) {
        var annotation = typeResolver.resolveAnnotation(use.name());
        if (annotation == null
            || !typeResolver
                .aggregateSelfType(annotation)
                .identity()
                .equals(dev.w0fv1.norm.value.AnnotationAbi.BUILD_WITH)) continue;
        if (use.arguments().size() == 1
            && use.arguments().getFirst().value() instanceof Syntax.Member member
            && member.name().equals("class")
            && member.receiver() instanceof Syntax.Name name) {
          var callback = typeResolver.resolveType(parameter.type(), declarationTypes);
          if (!callback.isFunction() || !callback.functionParameterTypes().isEmpty()) {
            diagnostics.error(
                SemanticDiagnosticCodes.TYPE_MISMATCH,
                "BuildWith requires a zero-parameter function parameter",
                parameter.type().span());
          }
          return Optional.of(
              typeResolver.resolveType(
                  new Syntax.TypeRef(name.value(), name.typeArguments(), false, name.span()),
                  declarationTypes));
        }
        diagnostics.error(
            SemanticDiagnosticCodes.TYPE_MISMATCH,
            "BuildWith requires a builder class literal",
            use.span());
      }
      return Optional.empty();
    }
  }

  boolean constructorInput(Syntax.FieldDecl field) {
    return !managedField(field)
        && (field.visibility() == Syntax.Visibility.PUBLIC || field.defaultValue().isEmpty());
  }

  boolean managedField(Syntax.FieldDecl field) {
    return annotationPolicy(field, field.annotations(), Policy.MANAGED_FIELD);
  }

  boolean managedImplementation(Syntax.AggregateDecl owner) {
    return annotationPolicy(owner, owner.annotations(), Policy.MANAGED_IMPLEMENTATION);
  }

  private boolean annotationPolicy(
      Object declaration, List<Syntax.AnnotationUse> annotations, Policy policy) {

    try (var selectedProgram =
        resolution.enterProgram(catalog.ownerOr(declaration, resolution.program()))) {
      for (Syntax.AnnotationUse use : annotations) {
        Syntax.AggregateDecl annotation = typeResolver.resolveAnnotation(use.name());
        if (annotation != null
            && typeResolver.nominalViews(typeResolver.aggregateSelfType(annotation)).stream()
                .anyMatch(view -> view.identity().equals(policy.identity))) return true;
      }
      return false;
    }
  }
}
