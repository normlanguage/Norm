@SuppressWarnings("module")
module dev.w0fv1.norm {
  requires com.fasterxml.jackson.core;
  requires com.fasterxml.jackson.dataformat.yaml;
  requires com.ctc.wstx;
  requires com.google.gson;
  requires java.net.http;
  requires java.xml;
  requires org.eclipse.lsp4j;
  requires org.eclipse.lsp4j.jsonrpc;
  requires org.graalvm.polyglot;
  requires org.graalvm.truffle;
  requires org.apache.maven.resolver;
  requires org.apache.maven.resolver.connector.basic;
  requires org.apache.maven.resolver.impl;
  requires org.apache.maven.resolver.named.locks;
  requires org.apache.maven.resolver.provider;
  requires org.apache.maven.resolver.spi;
  requires org.apache.maven.resolver.supplier;
  requires org.apache.maven.resolver.transport.apache;
  requires org.apache.maven.resolver.transport.file;
  requires org.apache.maven.resolver.util;
  requires org.apache.commons.codec;
  requires org.apache.commons.logging;
  requires org.apache.httpcomponents.httpclient;
  requires org.apache.httpcomponents.httpcore;
  requires org.codehaus.plexus.interpolation;
  requires org.codehaus.plexus.util;
  requires org.slf4j;
  requires org.objectweb.asm;

  uses javax.xml.stream.XMLInputFactory;
  uses javax.xml.stream.XMLOutputFactory;

  provides com.oracle.truffle.api.provider.TruffleLanguageProvider with
      dev.w0fv1.norm.truffle.LanguageProvider;

  opens dev.w0fv1.norm.cli.component to
      org.eclipse.lsp4j.jsonrpc;
}
