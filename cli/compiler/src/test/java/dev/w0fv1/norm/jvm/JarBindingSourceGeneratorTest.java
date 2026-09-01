package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

final class JarBindingSourceGeneratorTest {
  private static final Sha256Digest GRAPH_ID = Sha256Digest.parse("0123456789abcdef".repeat(4));

  @Test
  void generatesTypedNormFunctionsForSupportedStaticMethods() {
    JavaBindingCallable reverse =
        new JavaBindingCallable(
            "org.apache.commons.lang3.StringUtils",
            "reverse",
            "(Ljava/lang/String;)Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)),
            new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING));
    JavaBindingCallable length =
        new JavaBindingCallable(
            "org.apache.commons.lang3.StringUtils",
            "length",
            "(Ljava/lang/String;)I",
            JavaCallableKind.STATIC_METHOD,
            List.of(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)),
            JavaPrimitiveType.INT);
    JarApiSchema schema = schema("org.apache.commons.lang3.StringUtils", List.of(reverse, length));

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("commons.lang", 1), List.of("StringUtils"), GRAPH_ID, schema);

    GeneratedBindingSource source = generated.sources().getFirst();
    assertEquals("commons/lang/StringUtils.norm", source.relativePath());
    assertTrue(source.text().contains("package commons.lang"));
    assertTrue(source.text().contains("public String? stringUtilsReverse(String? arg0)"));
    assertTrue(source.text().contains("public Integer stringUtilsLength(String? arg0)"));
    assertTrue(source.text().contains("__jarInvoke1<String?>"));
    assertTrue(source.callIds().stream().allMatch(value -> value.startsWith("java-v13:")));
    assertEquals(2, generated.calls().size());
    assertEquals(reverse, generated.calls().get(source.callIds().getLast()));
  }

  @Test
  void generatesOnlyDeclaredMemberGroups() {
    String owner = "org.apache.commons.lang3.StringUtils";
    JavaReferenceType string = new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING);
    JavaBindingCallable reverse =
        new JavaBindingCallable(
            owner,
            "reverse",
            "(Ljava/lang/String;)Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(string),
            string);
    JavaBindingCallable length =
        new JavaBindingCallable(
            owner,
            "length",
            "(Ljava/lang/String;)I",
            JavaCallableKind.STATIC_METHOD,
            List.of(string),
            JavaPrimitiveType.INT);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("commons.lang", 1),
                List.of(new JarBindingType("StringUtils", List.of("reverse"))),
                GRAPH_ID,
                schema(owner, List.of(reverse, length)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("stringUtilsReverse"));
    assertFalse(source.text().contains("stringUtilsLength"));
  }

  @Test
  void rejectsUnknownDeclaredMemberGroups() {
    String owner = "org.apache.commons.lang3.StringUtils";
    JavaReferenceType string = new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING);
    JavaBindingCallable reverse =
        new JavaBindingCallable(
            owner,
            "reverse",
            "(Ljava/lang/String;)Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(string),
            string);

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new JarBindingSourceGenerator()
                    .generateSurface(
                        new ModuleCoordinate("commons.lang", 1),
                        List.of(new JarBindingType("StringUtils", List.of("missing"))),
                        GRAPH_ID,
                        schema(owner, List.of(reverse))));

    assertTrue(failure.getMessage().contains("StringUtils.missing"));
  }

  @Test
  void rejectsADeclaredMemberGroupWhenOneOverloadIsUnsupported() {
    String owner = "sample.Text";
    JavaReferenceType string = new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING);
    JavaBindingCallable supported =
        new JavaBindingCallable(
            owner,
            "value",
            "(Ljava/lang/String;)Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(string),
            string);
    String unsupportedDescriptor = "(Ljava/util/function/Supplier;)Ljava/lang/String;";
    JavaApiMethod unsupported =
        new JavaApiMethod(
            owner,
            "value",
            unsupportedDescriptor,
            new JavaGenericSignatureParser().parseMethod(unsupportedDescriptor),
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            JavaCallableKind.STATIC_METHOD,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            JavaApiDisposition.UNSUPPORTED,
            Optional.of(
                new JavaApiIssue(
                    JavaApiIssueCode.UNSUPPORTED_TYPE,
                    "java.util.function.Supplier is not mapped")),
            Optional.empty());
    JavaApiType type =
        new JavaApiType(
            owner,
            JavaApiTypeKind.CLASS,
            Opcodes.ACC_PUBLIC,
            new JavaClassSignature(
                List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of()),
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(apiMethod(supported), unsupported),
            JavaApiDisposition.BINDABLE);

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new JarBindingSourceGenerator()
                    .generateSurface(
                        new ModuleCoordinate("sample.binding", 1),
                        List.of(new JarBindingType("Text", List.of("value"))),
                        GRAPH_ID,
                        new JarApiSchema(List.of(type))));

    assertTrue(failure.getMessage().contains("Text.value"));
    assertTrue(failure.getMessage().contains("UNSUPPORTED_TYPE"));
  }

  @Test
  void exposesOnlyExplicitBindableOverloadsFromAMixedMemberGroup() {
    String owner = "sample.Text";
    JavaReferenceType string = new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING);
    JavaBindingCallable supported =
        new JavaBindingCallable(
            owner,
            "value",
            "(Ljava/lang/String;)Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(string),
            string);
    String unsupportedDescriptor = "(Ljava/lang/Appendable;)Ljava/lang/Appendable;";
    JavaApiMethod unsupported =
        new JavaApiMethod(
            owner,
            "value",
            unsupportedDescriptor,
            new JavaGenericSignatureParser().parseMethod(unsupportedDescriptor),
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            JavaCallableKind.STATIC_METHOD,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Optional.empty(),
            JavaApiDisposition.UNSUPPORTED,
            Optional.of(
                new JavaApiIssue(
                    JavaApiIssueCode.UNSUPPORTED_TYPE, "java.lang.Appendable is not mapped")),
            Optional.empty());
    JavaApiType type =
        new JavaApiType(
            owner,
            JavaApiTypeKind.CLASS,
            Opcodes.ACC_PUBLIC,
            new JavaClassSignature(
                List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of()),
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(apiMethod(supported), unsupported),
            JavaApiDisposition.BINDABLE);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("sample.binding", 1),
                List.of(
                    new JarBindingType(
                        "Text",
                        List.of(),
                        List.of(new JarBindingOverload("value", List.of("java.lang.String"))))),
                GRAPH_ID,
                new JarApiSchema(List.of(type)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("textValue(String? arg0)"));
    assertEquals(1, source.callIds().size());
  }

  @Test
  void rejectsAnUnknownExplicitOverload() {
    String owner = "sample.Text";
    JavaReferenceType string = new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING);
    JavaBindingCallable supported =
        new JavaBindingCallable(
            owner,
            "value",
            "(Ljava/lang/String;)Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(string),
            string);

    IllegalArgumentException failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new JarBindingSourceGenerator()
                    .generateSurface(
                        new ModuleCoordinate("sample.binding", 1),
                        List.of(
                            new JarBindingType(
                                "Text",
                                List.of(),
                                List.of(new JarBindingOverload("value", List.of("int"))))),
                        GRAPH_ID,
                        schema(owner, List.of(supported))));

    assertTrue(failure.getMessage().contains("Text.value(int)"));
  }

  @Test
  void projectsJavaCharSequenceAsNormString() {
    JavaReferenceType text =
        new JavaReferenceType("java.lang.CharSequence", JavaReferenceKind.CHAR_SEQUENCE);
    JavaBindingCallable echo =
        new JavaBindingCallable(
            "sample.Text",
            "echo",
            "(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;",
            JavaCallableKind.STATIC_METHOD,
            List.of(text),
            text);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Text"),
                GRAPH_ID,
                schema("sample.Text", List.of(echo)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("String? textEcho(String? arg0)"));
  }

  @Test
  void projectsJavaCharsetAsCanonicalNormString() {
    JavaReferenceType charset =
        new JavaReferenceType("java.nio.charset.Charset", JavaReferenceKind.CHARSET);
    JavaBindingCallable canonical =
        new JavaBindingCallable(
            "sample.Text",
            "canonical",
            "(Ljava/nio/charset/Charset;)Ljava/nio/charset/Charset;",
            JavaCallableKind.STATIC_METHOD,
            List.of(charset),
            charset);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Text"),
                GRAPH_ID,
                schema("sample.Text", List.of(canonical)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("String? textCanonical(String? arg0)"));
  }

  @Test
  void projectsJavaByteStreamsAsStandardNormStreams() {
    JavaReferenceType input =
        new JavaReferenceType("java.io.InputStream", JavaReferenceKind.INPUT_STREAM);
    JavaReferenceType output =
        new JavaReferenceType("java.io.OutputStream", JavaReferenceKind.OUTPUT_STREAM);
    JavaBindingCallable copy =
        new JavaBindingCallable(
            "sample.Streams",
            "copy",
            "(Ljava/io/InputStream;Ljava/io/OutputStream;)Ljava/io/InputStream;",
            JavaCallableKind.STATIC_METHOD,
            List.of(input, output),
            input);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Streams"),
                GRAPH_ID,
                schema("sample.Streams", List.of(copy)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("import std.io.InputStream"));
    assertTrue(source.text().contains("import std.io.OutputStream"));
    assertTrue(
        source.text().contains("InputStream? streamsCopy(InputStream? arg0, OutputStream? arg1)"));
  }

  @Test
  void projectsJavaFuturesAsStandardNormTasks() {
    JavaReferenceType string = new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING);
    JavaReferenceType task =
        new JavaReferenceType(
            "java.util.concurrent.CompletionStage",
            JavaReferenceKind.TASK,
            List.of(JavaBindingTypeArgument.exact(string)));
    JavaBindingCallable completed =
        new JavaBindingCallable(
            "sample.Tasks",
            "completed",
            "(Ljava/lang/String;)Ljava/util/concurrent/CompletionStage;",
            JavaCallableKind.STATIC_METHOD,
            List.of(string),
            task);
    JavaBindingCallable inspect =
        new JavaBindingCallable(
            "sample.Tasks",
            "inspect",
            "(Ljava/util/concurrent/Future;)Z",
            JavaCallableKind.STATIC_METHOD,
            List.of(task),
            JavaPrimitiveType.BOOLEAN);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Tasks"),
                GRAPH_ID,
                schema("sample.Tasks", List.of(completed, inspect)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("import std.concurrent.Task"));
    assertTrue(source.text().contains("Task<String?>? tasksCompleted(String? arg0)"));
    assertTrue(source.text().contains("Boolean tasksInspect(Task<String?>? arg0)"));
  }

  @Test
  void projectsJavaUrisAndUrlsAsStandardNormUris() {
    JavaReferenceType uri = new JavaReferenceType("java.net.URI", JavaReferenceKind.URI);
    JavaReferenceType url = new JavaReferenceType("java.net.URL", JavaReferenceKind.URI);
    JavaBindingCallable normalize =
        new JavaBindingCallable(
            "sample.Links",
            "normalize",
            "(Ljava/net/URI;)Ljava/net/URL;",
            JavaCallableKind.STATIC_METHOD,
            List.of(uri),
            url);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Links"),
                GRAPH_ID,
                schema("sample.Links", List.of(normalize)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("import std.http.Uri"));
    assertTrue(source.text().contains("Uri? linksNormalize(Uri? arg0)"));
  }

  @Test
  void projectsJavaDurationsAsStandardNormDurations() {
    JavaReferenceType duration =
        new JavaReferenceType("java.time.Duration", JavaReferenceKind.DURATION);
    JavaBindingCallable normalize =
        new JavaBindingCallable(
            "sample.Times",
            "normalize",
            "(Ljava/time/Duration;)Ljava/time/Duration;",
            JavaCallableKind.STATIC_METHOD,
            List.of(duration),
            duration);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Times"),
                GRAPH_ID,
                schema("sample.Times", List.of(normalize)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("import std.time.Duration"));
    assertTrue(source.text().contains("Duration? timesNormalize(Duration? arg0)"));
  }

  @Test
  void normalizesLeadingTypeAcronymsInGeneratedFunctionNames() {
    JavaReferenceType string = new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING);
    JavaBindingCallable value =
        new JavaBindingCallable(
            "sample.IOUtils",
            "value",
            "(Ljava/lang/String;)Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(string),
            string);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("IOUtils"),
                GRAPH_ID,
                schema("sample.IOUtils", List.of(value)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("ioUtilsValue"));
    assertFalse(source.text().contains("iOUtilsValue"));
  }

  @Test
  void projectsJavaCallbacksAsNormFunctionTypes() {
    JavaReferenceType string = new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING);
    JavaCallbackType supplier =
        new JavaCallbackType("java.util.function.Supplier", "get", List.of(), string);
    JavaCallbackType function =
        new JavaCallbackType("java.util.function.Function", "apply", List.of(string), string);
    JavaBindingCallable invoke =
        new JavaBindingCallable(
            "sample.Callbacks",
            "invoke",
            "(Ljava/util/function/Supplier;Ljava/util/function/Function;)Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(supplier, function),
            string);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Callbacks"),
                GRAPH_ID,
                schema("sample.Callbacks", List.of(invoke)))
            .sources()
            .getFirst();

    assertTrue(
        source
            .text()
            .contains(
                "String? callbacksInvoke(Function<String?()>? arg0, Function<String?(String?)>? arg1)"));
  }

  @Test
  void projectsJavaObjectAsNullableNormAny() {
    JavaReferenceType object = new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT);
    JavaBindingCallable identity =
        new JavaBindingCallable(
            "sample.Objects",
            "identity",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            JavaCallableKind.STATIC_METHOD,
            List.of(object),
            object);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Objects"),
                GRAPH_ID,
                schema("sample.Objects", List.of(identity)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("Any? objectsIdentity(Any? arg0)"));
  }

  @Test
  void projectsJavaClassTokensAsNormDeclarationReferences() {
    JavaReferenceType string = new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING);
    JavaReferenceType classOfString =
        new JavaReferenceType(
            "java.lang.Class",
            JavaReferenceKind.CLASS,
            List.of(JavaBindingTypeArgument.exact(string)));
    JavaBindingCallable identity =
        new JavaBindingCallable(
            "sample.Types",
            "identity",
            "(Ljava/lang/Class;)Ljava/lang/Class;",
            JavaCallableKind.STATIC_METHOD,
            List.of(classOfString),
            classOfString);

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Types"),
                GRAPH_ID,
                schema("sample.Types", List.of(identity)));

    GeneratedBindingSource source = generated.sources().getFirst();
    assertTrue(source.text().contains("Class<String>? typesIdentity(Class<String>? arg0)"));
    assertEquals(
        "Lsample/Types;",
        generated
            .classDescriptors()
            .get(
                new dev.w0fv1.norm.execution.JarBindingClassReference.Nominal(
                    new ModuleCoordinate("sample.binding", 1), "sample.binding", "Types")));
  }

  @Test
  void projectsJavaOptionalAsNormNullability() {
    JavaReferenceType optionalString =
        new JavaReferenceType(
            "java.util.Optional",
            JavaReferenceKind.OPTIONAL,
            List.of(
                JavaBindingTypeArgument.exact(
                    new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING))));
    JavaBindingCallable echo =
        new JavaBindingCallable(
            "sample.Optionals",
            "echo",
            "(Ljava/util/Optional;)Ljava/util/Optional;",
            JavaCallableKind.STATIC_METHOD,
            List.of(optionalString),
            optionalString);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Optionals"),
                GRAPH_ID,
                schema("sample.Optionals", List.of(echo)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("String? optionalsEcho(String? arg0)"));
  }

  @Test
  void projectsJavaListsAsNativeNormLists() {
    JavaReferenceType listOfString =
        new JavaReferenceType(
            "java.util.List",
            JavaReferenceKind.LIST,
            List.of(
                JavaBindingTypeArgument.exact(
                    new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING))));
    JavaBindingCallable echo =
        new JavaBindingCallable(
            "sample.Lists",
            "echo",
            "(Ljava/util/List;)Ljava/util/List;",
            JavaCallableKind.STATIC_METHOD,
            List.of(listOfString),
            listOfString);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Lists"),
                GRAPH_ID,
                schema("sample.Lists", List.of(echo)))
            .sources()
            .getFirst();

    assertTrue(
        source.text().contains("MutableList<String?>? listsEcho(MutableList<String?>? arg0)"));
    assertTrue(source.text().contains("import std.collections.MutableList"));
  }

  @Test
  void projectsJavaSetsAsSharedMutableSetReferences() {
    JavaReferenceType setOfString =
        new JavaReferenceType(
            "java.util.Set",
            JavaReferenceKind.SET,
            List.of(
                JavaBindingTypeArgument.exact(
                    new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING))));
    JavaBindingCallable echo =
        new JavaBindingCallable(
            "sample.Sets",
            "echo",
            "(Ljava/util/Set;)Ljava/util/Set;",
            JavaCallableKind.STATIC_METHOD,
            List.of(setOfString),
            setOfString);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Sets"),
                GRAPH_ID,
                schema("sample.Sets", List.of(echo)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("MutableSet<String?>? setsEcho(MutableSet<String?>? arg0)"));
    assertTrue(source.text().contains("import std.collections.MutableSet"));
  }

  @Test
  void projectsJavaMapsAsSharedMutableMapReferences() {
    JavaReferenceType map =
        new JavaReferenceType(
            "java.util.Map",
            JavaReferenceKind.MAP,
            List.of(
                JavaBindingTypeArgument.exact(
                    new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)),
                JavaBindingTypeArgument.exact(
                    new JavaBoxedType("java.lang.Integer", JavaPrimitiveType.INT))));
    JavaBindingCallable echo =
        new JavaBindingCallable(
            "sample.Maps",
            "echo",
            "(Ljava/util/Map;)Ljava/util/Map;",
            JavaCallableKind.STATIC_METHOD,
            List.of(map),
            map);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Maps"),
                GRAPH_ID,
                schema("sample.Maps", List.of(echo)))
            .sources()
            .getFirst();

    assertTrue(
        source
            .text()
            .contains(
                "MutableMap<String?, Integer?>? mapsEcho(MutableMap<String?, Integer?>? arg0)"));
    assertTrue(source.text().contains("import std.collections.MutableMap"));
  }

  @Test
  void projectsJavaCollectionsAsSharedMutableCollectionReferences() {
    JavaReferenceType collection =
        new JavaReferenceType(
            "java.util.Collection",
            JavaReferenceKind.COLLECTION,
            List.of(
                JavaBindingTypeArgument.exact(
                    new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING))));
    JavaBindingCallable echo =
        new JavaBindingCallable(
            "sample.Collections",
            "echo",
            "(Ljava/util/Collection;)Ljava/util/Collection;",
            JavaCallableKind.STATIC_METHOD,
            List.of(collection),
            collection);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Collections"),
                GRAPH_ID,
                schema("sample.Collections", List.of(echo)))
            .sources()
            .getFirst();

    assertTrue(
        source
            .text()
            .contains(
                "MutableCollection<String?>? collectionsEcho(MutableCollection<String?>? arg0)"));
    assertTrue(source.text().contains("import std.collections.MutableCollection"));
  }

  @Test
  void projectsJavaIterablesAsNormIterableViews() {
    JavaReferenceType iterable =
        new JavaReferenceType(
            "java.lang.Iterable",
            JavaReferenceKind.ITERABLE,
            List.of(
                JavaBindingTypeArgument.exact(
                    new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING))));
    JavaBindingCallable echo =
        new JavaBindingCallable(
            "sample.Iterables",
            "echo",
            "(Ljava/lang/Iterable;)Ljava/lang/Iterable;",
            JavaCallableKind.STATIC_METHOD,
            List.of(iterable),
            iterable);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Iterables"),
                GRAPH_ID,
                schema("sample.Iterables", List.of(echo)))
            .sources()
            .getFirst();

    assertTrue(
        source
            .text()
            .contains("IterableView<String?>? iterablesEcho(IterableView<String?>? arg0)"));
    assertTrue(source.text().contains("import std.collections.IterableView"));
  }

  @Test
  void projectsJavaIteratorsAsNormIteratorViews() {
    JavaReferenceType iterator =
        new JavaReferenceType(
            "java.util.Iterator",
            JavaReferenceKind.ITERATOR,
            List.of(
                JavaBindingTypeArgument.exact(
                    new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING))));
    JavaBindingCallable echo =
        new JavaBindingCallable(
            "sample.Iterators",
            "echo",
            "(Ljava/util/Iterator;)Ljava/util/Iterator;",
            JavaCallableKind.STATIC_METHOD,
            List.of(iterator),
            iterator);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Iterators"),
                GRAPH_ID,
                schema("sample.Iterators", List.of(echo)))
            .sources()
            .getFirst();

    assertTrue(
        source
            .text()
            .contains("IteratorView<String?>? iteratorsEcho(IteratorView<String?>? arg0)"));
    assertTrue(source.text().contains("import std.collections.IteratorView"));
  }

  @Test
  void generatesOpaqueClassFactoriesAndInstanceMethods() {
    String owner = "org.apache.commons.lang3.mutable.MutableInt";
    JavaBindingCallable constructor =
        new JavaBindingCallable(
            owner,
            "<init>",
            "(I)V",
            JavaCallableKind.CONSTRUCTOR,
            List.of(JavaPrimitiveType.INT),
            new JavaReferenceType(owner, JavaReferenceKind.OPAQUE));
    JavaBindingCallable intValue =
        new JavaBindingCallable(
            owner,
            "intValue",
            "()I",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            JavaPrimitiveType.INT);
    JavaBindingCallable increment =
        new JavaBindingCallable(
            owner,
            "increment",
            "()V",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            JavaPrimitiveType.VOID);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("commons.lang", 1),
                List.of("mutable.MutableInt"),
                GRAPH_ID,
                schema(owner, List.of(constructor, intValue, increment)))
            .sources()
            .getFirst();

    assertEquals("commons/lang/mutable/MutableInt.norm", source.relativePath());
    assertTrue(source.text().contains("private class MutableIntBindingToken"));
    assertTrue(source.text().contains("class MutableInt"));
    assertTrue(source.text().contains("Integer intValue()"));
    assertTrue(source.text().contains("__jarInvoke1<Integer>"));
    assertTrue(source.text().contains("Void increment()"));
    assertTrue(source.text().contains("__jarInvokeVoid1"));
    assertTrue(source.text().contains("MutableInt mutableIntNew(Integer arg0)"));
  }

  @Test
  void generatesNativeNormEnumsAndCallableBindings() {
    String owner = "sample.Level";
    JavaReferenceType level = new JavaReferenceType(owner, JavaReferenceKind.ENUM);
    JavaBindingCallable high =
        new JavaBindingCallable(
            owner, "HIGH", "Lsample/Level;", JavaCallableKind.STATIC_FIELD_GET, List.of(), level);
    JavaBindingCallable low =
        new JavaBindingCallable(
            owner, "LOW", "Lsample/Level;", JavaCallableKind.STATIC_FIELD_GET, List.of(), level);
    JavaBindingCallable defaultValue =
        new JavaBindingCallable(
            owner,
            "$DEFAULT",
            "Lsample/Level;",
            JavaCallableKind.STATIC_FIELD_GET,
            List.of(),
            level);
    JavaBindingCallable label =
        new JavaBindingCallable(
            owner,
            "label",
            "()Ljava/lang/String;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING));
    JavaBindingCallable echo =
        new JavaBindingCallable(
            owner,
            "echo",
            "(Lsample/Level;)Lsample/Level;",
            JavaCallableKind.STATIC_METHOD,
            List.of(level),
            level);
    JavaApiType type =
        new JavaApiType(
            owner,
            JavaApiTypeKind.ENUM,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ENUM,
            new JavaClassSignature(
                List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Enum")), List.of()),
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(
                enumField(owner, "HIGH", high),
                enumField(owner, "LOW", low),
                enumField(owner, "$DEFAULT", defaultValue)),
            List.of(apiMethod(label), apiMethod(echo)),
            JavaApiDisposition.BINDABLE);

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Level"),
                GRAPH_ID,
                new JarApiSchema(List.of(type)));

    GeneratedBindingSource source = generated.sources().getFirst();
    assertTrue(source.text().contains("enum Level {\n  _u0024_DEFAULT,\n  HIGH,\n  LOW\n}"));
    assertTrue(source.text().contains("public String? levelLabel(Level receiver)"));
    assertTrue(source.text().contains("public Level? levelEcho(Level? arg0)"));
    assertEquals(2, generated.calls().size());
    assertEquals(
        java.util.Map.of("_u0024_DEFAULT", "$DEFAULT", "HIGH", "HIGH", "LOW", "LOW"),
        generated.enumConstants().values().iterator().next());
  }

  @Test
  void generatesRootJarTypesRequiredByAnExportedApi() {
    String api = "sample.api.Tools";
    String model = "sample.model.Value";
    JavaReferenceType value = new JavaReferenceType(model, JavaReferenceKind.OPAQUE);
    JavaBindingCallable echo =
        new JavaBindingCallable(
            api,
            "echo",
            "(Lsample/model/Value;)Lsample/model/Value;",
            JavaCallableKind.STATIC_METHOD,
            List.of(value),
            value);
    JarApiSchema schema =
        new JarApiSchema(
            List.of(
                type(
                    api,
                    new JavaClassSignature(
                        List.of(),
                        Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                        List.of()),
                    List.of(),
                    List.of(echo)),
                type(
                    model,
                    new JavaClassSignature(
                        List.of(),
                        Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                        List.of()),
                    List.of(),
                    List.of())));

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1), List.of("api.Tools"), GRAPH_ID, schema);

    assertEquals(2, generated.sources().size());
    GeneratedBindingSource tools =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("api/Tools.norm"))
            .findFirst()
            .orElseThrow();
    assertTrue(tools.text().contains("import sample.binding.model.Value"));
    assertTrue(tools.text().contains("Value? toolsEcho(Value? arg0)"));
    assertTrue(
        generated.sources().stream()
            .anyMatch(source -> source.relativePath().endsWith("model/Value.norm")));
  }

  @Test
  void generatesStaticAndInstanceFieldAccessors() {
    String owner = "sample.MutableValue";
    JavaBindingCallable staticGetter =
        new JavaBindingCallable(
            owner,
            "DEFAULT_VALUE",
            "I",
            JavaCallableKind.STATIC_FIELD_GET,
            List.of(),
            JavaPrimitiveType.INT);
    JavaBindingCallable instanceGetter =
        new JavaBindingCallable(
            owner,
            "value",
            "I",
            JavaCallableKind.INSTANCE_FIELD_GET,
            List.of(),
            JavaPrimitiveType.INT);
    JavaBindingCallable instanceSetter =
        new JavaBindingCallable(
            owner,
            "value",
            "I",
            JavaCallableKind.INSTANCE_FIELD_SET,
            List.of(JavaPrimitiveType.INT),
            JavaPrimitiveType.VOID);
    JavaApiField staticField =
        apiField(
            owner,
            "DEFAULT_VALUE",
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
            List.of(staticGetter));
    JavaApiField instanceField =
        apiField(owner, "value", Opcodes.ACC_PUBLIC, List.of(instanceGetter, instanceSetter));

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("MutableValue"),
                GRAPH_ID,
                schema(owner, List.of(staticField, instanceField), List.of()))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("Integer fieldGetValue()"));
    assertTrue(source.text().contains("Void fieldSetValue(Integer arg0)"));
    assertTrue(source.text().contains("Integer mutableValueFieldGetDefaultValue()"));
  }

  @Test
  void generatesMutableJavaArrayReferenceSupport() {
    String owner = "sample.tools.TextTools";
    JavaArrayType strings =
        new JavaArrayType(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING));
    JavaBindingCallable normalize =
        new JavaBindingCallable(
            owner,
            "normalize",
            "([Ljava/lang/String;)[Ljava/lang/String;",
            JavaCallableKind.STATIC_METHOD,
            List.of(strings),
            strings);

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("tools.TextTools"),
                GRAPH_ID,
                schema(owner, List.of(normalize)));

    assertEquals(2, generated.sources().size());
    GeneratedBindingSource arrays =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("JavaArrays.norm"))
            .findFirst()
            .orElseThrow();
    assertTrue(arrays.text().contains("class JavaStringArray"));
    assertTrue(arrays.text().contains("Integer size()"));
    assertTrue(arrays.text().contains("String? get(Integer index)"));
    assertTrue(arrays.text().contains("Void set(Integer index, String? value)"));
    assertTrue(arrays.text().contains("JavaStringArray javaStringArrayNew(Integer size)"));
    GeneratedBindingSource tools =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("TextTools.norm"))
            .findFirst()
            .orElseThrow();
    assertTrue(tools.text().contains("JavaStringArray? textToolsNormalize(JavaStringArray? arg0)"));
    assertTrue(tools.text().contains("import sample.binding.JavaStringArray"));
    assertEquals(5, generated.calls().size());
  }

  @Test
  void generatesReifiedObjectArraySupportForGenericJavaArrays() {
    String owner = "sample.GenericTools";
    JavaBindingTypeVariable element =
        new JavaBindingTypeVariable(
            "T", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OPAQUE));
    JavaArrayType values = new JavaArrayType(element);
    JavaBindingCallable first =
        new JavaBindingCallable(
            owner,
            "first",
            "([Ljava/lang/Object;)Ljava/lang/Object;",
            JavaCallableKind.STATIC_METHOD,
            List.of(new JavaBindingTypeParameter("T", Optional.empty())),
            List.of(values),
            element);

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("GenericTools"),
                GRAPH_ID,
                schema(owner, List.of(first)));

    GeneratedBindingSource arrays =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("JavaArrays.norm"))
            .findFirst()
            .orElseThrow();
    assertTrue(arrays.text().contains("class JavaObjectArray<T>"));
    assertTrue(arrays.text().contains("T? get(Integer index)"));
    assertTrue(arrays.text().contains("Void set(Integer index, T? value)"));
    assertTrue(arrays.text().contains("JavaObjectArray<T> javaObjectArrayNew<T>(Integer size)"));
    GeneratedBindingSource tools =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("GenericTools.norm"))
            .findFirst()
            .orElseThrow();
    assertTrue(tools.text().contains("T? genericToolsFirst<T>(JavaObjectArray<T>? arg0)"));
  }

  @Test
  void generatesReifiedGenericFunctionsAndClasses() {
    String owner = "sample.Box";
    JavaBindingCallable identity =
        new JavaBindingCallable(
            owner,
            "identity",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(new JavaBindingTypeParameter("U", Optional.empty())),
            List.of(
                new JavaBindingTypeVariable(
                    "U", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OPAQUE))),
            new JavaBindingTypeVariable(
                "U", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OPAQUE)));
    JavaApiType type =
        type(
            owner,
            new JavaClassSignature(
                List.of(
                    new JavaTypeParameter(
                        "T",
                        Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                        List.of())),
                Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                List.of()),
            List.of(),
            List.of(identity));

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Box"),
                GRAPH_ID,
                new JarApiSchema(List.of(type)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("class Box<T>"));
    assertTrue(source.text().contains("U? identity<U>(U? arg0)"));
  }

  @Test
  void projectsMethodTypeParametersBoundByOwnerTypeParameters() {
    String owner = "sample.Box";
    JavaReferenceType object = new JavaReferenceType("java.lang.Object", JavaReferenceKind.OPAQUE);
    JavaBindingTypeVariable ownerValue = new JavaBindingTypeVariable("T", object);
    JavaBindingTypeVariable methodValue = new JavaBindingTypeVariable("U", object);
    JavaBindingCallable narrow =
        new JavaBindingCallable(
            owner,
            "narrow",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(new JavaBindingTypeParameter("U", Optional.of(ownerValue))),
            List.of(methodValue),
            methodValue);
    JavaApiType type =
        type(
            owner,
            new JavaClassSignature(
                List.of(
                    new JavaTypeParameter(
                        "T",
                        Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                        List.of())),
                Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                List.of()),
            List.of(),
            List.of(narrow));

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Box"),
                GRAPH_ID,
                new JarApiSchema(List.of(type)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("U? narrow<U extends T>(U? arg0)"));
  }

  @Test
  void projectsJavaComparableGenericBoundsToTheNormProtocol() {
    String owner = "sample.Ordering";
    JavaReferenceType comparableErasure =
        new JavaReferenceType("java.lang.Comparable", JavaReferenceKind.OPAQUE);
    JavaBindingTypeVariable element = new JavaBindingTypeVariable("T", comparableErasure);
    JavaReferenceType bound =
        new JavaReferenceType(
            "java.lang.Comparable",
            JavaReferenceKind.OPAQUE,
            List.of(JavaBindingTypeArgument.exact(element)));
    JavaBindingCallable maximum =
        new JavaBindingCallable(
            owner,
            "maximum",
            "([Ljava/lang/Comparable;)Ljava/lang/Comparable;",
            JavaCallableKind.STATIC_METHOD,
            List.of(new JavaBindingTypeParameter("T", Optional.of(bound))),
            List.of(new JavaArrayType(element)),
            element);

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Ordering"),
                GRAPH_ID,
                schema(owner, List.of(maximum)));

    GeneratedBindingSource ordering =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("Ordering.norm"))
            .findFirst()
            .orElseThrow();
    assertTrue(ordering.text().contains("import std.core.Comparable"));
    assertTrue(
        ordering
            .text()
            .contains("T? orderingMaximum<T extends Comparable<T>>(JavaComparableArray<T>? arg0)"));
    GeneratedBindingSource arrays =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("JavaArrays.norm"))
            .findFirst()
            .orElseThrow();
    assertTrue(arrays.text().contains("class JavaComparableArray<T extends Comparable<T>>"));
    assertTrue(
        arrays
            .text()
            .contains(
                "JavaComparableArray<T> javaComparableArrayNew<T extends Comparable<T>>(Integer size)"));
  }

  @Test
  void projectsJavaThrowableGenericBoundsToNormException() {
    String owner = "sample.Failures";
    JavaReferenceType throwable =
        new JavaReferenceType("java.lang.Throwable", JavaReferenceKind.OPAQUE);
    JavaBindingTypeVariable failure = new JavaBindingTypeVariable("E", throwable);
    JavaBindingCallable identity =
        new JavaBindingCallable(
            owner,
            "identity",
            "(Ljava/lang/Throwable;)Ljava/lang/Throwable;",
            JavaCallableKind.STATIC_METHOD,
            List.of(new JavaBindingTypeParameter("E", Optional.of(throwable))),
            List.of(failure),
            failure);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Failures"),
                GRAPH_ID,
                schema(owner, List.of(identity)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("import std.core.Exception"));
    assertTrue(source.text().contains("E? failuresIdentity<E extends Exception>(E? arg0)"));
  }

  @Test
  void mapsJavaThrowableValuesToNormExceptions() {
    String owner = "sample.Failures";
    JavaReferenceType throwable =
        new JavaReferenceType("java.lang.Throwable", JavaReferenceKind.EXCEPTION);
    JavaBindingCallable identity =
        new JavaBindingCallable(
            owner,
            "identity",
            "(Ljava/lang/Throwable;)Ljava/lang/Throwable;",
            JavaCallableKind.STATIC_METHOD,
            List.of(),
            List.of(throwable),
            throwable);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Failures"),
                GRAPH_ID,
                schema(owner, List.of(identity)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("import std.core.Exception"));
    assertTrue(source.text().contains("Exception? failuresIdentity(Exception? arg0)"));
  }

  @Test
  void projectsJavaCloseableTypesAsNormResources() {
    for (String resourceInterface : List.of("java.lang.AutoCloseable", "java.io.Closeable")) {
      String owner = "sample.Managed";
      JavaReferenceType managed = new JavaReferenceType(owner, JavaReferenceKind.OPAQUE);
      JavaBindingCallable constructor =
          new JavaBindingCallable(
              owner, "<init>", "()V", JavaCallableKind.CONSTRUCTOR, List.of(), managed);
      JavaBindingCallable close =
          new JavaBindingCallable(
              owner,
              "close",
              "()V",
              JavaCallableKind.INSTANCE_METHOD,
              List.of(),
              JavaPrimitiveType.VOID);
      JavaApiType type =
          type(
              owner,
              new JavaClassSignature(
                  List.of(),
                  Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                  List.of(JavaClassTypeSignature.raw(resourceInterface))),
              List.of(),
              List.of(constructor, close));

      GeneratedJarBinding generated =
          new JarBindingSourceGenerator()
              .generate(
                  new ModuleCoordinate("sample.binding", 1),
                  List.of("Managed"),
                  GRAPH_ID,
                  new JarApiSchema(List.of(type)));

      GeneratedBindingSource source = generated.sources().getFirst();
      assertTrue(source.text().contains("import std.io.Resource"));
      assertTrue(source.text().contains("class Managed implements Resource"));
      JavaReferenceType constructed =
          (JavaReferenceType)
              generated.calls().values().stream()
                  .filter(callable -> callable.kind() == JavaCallableKind.CONSTRUCTOR)
                  .findFirst()
                  .orElseThrow()
                  .returnType();
      assertEquals(JavaReferenceKind.RESOURCE, constructed.kind());
    }
  }

  @Test
  void projectsJavaInterfacesAndConcreteConformanceIntoTheNormTypeHierarchy() {
    String readableName = "sample.Readable";
    JavaApiType readable =
        new JavaApiType(
            readableName,
            JavaApiTypeKind.INTERFACE,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
            new JavaClassSignature(
                List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of()),
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            JavaApiDisposition.BINDABLE);
    String itemName = "sample.Item";
    JavaReferenceType itemType = new JavaReferenceType(itemName, JavaReferenceKind.OPAQUE);
    JavaBindingCallable constructor =
        new JavaBindingCallable(
            itemName, "<init>", "()V", JavaCallableKind.CONSTRUCTOR, List.of(), itemType);
    JavaApiType item =
        type(
            itemName,
            new JavaClassSignature(
                List.of(),
                Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                List.of(JavaClassTypeSignature.raw(readableName))),
            List.of(),
            List.of(constructor));

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("sample.binding", 1),
                List.of(new JarBindingType("Item", List.of("new"))),
                GRAPH_ID,
                new JarApiSchema(List.of(item, readable)));

    GeneratedBindingSource itemSource =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("/Item.norm"))
            .findFirst()
            .orElseThrow();
    GeneratedBindingSource readableSource =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("/Readable.norm"))
            .findFirst()
            .orElseThrow();
    assertTrue(itemSource.text().contains("class Item implements Readable"));
    assertTrue(readableSource.text().contains("interface Readable"));
    assertTrue(
        readableSource.text().contains("private class ReadableBindingValue implements Readable"));
    assertTrue(
        generated.classDescriptors().keySet().stream()
            .anyMatch(reference -> reference.name().equals("ReadableBindingValue")));
  }

  @Test
  void givesClosureTypesStableNamesThatDoNotCollideWithNormBuiltinTypes() {
    String functionName = "sample.Function";
    JavaApiType function =
        new JavaApiType(
            functionName,
            JavaApiTypeKind.INTERFACE,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
            new JavaClassSignature(
                List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of()),
            List.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            JavaApiDisposition.BINDABLE);
    JavaApiType box =
        type(
            "sample.Box",
            new JavaClassSignature(
                List.of(),
                Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                List.of(JavaClassTypeSignature.raw(functionName))),
            List.of(),
            List.of());

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("sample.binding", 1),
                List.of(new JarBindingType("Box", List.of())),
                GRAPH_ID,
                new JarApiSchema(List.of(box, function)));

    GeneratedBindingSource boxSource =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("/Box.norm"))
            .findFirst()
            .orElseThrow();
    GeneratedBindingSource functionSource =
        generated.sources().stream()
            .filter(source -> source.relativePath().endsWith("/JavaFunction.norm"))
            .findFirst()
            .orElseThrow();
    assertTrue(boxSource.text().contains("class Box implements JavaFunction"));
    assertTrue(functionSource.text().contains("interface JavaFunction"));
  }

  @Test
  void projectsRootJarCollectionsIntoTheNormIterableProtocol() {
    JavaClassTypeSignature iterable =
        new JavaClassTypeSignature(
            List.of(
                new JavaClassTypeSegment(
                    "java.lang.Iterable",
                    List.of(
                        JavaTypeArgument.of(
                            JavaTypeVariance.EXACT,
                            JavaClassTypeSignature.raw("java.lang.String"))))));
    JavaApiType values =
        type(
            "sample.Values",
            new JavaClassSignature(
                List.of(),
                Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                List.of(iterable)),
            List.of(),
            List.of());

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("sample.binding", 1),
                List.of(new JarBindingType("Values", List.of())),
                GRAPH_ID,
                new JarApiSchema(List.of(values)));

    GeneratedBindingSource source = generated.sources().getFirst();
    assertTrue(source.text().contains("import std.core.Iterable"));
    assertTrue(source.text().contains("import std.core.Iterator"));
    assertTrue(source.text().contains("class Values implements Iterable<String?>"));
    assertTrue(source.text().contains("public Iterator<String?> iterator()"));
    assertTrue(
        generated.calls().values().stream()
            .anyMatch(
                callable ->
                    callable.owner().equals("java.lang.Iterable")
                        && callable.name().equals("iterator")));
  }

  @Test
  void preservesUnboundedWildcardsOnPlatformIterableParameters() {
    String owner = "sample.Joiner";
    JavaReferenceType iterable =
        new JavaReferenceType(
            "java.lang.Iterable",
            JavaReferenceKind.ITERABLE,
            List.of(JavaBindingTypeArgument.unbounded()));
    JavaBindingCallable join =
        new JavaBindingCallable(
            owner,
            "join",
            "(Ljava/lang/Iterable;)Ljava/lang/String;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(iterable),
            new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING));

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("sample.binding", 1),
                List.of(new JarBindingType("Joiner", List.of("join"))),
                GRAPH_ID,
                schema(owner, List.of(join)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("join(IterableView<?>? arg0)"));
  }

  @Test
  void mapsJavaPathAndFileValuesToNormPath() {
    String owner = "sample.Paths";
    JavaReferenceType path = new JavaReferenceType("java.nio.file.Path", JavaReferenceKind.PATH);
    JavaReferenceType file = new JavaReferenceType("java.io.File", JavaReferenceKind.FILE);
    JavaBindingCallable pathIdentity =
        new JavaBindingCallable(
            owner,
            "identity",
            "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            JavaCallableKind.STATIC_METHOD,
            List.of(path),
            path);
    JavaBindingCallable fileIdentity =
        new JavaBindingCallable(
            owner,
            "identity",
            "(Ljava/io/File;)Ljava/io/File;",
            JavaCallableKind.STATIC_METHOD,
            List.of(file),
            file);
    JavaArrayType files = new JavaArrayType(file);
    JavaBindingCallable filesIdentity =
        new JavaBindingCallable(
            owner,
            "identity",
            "([Ljava/io/File;)[Ljava/io/File;",
            JavaCallableKind.STATIC_METHOD,
            List.of(files),
            files);

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Paths"),
                GRAPH_ID,
                schema(owner, List.of(pathIdentity, fileIdentity, filesIdentity)));
    GeneratedBindingSource source = generated.sources().getFirst();

    assertTrue(source.text().contains("import std.filesystem.Path"));
    assertTrue(source.text().contains("Path? pathsIdentityJavaPath(Path? arg0)"));
    assertTrue(source.text().contains("Path? pathsIdentityJavaFile(Path? arg0)"));
    assertTrue(generated.sources().getLast().text().contains("import std.filesystem.Path"));
  }

  @Test
  void sharesIdenticalInheritedCallsAcrossExportedClasses() {
    String parentName = "sample.Value";
    JavaBindingTypeVariable valueType =
        new JavaBindingTypeVariable(
            "T", new JavaReferenceType("java.lang.Object", JavaReferenceKind.OPAQUE));
    JavaBindingCallable get =
        new JavaBindingCallable(
            parentName,
            "get",
            "()Ljava/lang/Object;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            valueType);
    JavaApiType parent =
        type(
            parentName,
            new JavaClassSignature(
                List.of(
                    new JavaTypeParameter(
                        "T",
                        Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                        List.of())),
                Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                List.of()),
            List.of(),
            List.of(get));
    JavaClassTypeSignature stringValue =
        new JavaClassTypeSignature(
            List.of(
                new JavaClassTypeSegment(
                    parentName,
                    List.of(
                        JavaTypeArgument.of(
                            JavaTypeVariance.EXACT,
                            JavaClassTypeSignature.raw("java.lang.String"))))));
    JavaApiType first =
        type(
            "sample.FirstValue",
            new JavaClassSignature(List.of(), Optional.of(stringValue), List.of()),
            List.of(),
            List.of());
    JavaApiType second =
        type(
            "sample.SecondValue",
            new JavaClassSignature(List.of(), Optional.of(stringValue), List.of()),
            List.of(),
            List.of());

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("FirstValue", "SecondValue"),
                GRAPH_ID,
                new JarApiSchema(List.of(parent, first, second)));

    assertEquals(1, generated.calls().size());
    assertTrue(generated.sources().get(0).text().contains("String? get()"));
    assertTrue(generated.sources().get(1).text().contains("String? get()"));
    assertEquals(
        generated.sources().get(0).callIds().getFirst(),
        generated.sources().get(1).callIds().getFirst());
  }

  @Test
  void projectsRootJarSubclassesOfJavaListsWithTheirResolvedElementType() {
    String nodeName = "sample.Node";
    String nodesName = "sample.Nodes";
    String elementsName = "sample.Elements";
    JavaApiType node =
        type(
            nodeName,
            new JavaClassSignature(
                List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of()),
            List.of(),
            List.of());
    JavaApiType nodes =
        type(
            nodesName,
            new JavaClassSignature(
                List.of(
                    new JavaTypeParameter(
                        "T", Optional.of(JavaClassTypeSignature.raw(nodeName)), List.of())),
                Optional.of(
                    new JavaClassTypeSignature(
                        List.of(
                            new JavaClassTypeSegment(
                                "java.util.ArrayList",
                                List.of(
                                    JavaTypeArgument.of(
                                        JavaTypeVariance.EXACT,
                                        new JavaTypeVariableSignature("T"))))))),
                List.of()),
            List.of(),
            List.of());
    JavaApiType elements =
        type(
            elementsName,
            new JavaClassSignature(
                List.of(),
                Optional.of(
                    new JavaClassTypeSignature(
                        List.of(
                            new JavaClassTypeSegment(
                                nodesName,
                                List.of(
                                    JavaTypeArgument.of(
                                        JavaTypeVariance.EXACT,
                                        JavaClassTypeSignature.raw(nodeName))))))),
                List.of()),
            List.of(),
            List.of());

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("sample.binding", 1),
                List.of(new JarBindingType("Elements", List.of("get", "size"))),
                GRAPH_ID,
                new JarApiSchema(List.of(node, nodes, elements)));

    GeneratedBindingSource source = generated.sources().getFirst();
    assertTrue(source.text().contains("public Integer size()"));
    assertTrue(source.text().contains("public Node? get(Integer arg0)"));
    assertTrue(
        generated.calls().values().stream()
            .anyMatch(
                callable ->
                    callable.owner().equals("java.util.List") && callable.name().equals("get")));
  }

  @Test
  void rejectsAnExportThatDoesNotIdentifyOneRootJarClass() {
    JarApiSchema schema = new JarApiSchema(List.of());

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new JarBindingSourceGenerator()
                    .generate(
                        new ModuleCoordinate("commons.lang", 1),
                        List.of("StringUtils"),
                        GRAPH_ID,
                        schema));

    assertTrue(exception.getMessage().contains("StringUtils"));
  }

  @Test
  void prefersATopLevelTypeOverANestedTypeWithTheSameSimpleName() {
    JavaClassSignature signature =
        new JavaClassSignature(
            List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of());
    JarApiSchema schema =
        new JarApiSchema(
            List.of(
                type("sample.Request", signature, List.of(), List.of()),
                type("sample.Dns$Request", signature, List.of(), List.of())));

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("sample.binding", 1),
                List.of(new JarBindingType("Request", List.of())),
                GRAPH_ID,
                schema)
            .sources()
            .getFirst();

    assertEquals("sample/binding/Request.norm", source.relativePath());
  }

  @Test
  void prefixesNestedJavaTypesWithTheirEnclosingType() {
    String binaryName = "sample.Container$Nested";
    JavaBindingCallable value =
        new JavaBindingCallable(
            binaryName,
            "value",
            "()I",
            JavaCallableKind.STATIC_METHOD,
            List.of(),
            JavaPrimitiveType.INT);
    JavaApiType nested =
        new JavaApiType(
            binaryName,
            JavaApiTypeKind.CLASS,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            new JavaClassSignature(
                List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of()),
            List.of(),
            List.of(),
            Optional.of("sample.Container"),
            List.of(),
            List.of(),
            List.of(),
            List.of(apiMethod(value)),
            JavaApiDisposition.BINDABLE);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("sample.binding", 1),
                List.of(new JarBindingType("Container.Nested", List.of("value"))),
                GRAPH_ID,
                new JarApiSchema(List.of(nested)))
            .sources()
            .getFirst();

    assertEquals("sample/binding/ContainerNested.norm", source.relativePath());
    assertTrue(source.text().startsWith("package sample.binding\n"));
    assertTrue(source.text().contains("public Integer containerNestedValue()"));
  }

  private static JavaApiMethod apiMethod(JavaBindingCallable callable) {
    return new JavaApiMethod(
        callable.owner(),
        callable.name(),
        callable.descriptor(),
        new JavaGenericSignatureParser().parseMethod(callable.descriptor()),
        callable.kind() == JavaCallableKind.STATIC_METHOD
            ? Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
            : Opcodes.ACC_PUBLIC,
        callable.kind(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Optional.empty(),
        JavaApiDisposition.BINDABLE,
        Optional.empty(),
        Optional.of(callable));
  }

  private static JavaApiField apiField(
      String owner, String name, int modifiers, List<JavaBindingCallable> bindings) {
    return new JavaApiField(
        owner,
        name,
        "I",
        new JavaPrimitiveTypeSignature(JavaPrimitiveType.INT),
        modifiers,
        Optional.empty(),
        List.of(),
        List.of(),
        JavaApiDisposition.BINDABLE,
        Optional.empty(),
        bindings);
  }

  private static JavaApiField enumField(String owner, String name, JavaBindingCallable binding) {
    return new JavaApiField(
        owner,
        name,
        "L" + owner.replace('.', '/') + ";",
        JavaClassTypeSignature.raw(owner),
        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
        Optional.empty(),
        List.of(),
        List.of(),
        JavaApiDisposition.BINDABLE,
        Optional.empty(),
        List.of(binding));
  }

  private static JarApiSchema schema(String binaryName, List<JavaBindingCallable> callables) {
    return schema(binaryName, List.of(), callables);
  }

  private static JarApiSchema schema(
      String binaryName, List<JavaApiField> fields, List<JavaBindingCallable> callables) {
    return new JarApiSchema(
        List.of(
            type(
                binaryName,
                new JavaClassSignature(
                    List.of(),
                    Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                    List.of()),
                fields,
                callables)));
  }

  private static JavaApiType type(
      String binaryName,
      JavaClassSignature signature,
      List<JavaApiField> fields,
      List<JavaBindingCallable> callables) {
    return new JavaApiType(
        binaryName,
        JavaApiTypeKind.CLASS,
        Opcodes.ACC_PUBLIC,
        signature,
        List.of(),
        List.of(),
        Optional.empty(),
        List.of(),
        List.of(),
        fields,
        callables.stream().map(JarBindingSourceGeneratorTest::apiMethod).toList(),
        JavaApiDisposition.BINDABLE);
  }
}
