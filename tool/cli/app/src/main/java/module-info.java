@SuppressWarnings("module")
module dev.w0fv1.norm.cli {
  requires dev.w0fv1.norm.core;
  requires org.eclipse.lsp4j;
  requires org.eclipse.lsp4j.jsonrpc;
  requires com.google.gson;

  exports dev.w0fv1.norm.cli;
  exports dev.w0fv1.norm.cli.value;
}
