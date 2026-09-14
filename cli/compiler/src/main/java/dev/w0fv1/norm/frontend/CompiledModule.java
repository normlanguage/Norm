package dev.w0fv1.norm.frontend;

import dev.w0fv1.norm.bound.BoundLocalId;
import dev.w0fv1.norm.core.CoreArtifact;
import dev.w0fv1.norm.core.CoreBinding;
import dev.w0fv1.norm.core.CoreDefinitionOrigin;
import dev.w0fv1.norm.core.CoreDefinitionRole;
import dev.w0fv1.norm.core.CoreIdentityVersion;
import dev.w0fv1.norm.core.store.PortableObjectCodec;
import dev.w0fv1.norm.semantic.SemanticContribution;
import dev.w0fv1.norm.semantic.SymbolId;
import dev.w0fv1.norm.source.DocumentId;
import dev.w0fv1.norm.source.SourceFile;
import dev.w0fv1.norm.source.SourceSpan;
import dev.w0fv1.norm.syntax.TokenSpanMapping;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.ModuleSourceCoordinate;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

public final class CompiledModule {
  public static final String ABI = "norm-compiled-module-1";
  public static final String ENTRY = "core/module.bin";
  private final Payload payload;
  private final dev.w0fv1.norm.value.Sha256Digest contentId;
  private transient byte[] encoded;
  private transient Content loaded;
  final int nextSymbolOrdinal;

  private CompiledModule(Payload payload, byte[] encoded, Content loaded) {
    this.payload = payload;
    this.encoded = encoded;
    this.loaded = loaded;
    this.nextSymbolOrdinal = payload.nextSymbolOrdinal();
    this.contentId = dev.w0fv1.norm.value.Sha256Digest.compute(encoded);
  }

