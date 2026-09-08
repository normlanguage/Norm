package dev.w0fv1.norm.cli.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.w0fv1.norm.language.QueryPage;
import dev.w0fv1.norm.language.SemanticQuery;
import dev.w0fv1.norm.source.SourceLocation;
import java.util.function.Function;

public final class SemanticQueryWriter {
  public JsonObject rename(dev.w0fv1.norm.language.RenamePreview preview) {
    JsonObject result = new JsonObject();
    result.addProperty("writesFiles", false);
    result.addProperty("validationScope", "captured-compilation-request");
    JsonArray inputs = new JsonArray();
    preview
        .inputs()
        .forEach(
            input -> {
              JsonObject document = new JsonObject();
              document.addProperty("uri", input.document().uri().toString());
              document.addProperty("revision", input.content().value());
              inputs.add(document);
            });
    result.add("inputs", inputs);
    JsonArray changes = new JsonArray();
    preview
        .changes()
        .forEach(
            change -> {
              JsonObject document = new JsonObject();
              document.addProperty("uri", change.before().document().uri().toString());
              document.addProperty("beforeRevision", change.before().content().value());
              document.addProperty("afterRevision", change.after().content().value());
              JsonArray edits = new JsonArray();
              change
                  .edits()
                  .forEach(
                      edit -> {
                        JsonObject replacement = new JsonObject();
                        replacement.addProperty("startOffset", edit.location().startOffset());
                        replacement.addProperty("endOffset", edit.location().endOffset());
                        replacement.addProperty("oldText", edit.oldText());
                        replacement.addProperty("newText", edit.newText());
                        edits.add(replacement);
                      });
              document.add("edits", edits);
              changes.add(document);
            });
    result.add("changes", changes);
    return result;
  }

  public JsonObject symbols(QueryPage<SemanticQuery.Declaration> symbols) {
    return page(symbols, this::declaration);
  }

  public JsonObject context(SemanticQuery.Context context) {
    JsonObject result = new JsonObject();
    result.add("declaration", declaration(context.declaration()));
    context
        .source()
        .ifPresent(
            source -> {
              JsonObject value = location(source.location());
              value.addProperty("text", source.text());
              result.add("source", value);
            });
    result.add("dependencies", symbols(context.dependencies()));
    result.add("references", page(context.references(), SemanticQueryWriter::location));
    result.add("tests", symbols(context.tests()));
    return result;
  }

  private JsonObject declaration(SemanticQuery.Declaration declaration) {
    JsonObject result = new JsonObject();
    var symbol = declaration.symbol();
    result.addProperty("id", symbol.id().value());
    result.addProperty("name", symbol.name());
    result.addProperty("kind", symbol.kind().name().toLowerCase(java.util.Locale.ROOT));
    result.addProperty("signature", declaration.signature());
    result.add("type", type(symbol.type()));
    symbol.owner().ifPresent(owner -> result.addProperty("owner", owner.value()));
    if (!symbol.parameters().isEmpty()) {
      JsonArray parameters = new JsonArray();
      symbol
          .parameters()
          .forEach(
              parameter -> {
                JsonObject value = new JsonObject();
                value.addProperty("name", parameter.name());
                value.add("type", type(parameter.type()));
                value.addProperty("hasDefault", parameter.hasDefault());
                parameters.add(value);
              });
      result.add("parameters", parameters);
    }
    if (!symbol.typeParameters().isEmpty()) {
      JsonArray parameters = new JsonArray();
      symbol
          .typeParameters()
          .forEach(
              parameter -> {
                JsonObject value = new JsonObject();
                value.addProperty("name", parameter.name());
                value.add("type", type(parameter.type()));
                parameter.upperBound().ifPresent(bound -> value.add("upperBound", type(bound)));
                parameter
                    .defaultType()
                    .ifPresent(defaultType -> value.add("defaultType", type(defaultType)));
                parameters.add(value);
              });
      result.add("typeParameters", parameters);
    }
    declaration
        .revision()
        .ifPresent(revision -> result.addProperty("revision", revision.content().value()));
    result.addProperty("contextAvailable", declaration.revision().isPresent());
    result.add("location", location(symbol.declaration().orElseThrow()));
    if (!symbol.documentation().isBlank())
      result.addProperty("documentation", symbol.documentation());
    return result;
  }

  private static JsonObject location(SourceLocation location) {
    JsonObject result = new JsonObject();
    result.addProperty("uri", location.document().uri().toString());
    result.addProperty("startOffset", location.startOffset());
    result.addProperty("endOffset", location.endOffset());
    return result;
  }

  private static JsonObject type(dev.w0fv1.norm.semantic.SemanticType type) {
    JsonObject result = new JsonObject();
    result.addProperty("identity", type.identity());
    result.addProperty("displayName", type.displayName());
    return result;
  }

  private static <T> JsonObject page(QueryPage<T> page, Function<T, JsonObject> render) {
    JsonObject result = new JsonObject();
    result.addProperty("offset", page.offset());
    result.addProperty("total", page.total());
    result.addProperty("hasMore", page.hasMore());
    JsonArray items = new JsonArray();
    page.items().forEach(item -> items.add(render.apply(item)));
    result.add("items", items);
    return result;
  }
}
