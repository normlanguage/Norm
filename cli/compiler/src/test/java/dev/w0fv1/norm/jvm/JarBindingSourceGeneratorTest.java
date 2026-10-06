package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.JarBindingClassReference;
import dev.w0fv1.norm.value.JarBindingOverload;
import dev.w0fv1.norm.value.JarBindingType;
import dev.w0fv1.norm.value.ModuleCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.Opcodes;

final class JarBindingSourceGeneratorTest {
  private static final Sha256Digest GRAPH_ID = Sha256Digest.parse("0123456789abcdef".repeat(4));

  @Test
  void planningOwnsNamesAndCallRegistryBeforeRendering() {
    String owner = "sample.Numbers";
    JavaBindingCallable integer =
        new JavaBindingCallable(
            owner,
            "value",
            "(I)I",
            JavaCallableKind.STATIC_METHOD,
            List.of(JavaPrimitiveType.INT),
            JavaPrimitiveType.INT);
    JavaBindingCallable small =
        new JavaBindingCallable(
            owner,
            "value",
            "(S)I",
            JavaCallableKind.STATIC_METHOD,
            List.of(JavaPrimitiveType.SHORT),
            JavaPrimitiveType.INT);
    BindingPlan plan =
        new BindingPlanner()
            .plan(
                new ModuleCoordinate("numbers", 1),
                List.of("Numbers"),
                GRAPH_ID,
                schema(owner, List.of(integer, small)));
    BindingPlan.Declaration declaration = plan.declarations().getFirst();
    assertEquals(2, plan.calls().size());
    assertEquals(2, declaration.functions().size());
    assertEquals(
        2, declaration.functions().stream().map(BindingPlan.Call::name).distinct().count());
    assertTrue(
        declaration.functions().stream()
            .allMatch(call -> plan.calls().get(call.id()).equals(call.callable())));
    assertThrows(UnsupportedOperationException.class, () -> plan.calls().clear());
    GeneratedJarBinding first = new BindingSourceRenderer().render(plan);
    assertEquals(first, new BindingSourceRenderer().render(plan));
    assertEquals(plan.calls(), first.calls());
  }

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
    assertTrue(source.callIds().stream().allMatch(value -> value.startsWith("java-v19:")));
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
  void generatesAStableNormExportNameIndependentOfTheJavaTypeName() {
    String owner = "jakarta.persistence.EntityManager";
    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("orm", 1),
                List.of("Store"),
                List.of(new JarBindingType(owner, List.of())),
                GRAPH_ID,
                schema(owner, List.of()));