  private CompiledModule(
      ModuleCoordinate coordinate,
      Map<ModuleSourceCoordinate, SourceFile> sources,
      java.util.Set<ModuleSourceCoordinate> exports,
      java.util.Set<ModuleSourceCoordinate> bindings,
      Map<SourceSpan, SemanticContribution> contributions,
      Map<ModuleCoordinate, Map<SymbolId, DeclarationContract>> contracts,
      Map<CoreBuildHistory.Key, Definition> definitions,
      Map<String, Map<BoundLocalId, Integer>> locals,
      int nextSymbolOrdinal) {
    this.loaded =
        new Content(sources, exports, bindings, contributions, contracts, definitions, locals);
    this.nextSymbolOrdinal = nextSymbolOrdinal;
    try {
      var sourceIds =
          sources.entrySet().stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      Map.Entry::getKey,
                      entry ->
                          dev.w0fv1.norm.value.Sha256Digest.compute(
                              entry
                                  .getValue()
                                  .text()
                                  .getBytes(java.nio.charset.StandardCharsets.UTF_8))));
      this.payload =
          new Payload(
              ABI,
              CoreIdentityVersion.CURRENT,
              coordinate,
              nextSymbolOrdinal,
              sourceIds,
              PortableObjectCodec.encodeDeterministic(loaded));
      this.encoded = PortableObjectCodec.encodeDeterministic(payload);
      this.contentId = dev.w0fv1.norm.value.Sha256Digest.compute(encoded);
    } catch (IOException exception) {
      throw new CompilationInfrastructureException("cannot encode compiled module", exception);
    }
  }

  public ModuleCoordinate coordinate() {
    return payload.coordinate();
  }

  public synchronized byte[] encode() throws IOException {
    if (encoded == null) encoded = PortableObjectCodec.encodeDeterministic(payload);
    return encoded.clone();
  }

  dev.w0fv1.norm.value.Sha256Digest contentId() {
    return contentId;
  }

  void verifySources(Map<ModuleSourceCoordinate, SourceFile> sources) {
    payload
        .sources()
        .forEach(
            (coordinate, expected) -> {
              var source = sources.get(coordinate);
              if (source == null
                  || !expected.equals(
                      dev.w0fv1.norm.value.Sha256Digest.compute(
                          source.text().getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                throw new IllegalArgumentException(
                    "published Core source content does not match: " + coordinate);
            });
  }

  synchronized Content content() {
    if (loaded == null) {
      try {
        loaded = PortableObjectCodec.decodeDeterministic(payload.content(), Content.class);
      } catch (IOException exception) {
        throw new CompilationInfrastructureException(
            "cannot decode published Core: " + coordinate(), exception);
      }
    }
    return loaded;
  }

  public static CompiledModule decode(byte[] bytes) throws IOException {
    var payload = PortableObjectCodec.decodeDeterministic(bytes, Payload.class);
    if (!ABI.equals(payload.abi())
        || !CoreIdentityVersion.CURRENT.equals(payload.identityVersion()))
      throw new IOException("unsupported published Core ABI: " + payload.coordinate());
    return new CompiledModule(payload, bytes.clone(), null);
  }

  record Payload(
      String abi,
      CoreIdentityVersion identityVersion,
      ModuleCoordinate coordinate,
      int nextSymbolOrdinal,
      Map<ModuleSourceCoordinate, dev.w0fv1.norm.value.Sha256Digest> sources,
      byte[] content) {
    Payload {
      java.util.Objects.requireNonNull(abi, "abi");
      java.util.Objects.requireNonNull(identityVersion, "identityVersion");
      java.util.Objects.requireNonNull(coordinate, "coordinate");
      java.util.Objects.requireNonNull(content, "content");
      sources = Map.copyOf(sources);
      if (nextSymbolOrdinal < 0)
        throw new IllegalArgumentException("negative module symbol ordinal");
    }
  }

  record Content(
      Map<ModuleSourceCoordinate, SourceFile> sources,
      java.util.Set<ModuleSourceCoordinate> exportedSources,
      java.util.Set<ModuleSourceCoordinate> bindingSources,
      Map<SourceSpan, SemanticContribution> contributions,
      Map<ModuleCoordinate, Map<SymbolId, DeclarationContract>> contracts,
      Map<CoreBuildHistory.Key, Definition> definitions,
      Map<String, Map<BoundLocalId, Integer>> callableLocals) {
    Content {
      sources = Map.copyOf(sources);
      exportedSources = java.util.Set.copyOf(exportedSources);
      bindingSources = java.util.Set.copyOf(bindingSources);
      contributions = Map.copyOf(contributions);
      contracts =
          contracts.entrySet().stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
      definitions = Map.copyOf(definitions);
      callableLocals =
          callableLocals.entrySet().stream()
              .collect(
                  java.util.stream.Collectors.toUnmodifiableMap(
                      Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
    }
  }

  static CompiledModule capture(
      ModuleCoordinate module,
      CompilationSnapshot snapshot,
      CoreBuildHistory history,
      CoreArtifact artifact,
      java.util.Set<DocumentId> exports,
      java.util.Set<DocumentId> bindingsDocuments) {
    var scope = snapshot.semanticModel().compilationScope();
    if (!scope.modules().modules().contains(module))
      throw new IllegalArgumentException("compiled module is absent from compilation scope");
    var sources = new LinkedHashMap<ModuleSourceCoordinate, SourceFile>();
    var mappings = new LinkedHashMap<DocumentId, TokenSpanMapping>();
    var declarationDocuments = snapshot.declarations().sourceDocuments();
    scope
        .coordinates()
        .forEach(
            (document, coordinate) -> {
              if (!coordinate.module().equals(module) || !declarationDocuments.contains(document))
                return;
              var source = snapshot.document(document).orElseThrow().source();
              SourceFile canonical;
              try {
                canonical =
                    SourceFile.of(
                        new DocumentId(
                            new java.net.URI(
                                "norm-module",
                                null,
                                "/"
                                    + module.name()
                                    + "/"
                                    + module.version()
                                    + "/"
                                    + coordinate.relativePath(),
                                null)),
                        source.text());
              } catch (java.net.URISyntaxException exception) {
                throw new IllegalArgumentException("invalid module source coordinate", exception);
              }
              sources.put(coordinate, canonical);
              mappings.put(document, TokenSpanMapping.relocate(source, canonical));
            });
    var contributions = new LinkedHashMap<SourceSpan, SemanticContribution>();
    snapshot
        .history()
        .contributions(mappings.keySet())
        .forEach(
            (span, contribution) -> {
              var mapping = mappings.get(span.source().id());
              contributions.put(mapping.rebase(span), contribution.rebase(mapping));
            });
    var readable = new LinkedHashSet<>(scope.modules().dependencies().get(module));
    readable.add(module);
    var contracts = snapshot.declarations().moduleContracts(scope, readable);
    var keys =
        new LinkedHashMap<dev.w0fv1.norm.core.DefinitionOccurrenceId, CoreBuildHistory.Key>();
    history.occurrences().forEach((key, occurrence) -> keys.put(occurrence, key));
    var bindings = new LinkedHashMap<dev.w0fv1.norm.core.DefinitionOccurrenceId, CoreBinding>();
    artifact.namespace().bindings().forEach(binding -> bindings.put(binding.occurrence(), binding));
    var definitions = new LinkedHashMap<CoreBuildHistory.Key, Definition>();
    var locals = new LinkedHashMap<String, Map<BoundLocalId, Integer>>();
    for (var occurrence : artifact.authoring().occurrences()) {
      var mapping = mappings.get(occurrence.origin().rootSpan().source().id());
      if (mapping == null) continue;
      var key = keys.get(occurrence.id());
      var references = new LinkedHashMap<Integer, CoreBuildHistory.Key>();
      occurrence.references().forEach((node, target) -> references.put(node, keys.get(target)));
      var nodes = new LinkedHashMap<Integer, SourceSpan>();
      occurrence
          .origin()
          .nodeSpans()
          .forEach((node, span) -> nodes.put(node, mapping.rebase(span)));
      var origin =
          new CoreDefinitionOrigin(
              occurrence.origin().definitionName(),
              mapping.rebase(occurrence.origin().rootSpan()),
              nodes);
      definitions.put(
          key,
          new Definition(
              history.units().get(key),
              origin,
              references,
              Optional.ofNullable(bindings.get(occurrence.id())),
              occurrence.role()));
      if (key instanceof CoreBuildHistory.Key.Named named
          && history.callableLocals().containsKey(named.identity()))
        locals.put(named.identity(), history.callableLocals().get(named.identity()));
    }
    var exportedSources =
        exports.stream()
            .filter(mappings::containsKey)
            .map(scope::coordinate)
            .collect(java.util.stream.Collectors.toSet());
    var bindingSources =
        bindingsDocuments.stream()
            .filter(mappings::containsKey)
            .map(scope::coordinate)
            .collect(java.util.stream.Collectors.toSet());
    return new CompiledModule(
        module,
        sources,
        exportedSources,
        bindingSources,
        contributions,
        contracts,
        definitions,
        locals,
        snapshot.history().nextSymbolOrdinal());
  }

  record Definition(
      CoreBuildHistory.Unit unit,
      CoreDefinitionOrigin origin,
      Map<Integer, CoreBuildHistory.Key> references,
      Optional<CoreBinding> binding,
      CoreDefinitionRole role) {
    Definition {
      java.util.Objects.requireNonNull(unit, "unit");
      java.util.Objects.requireNonNull(origin, "origin");
      references = Map.copyOf(references);
      java.util.Objects.requireNonNull(binding, "binding");
      java.util.Objects.requireNonNull(role, "role");
    }
  }
}
