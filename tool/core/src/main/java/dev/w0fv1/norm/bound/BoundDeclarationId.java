package dev.w0fv1.norm.bound;

public sealed interface BoundDeclarationId
    permits BoundCallableId,
        BoundAggregateId,
        BoundEnumId,
        BoundEnumVariantId,
        BoundInterfaceId,
        BoundInterfaceMethodId,
        BoundFieldId,
        BoundLocalId {
  String value();
}
