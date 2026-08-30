package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.diagnostic.DiagnosticCode;

final class SemanticDiagnosticCodes {
  static final DiagnosticCode DUPLICATE_NAME = new DiagnosticCode("NORM-NAME-0001");
  static final DiagnosticCode MISSING_MAIN = new DiagnosticCode("NORM-NAME-0002");
  static final DiagnosticCode UNKNOWN_NAME = new DiagnosticCode("NORM-NAME-0003");
  static final DiagnosticCode TYPE_MISMATCH = new DiagnosticCode("NORM-TYPE-0001");
  static final DiagnosticCode INVALID_CALL = new DiagnosticCode("NORM-TYPE-0002");
  static final DiagnosticCode INVALID_CONTROL = new DiagnosticCode("NORM-FLOW-0001");
  static final DiagnosticCode NULLABILITY_MISMATCH = new DiagnosticCode("NORM-NULL-0001");
  static final DiagnosticCode UNTYPED_NULL = new DiagnosticCode("NORM-NULL-0002");
  static final DiagnosticCode INVALID_NULLABLE_TYPE = new DiagnosticCode("NORM-NULL-0003");
  static final DiagnosticCode UNSAFE_NULLABLE_ACCESS = new DiagnosticCode("NORM-NULL-0004");

  private SemanticDiagnosticCodes() {}
}
