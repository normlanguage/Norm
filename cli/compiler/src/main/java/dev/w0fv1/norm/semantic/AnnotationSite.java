package dev.w0fv1.norm.semantic;

import dev.w0fv1.norm.value.AnnotationTarget;
import dev.w0fv1.norm.value.DocumentId;
import dev.w0fv1.norm.value.ModuleCoordinate;
import java.util.Objects;

public sealed interface AnnotationSite permits AnnotationSite.Package, AnnotationSite.Symbol {
  AnnotationTarget kind();

  DocumentId document();

  record Package(ModuleCoordinate module, String packageName, DocumentId document)
      implements AnnotationSite {
    public Package {
      Objects.requireNonNull(module, "module");
      Objects.requireNonNull(packageName, "packageName");
      Objects.requireNonNull(document, "document");
    }

    @Override
    public AnnotationTarget kind() {
      return AnnotationTarget.PACKAGE;
    }
  }

  record Symbol(AnnotationTarget kind, SymbolId symbol, DocumentId document)
      implements AnnotationSite {
    public Symbol {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(symbol, "symbol");
      Objects.requireNonNull(document, "document");
      if (kind == AnnotationTarget.PACKAGE) {
        throw new IllegalArgumentException("package annotations require a package site");
      }
    }
  }
}
