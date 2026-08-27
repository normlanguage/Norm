package dev.w0fv1.norm.core;

import java.util.Set;

abstract class CoreWalker {
  final void walk(CoreDefinition definition) {
    switch (definition) {
      case CoreDefinition.Callable callable -> {
        callable.receiverType().ifPresent(this::walkType);
        callable.typeParameters().forEach(this::walkTypeParameter);
        callable.captureTypes().forEach(this::walkType);
        callable
            .parameters()
            .forEach(
                parameter -> {
                  walkType(parameter.type());
                  walkInterceptors(parameter.interceptors());
                });
        walkInterceptors(callable.interceptors());
        walkType(callable.returnType());
        Set<Integer> captureLocals = Set.copyOf(callable.captureLocals());
        callable.locals().stream()
            .filter(local -> !captureLocals.contains(local.index()))
            .forEach(local -> walkType(local.type()));
        walkBlock(callable.body());
      }
      case CoreDefinition.Aggregate declaration -> {
        declaration.typeParameters().forEach(this::walkTypeParameter);
        declaration.parentType().ifPresent(this::walkType);
        declaration
            .fields()
            .forEach(
                field -> {
                  walkType(field.type());
                  walkInterceptors(field.interceptors());
                });
        declaration
            .dispatch()
            .forEach(
                dispatch -> {
                  visitLink(dispatch.slot());
                  visitLink(dispatch.implementation());
                  walkType(dispatch.receiverType());
                });
        visitLink(declaration.constructor());
        declaration.conformances().forEach(this::walkConformance);
      }
      case CoreDefinition.Enum declaration -> {
        declaration.typeParameters().forEach(this::walkTypeParameter);
        declaration
            .variants()
            .forEach(
                variant ->
                    variant
                        .fields()
                        .forEach(
                            field -> {
                              walkType(field.type());
                              walkInterceptors(field.interceptors());
                            }));
      }
      case CoreDefinition.Interface declaration -> {
        declaration.typeParameters().forEach(this::walkTypeParameter);
        declaration.directParents().forEach(this::walkType);
        declaration.declaredMethods().forEach(this::visitLink);
      }
      case CoreDefinition.InterfaceMethod method -> {
        walkType(method.receiverInterfaceType());
        method.typeParameters().forEach(this::walkTypeParameter);
        method.parameterTypes().forEach(this::walkType);
        walkType(method.returnType());
      }
      case CoreDefinition.BuiltinConformance conformance -> {
        conformance.typeParameters().forEach(this::walkTypeParameter);
        walkType(conformance.concreteBuiltinType());
        walkType(conformance.interfaceType());
        conformance.witnesses().forEach(this::walkWitness);
      }
    }
  }

  private void walkInterceptors(java.util.List<CoreInterceptor> interceptors) {
    interceptors.forEach(
        interceptor -> {
          visitLink(interceptor.annotation());
          interceptor.values().forEach(value -> walkType(value.type()));
        });
  }

  protected void visitLink(CoreDefinitionLink link) {}

  protected void visitReference(int nodeIndex, CoreDefinitionLink link) {}

  private void walkType(CoreType type) {
    CoreTypes.links(type).forEach(this::visitLink);
  }

  private void walkTypeParameter(CoreTypeParameter parameter) {
    parameter.upperBound().ifPresent(this::walkType);
  }

  private void walkRuntimeType(CoreRuntimeType type) {
    walkType(type.template());
  }

  private void walkBlock(CoreBlock block) {
    block.statements().forEach(this::walkStatement);
  }

  private void walkStatement(CoreStatement statement) {
    switch (statement) {
      case CoreStatement.LocalDeclaration local -> walkExpression(local.initializer());
      case CoreStatement.LocalAssignment assignment -> walkExpression(assignment.value());
      case CoreStatement.FieldAssignment assignment -> {
        visitLink(assignment.field().owner());
        walkExpression(assignment.receiver());
        walkExpression(assignment.value());
      }
      case CoreStatement.IntrinsicAssignment assignment -> {
        walkExpression(assignment.receiver());
        assignment.index().ifPresent(this::walkExpression);
        walkExpression(assignment.value());
      }
      case CoreStatement.ReferenceAssignment assignment -> {
        walkExpression(assignment.reference());
        walkExpression(assignment.value());
      }
      case CoreStatement.ExpressionStatement expression -> walkExpression(expression.expression());
      case CoreStatement.IfStatement conditional -> {
        walkExpression(conditional.condition());
        walkBlock(conditional.thenBlock());
        walkBlock(conditional.elseBlock());
      }
      case CoreStatement.ConditionalForStatement loop -> {
        walkExpression(loop.condition());
        walkBlock(loop.body());
      }
      case CoreStatement.ForStatement loop -> {
        walkExpression(loop.iterable());
        walkIteration(loop.iteration());
        walkBlock(loop.body());
      }
      case CoreStatement.TryStatement tried -> {
        walkBlock(tried.body());
        for (CoreCatchClause clause : tried.catches()) {
          walkType(clause.type());
          walkBlock(clause.body());
        }
        tried.finallyBlock().ifPresent(this::walkBlock);
      }
      case CoreStatement.ThrowStatement thrown -> walkExpression(thrown.exception());
      case CoreStatement.ReturnStatement returned ->
          returned.value().ifPresent(this::walkExpression);
      case CoreStatement.YieldStatement yielded -> walkExpression(yielded.value());
      case CoreStatement.BreakStatement ignored -> {}
      case CoreStatement.ContinueStatement ignored -> {}
    }
  }

