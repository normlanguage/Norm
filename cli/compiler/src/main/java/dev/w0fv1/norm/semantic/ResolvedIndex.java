package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.abi.IntrinsicId;
import java.util.Optional;

public record ResolvedIndex(
    IndexKind kind,
    SemanticType keyType,
    SemanticType resultType,
    IntrinsicId readIntrinsic,
    Optional<IntrinsicId> writeIntrinsic) {}