    assertEquals(List.of("Store"), generated.exports());
    assertEquals("orm/Store.norm", generated.sources().getFirst().relativePath());
    assertTrue(generated.sources().getFirst().text().contains("class Store"));
    assertEquals(
        "Ljakarta/persistence/EntityManager;",
        generated.classDescriptors().values().iterator().next());
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
            List.of(),
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
            List.of(),
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
                "String? callbacksInvoke(Function<String?()>? arg0, Function<String?(String?)>?"
                    + " arg1)"));
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
                new JarBindingClassReference.Nominal(
                    new ModuleCoordinate("sample.binding", 1), "sample.binding", "Types")));
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
    assertTrue(source.text().contains("MutableInt(__JarBindingToken token)"));
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
            List.of(),
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
  void generatesStrongNormAnnotationsFromJavaAnnotationContracts() {
    String owner = "sample.Endpoint";
    JavaBindingCallable path =
        new JavaBindingCallable(
            owner,
            "path",
            "()Ljava/lang/String;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING));
    JavaApiType endpoint =
        new JavaApiType(
            owner,
            JavaApiTypeKind.ANNOTATION,
            Opcodes.ACC_PUBLIC
                | Opcodes.ACC_INTERFACE
                | Opcodes.ACC_ABSTRACT
                | Opcodes.ACC_ANNOTATION,
            new JavaClassSignature(
                List.of(),
                Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                List.of(JavaClassTypeSignature.raw("java.lang.annotation.Annotation"))),
            List.of(
                new JavaApiAnnotation(
                    "java.lang.annotation.Target",
                    true,
                    List.of(
                        new JavaAnnotationElement(
                            "value",
                            new JavaAnnotationArrayValue(
                                List.of(
                                    new JavaAnnotationEnumValue(
                                        "java.lang.annotation.ElementType", "METHOD"),
                                    new JavaAnnotationEnumValue(
                                        "java.lang.annotation.ElementType", "TYPE")))))),
                new JavaApiAnnotation(
                    "java.lang.annotation.Retention",
                    true,
                    List.of(
                        new JavaAnnotationElement(
                            "value",
                            new JavaAnnotationEnumValue(
                                "java.lang.annotation.RetentionPolicy", "RUNTIME")))),
                new JavaApiAnnotation("java.lang.annotation.Inherited", true, List.of()),
                new JavaApiAnnotation(
                    "java.lang.annotation.Repeatable",
                    true,
                    List.of(
                        new JavaAnnotationElement(
                            "value", new JavaAnnotationClassValue("Lsample/Endpoints;"))))),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(apiMethod(path)),
            List.of(),
            JavaApiDisposition.BINDABLE);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Endpoint"),
                GRAPH_ID,
                new JarApiSchema(List.of(endpoint)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("import std.annotation.FunctionTarget"));
    assertTrue(source.text().contains("import std.annotation.RuntimeRetention"));
    assertTrue(source.text().contains("import std.annotation.TypeTarget"));
    assertTrue(source.text().contains("import std.annotation.InheritedAnnotation"));
    assertTrue(source.text().contains("import std.annotation.RepeatableAnnotation"));
    assertTrue(
        source
            .text()
            .contains(
                "public annotation Endpoint implements TypeTarget, FunctionTarget,"
                    + " RuntimeRetention, InheritedAnnotation, RepeatableAnnotation"));
    assertTrue(source.text().contains("String path"));
    assertTrue(source.callIds().isEmpty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"Throwable", "Void"})
  void preservesJavaAnnotationDefaultsWithOrdinaryNormConstructorSemantics(String className) {
    String exportedClassName = className.equals("Void") ? "JavaVoid" : className;
    String owner = "sample.Endpoint";
    JavaBindingCallable path =
        new JavaBindingCallable(
            owner,
            "path",
            "()Ljava/lang/String;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING));
    JavaBindingCallable as =
        new JavaBindingCallable(
            owner,
            "as",
            "()Ljava/lang/String;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING));
    JavaBindingCallable enabled =
        new JavaBindingCallable(
            owner,
            "enabled",
            "()Z",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            JavaPrimitiveType.BOOLEAN);
    JavaBindingCallable order =
        new JavaBindingCallable(
            owner,
            "order",
            "()I",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            JavaPrimitiveType.INT);
    JavaBindingCallable tags =
        new JavaBindingCallable(
            owner,
            "tags",
            "()[Ljava/lang/String;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            new JavaArrayType(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)));
    JavaBindingCallable level =
        new JavaBindingCallable(
            owner,
            "level",
            "()Lsample/Level;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            new JavaReferenceType("sample.Level", JavaReferenceKind.ENUM));
    JavaBindingCallable handler =
        new JavaBindingCallable(
            owner,
            "handler",
            "()Ljava/lang/Class;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            new JavaReferenceType(
                "java.lang.Class",
                JavaReferenceKind.CLASS,
                List.of(JavaBindingTypeArgument.unbounded())));
    JavaBindingCallable failure =
        new JavaBindingCallable(
            owner,
            "failure",
            "()Ljava/lang/Class;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            new JavaReferenceType(
                "java.lang.Class",
                JavaReferenceKind.CLASS,
                List.of(JavaBindingTypeArgument.unbounded())));
    JavaApiType levelType = enumType("sample.Level", "DEFAULT", "STRICT");
    JavaApiType handlerType =
        type(
            "sample.config.Handler",
            new JavaClassSignature(
                List.of(), Optional.of(JavaClassTypeSignature.raw("java.lang.Object")), List.of()),
            List.of(),
            List.of());
    JavaApiType endpoint =
        new JavaApiType(
            owner,
            JavaApiTypeKind.ANNOTATION,
            Opcodes.ACC_PUBLIC
                | Opcodes.ACC_INTERFACE
                | Opcodes.ACC_ABSTRACT
                | Opcodes.ACC_ANNOTATION,
            new JavaClassSignature(
                List.of(),
                Optional.of(JavaClassTypeSignature.raw("java.lang.Object")),
                List.of(JavaClassTypeSignature.raw("java.lang.annotation.Annotation"))),
            List.of(
                new JavaApiAnnotation(
                    "java.lang.annotation.Target",
                    true,
                    List.of(
                        new JavaAnnotationElement(
                            "value",
                            new JavaAnnotationArrayValue(
                                List.of(
                                    new JavaAnnotationEnumValue(
                                        "java.lang.annotation.ElementType", "TYPE"))))))),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            List.of(),
            List.of(
                annotationMethod(as, new JavaAnnotationConstantValue("self")),
                annotationMethod(enabled, new JavaAnnotationConstantValue(true)),
                annotationMethod(
                    failure, new JavaAnnotationClassValue("Ljava/lang/" + className + ";")),
                annotationMethod(order, new JavaAnnotationConstantValue(0)),
                annotationMethod(level, new JavaAnnotationEnumValue("sample.Level", "STRICT")),
                annotationMethod(handler, new JavaAnnotationClassValue("Lsample/config/Handler;")),
                apiMethod(path),
                annotationMethod(
                    tags,
                    new JavaAnnotationArrayValue(
                        List.of(
                            new JavaAnnotationConstantValue("http"),
                            new JavaAnnotationConstantValue("json"))))),
            List.of(),
            JavaApiDisposition.BINDABLE);

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generateSurface(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Endpoint", "config.Handler"),
                List.of(
                    new JarBindingType(
                        "Endpoint",
                        List.of(
                            "as", "enabled", "failure", "order", "level", "handler", "path",
                            "tags")),
                    new JarBindingType("config.Handler", List.of())),
                GRAPH_ID,
                new JarApiSchema(
                    List.of(endpoint, levelType, handlerType),
                    List.of(
                        type(
                            "java.lang." + className,
                            new JavaClassSignature(List.of(), Optional.empty(), List.of()),
                            List.of(),
                            List.of()))),
                java.util.Map.of(
                    "java.lang." + className,
                    new JarBindingClassReference.Nominal(
                        new ModuleCoordinate("java.base", 1), "java.base.lang", exportedClassName)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("Boolean enabled"));
    assertTrue(source.text().contains("String asValue"));
    assertTrue(source.text().contains("Integer order"));
    assertTrue(source.text().contains("Level level"));
    assertTrue(source.text().contains("Class<?> failure"));
    assertTrue(source.text().contains("import java.base.lang." + exportedClassName));
    assertTrue(source.text().contains("Class<?> handler"));
    assertTrue(source.text().contains("import sample.binding.config.Handler"));
    assertTrue(source.text().contains("String path"));
    assertTrue(source.text().contains("List<String> tags"));
    assertTrue(
        source
            .text()
            .contains(
                """
                  Endpoint(
                    String? asValue,
                    Boolean? enabled,
                    Class<?>? failure,
                    Class<?>? handler,
                    Level? level,
                    Integer? order,
                    String path,
                    List<String>? tags
                  ) {
                """));
    assertTrue(source.text().contains("this.asValue = asValue ?? \"self\""));
    assertTrue(source.text().contains("this.enabled = enabled ?? true"));
    assertTrue(source.text().contains("this.failure = failure ?? " + exportedClassName + ".class"));
    assertTrue(source.text().contains("this.handler = handler ?? Handler.class"));
    assertTrue(source.text().contains("this.order = order ?? 0"));
    assertTrue(source.text().contains("this.level = level ?? Level.STRICT"));
    assertTrue(source.text().contains("this.path = path"));
    assertTrue(source.text().contains("this.tags = tags ?? [\"http\", \"json\"]"));
  }

  @Test
  void generatesDependencyGraphTypesRequiredByAnExportedApi() {
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
                    List.of(echo))),
            List.of(
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
  void rendersRepresentableNominalGenericBounds() {
    String owner = "sample.Lifecycle";
    JavaApiType type =
        type(
            owner,
            new JavaGenericSignatureParser()
                .parseClass("<T::Lsample/Lifecycle<TT;>;>Ljava/lang/Object;"),
            List.of(),
            List.of());

    GeneratedBindingSource source =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("Lifecycle"),
                GRAPH_ID,
                new JarApiSchema(List.of(type)))
            .sources()
            .getFirst();

    assertTrue(source.text().contains("class Lifecycle<T extends Lifecycle<T>>"), source.text());
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
    JavaBindingCallable inherited =
        new JavaBindingCallable(
            parentName,
            "get",
            "()Ljava/lang/Object;",
            JavaCallableKind.INSTANCE_METHOD,
            List.of(),
            new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING));
    List<JavaApiType> children =
        java.util.stream.Stream.of("sample.FirstValue", "sample.SecondValue")
            .map(
                binaryName ->
                    new JavaApiType(
                        binaryName,
                        JavaApiTypeKind.CLASS,
                        Opcodes.ACC_PUBLIC,
                        new JavaClassSignature(List.of(), Optional.of(stringValue), List.of()),
                        List.of(),
                        List.of(),
                        Optional.empty(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(apiMethod(inherited)),
                        JavaApiDisposition.BINDABLE))
            .toList();

    GeneratedJarBinding generated =
        new JarBindingSourceGenerator()
            .generate(
                new ModuleCoordinate("sample.binding", 1),
                List.of("FirstValue", "SecondValue"),
                GRAPH_ID,
                new JarApiSchema(
                    java.util.stream.Stream.concat(
                            java.util.stream.Stream.of(parent), children.stream())
                        .toList()));

    assertEquals(1, generated.calls().size());
    assertTrue(generated.sources().get(0).text().contains("String? get()"));
    assertTrue(generated.sources().get(1).text().contains("String? get()"));
    assertEquals(
        generated.sources().get(0).callIds().getFirst(),
        generated.sources().get(1).callIds().getFirst());
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
            List.of(),
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

  private static JavaApiMethod annotationMethod(
      JavaBindingCallable callable, JavaAnnotationValue defaultValue) {
    return new JavaApiMethod(
        callable.owner(),
        callable.name(),
        callable.descriptor(),
        new JavaGenericSignatureParser().parseMethod(callable.descriptor()),
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
        callable.kind(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        Optional.of(defaultValue),
        JavaApiDisposition.BINDABLE,
        Optional.empty(),
        Optional.of(callable));
  }

  private static JavaApiType enumType(String owner, String... constants) {
    JavaReferenceType type = new JavaReferenceType(owner, JavaReferenceKind.ENUM);
    List<JavaApiField> fields =
        java.util.Arrays.stream(constants)
            .map(
                constant -> {
                  JavaBindingCallable binding =
                      new JavaBindingCallable(
                          owner,
                          constant,
                          "L" + owner.replace('.', '/') + ";",
                          JavaCallableKind.STATIC_FIELD_GET,
                          List.of(),
                          type);
                  return enumField(owner, constant, binding);
                })
            .toList();
    return new JavaApiType(
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
        fields,
        List.of(),
        List.of(),
        JavaApiDisposition.BINDABLE);
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
        List.of(),
        JavaApiDisposition.BINDABLE);
  }

  private static JavaApiType interfaceType(String binaryName, JavaClassSignature signature) {
    return new JavaApiType(
        binaryName,
        JavaApiTypeKind.INTERFACE,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
        signature,
        List.of(),
        List.of(),
        Optional.empty(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        JavaApiDisposition.BINDABLE);
  }
}