  private void walkExpression(CoreExpression expression) {
    walkType(expression.type());
    switch (expression) {
      case CoreExpression.Literal ignored -> {}
      case CoreExpression.NullLiteral ignored -> {}
      case CoreExpression.CollectionLiteral collection -> {
        collection.elements().forEach(this::walkExpression);
        walkRuntimeType(collection.runtimeType());
      }
      case CoreExpression.LocalRead ignored -> {}
      case CoreExpression.FieldRead field -> {
        visitLink(field.field().owner());
        walkExpression(field.receiver());
      }
      case CoreExpression.AddressLocal ignored -> {}
      case CoreExpression.AddressField field -> {
        visitLink(field.field().owner());
        walkExpression(field.receiver());
      }
      case CoreExpression.Dereference dereference -> walkExpression(dereference.reference());
      case CoreExpression.EnumConstruct construct -> {
        visitReference(construct.nodeIndex(), construct.target());
        visitLink(construct.target());
        walkRuntimeType(construct.runtimeType());
        construct.arguments().forEach(argument -> walkExpression(argument.value()));
      }
      case CoreExpression.Unary unary -> walkExpression(unary.operand());
      case CoreExpression.Binary binary -> {
        walkExpression(binary.left());
        walkExpression(binary.right());
      }
      case CoreExpression.Switch switched -> {
        walkExpression(switched.value());
        switched.cases().forEach(this::walkSwitchCase);
      }
      case CoreExpression.Index index -> {
        walkExpression(index.receiver());
        walkExpression(index.index());
      }
      case CoreExpression.CopyObject copied -> walkExpression(copied.receiver());
      case CoreExpression.Closure closure -> {
        visitReference(closure.nodeIndex(), closure.target());
        visitLink(closure.target());
        closure.receiver().ifPresent(this::walkExpression);
        closure.captures().forEach(this::walkExpression);
        closure.reifiedArguments().forEach(this::walkRuntimeType);
        closure.receiverTypeArguments().forEach(this::walkRuntimeType);
      }
      case CoreExpression.Invoke invoke -> {
        walkExpression(invoke.callee());
        invoke.arguments().forEach(argument -> walkExpression(argument.value()));
      }
      case CoreExpression.Call call -> {
        visitReference(call.nodeIndex(), call.target());
        visitLink(call.target());
        call.receiver().ifPresent(this::walkExpression);
        call.arguments().forEach(argument -> walkExpression(argument.value()));
        call.reifiedArguments().forEach(this::walkRuntimeType);
        call.receiverTypeArguments().forEach(this::walkRuntimeType);
      }
      case CoreExpression.InterfaceCall call -> {
        visitReference(call.nodeIndex(), call.requirement());
        visitLink(call.requirement());
        walkExpression(call.receiver());
        call.arguments().forEach(argument -> walkExpression(argument.value()));
        call.reifiedArguments().forEach(this::walkRuntimeType);
      }
      case CoreExpression.Construct construct -> {
        visitReference(construct.nodeIndex(), construct.target());
        visitLink(construct.target());
        visitLink(construct.initializer());
        walkRuntimeType(construct.runtimeType());
        construct.arguments().forEach(argument -> walkExpression(argument.value()));
      }
      case CoreExpression.Intrinsic intrinsic -> {
        intrinsic.receiver().ifPresent(this::walkExpression);
        intrinsic.arguments().forEach(argument -> walkExpression(argument.value()));
        intrinsic.runtimeType().ifPresent(this::walkRuntimeType);
      }
    }
  }

  private void walkSwitchCase(CoreSwitchCase switchCase) {
    walkPattern(switchCase.pattern());
    walkBlock(switchCase.body());
  }

  private void walkPattern(CorePattern pattern) {
    switch (pattern) {
      case CorePattern.Variant variant -> variant.arguments().forEach(this::walkPattern);
      case CorePattern.Binding binding -> walkType(binding.type());
      case CorePattern.Wildcard ignored -> {}
      case CorePattern.Literal literal -> walkType(literal.type());
      case CorePattern.Null ignored -> {}
    }
  }

  private void walkIteration(CoreIteration iteration) {
    switch (iteration) {
      case CoreIteration.Builtin ignored -> {}
      case CoreIteration.Interface protocol -> {
        visitLink(protocol.iteratorRequirement());
        visitLink(protocol.hasNextRequirement());
        visitLink(protocol.nextRequirement());
      }
    }
  }

  private void walkConformance(CoreConformance conformance) {
    walkType(conformance.interfaceType());
    conformance.witnesses().forEach(this::walkWitness);
  }

  private void walkWitness(CoreWitness witness) {
    visitLink(witness.requirement());
    if (witness.implementation() instanceof CoreWitnessTarget.Callable callable) {
      visitLink(callable.definition());
    }
  }
}
