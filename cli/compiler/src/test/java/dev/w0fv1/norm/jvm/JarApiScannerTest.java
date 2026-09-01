package dev.w0fv1.norm.jvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.MavenArtifactCoordinate;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypeReference;

final class JarApiScannerTest {
  @TempDir Path temporaryDirectory;

  @Test
  void inventoriesEveryStablePublicDeclarationWithoutLoadingClasses() throws Exception {
    JarApiSchema schema = scan(libraryJar(temporaryDirectory.resolve("sample.jar"), "value"));
    JavaApiType tools = type(schema, "sample.Tools");

    assertEquals(JavaApiTypeKind.CLASS, tools.kind());
    assertEquals("T", tools.signature().typeParameters().getFirst().name());
    assertEquals("java.lang.Object", tools.signature().superclass().orElseThrow().binaryName());
    assertEquals("java.io.Serializable", tools.signature().interfaces().getFirst().binaryName());
    assertTrue(
        tools.annotations().stream().anyMatch(value -> value.type().equals("sample.Marker")));
    assertEquals(1, tools.typeAnnotations().size());
    assertEquals(
        "tools",
        ((JavaAnnotationConstantValue)
                tools.annotations().stream()
                    .filter(value -> value.type().equals("sample.Marker"))
                    .findFirst()
                    .orElseThrow()
                    .elements()
                    .getFirst()
                    .value())
            .value());
    assertTrue(
        tools.methods().stream()
            .anyMatch(
                value ->
                    value.kind() == JavaCallableKind.CONSTRUCTOR
                        && value.descriptor().equals("()V")
                        && value.disposition() == JavaApiDisposition.BINDABLE));
    assertTrue(
        tools.methods().stream()
            .anyMatch(
                value ->
                    value.kind() == JavaCallableKind.STATIC_METHOD
                        && value.name().equals("reverse")
                        && value.disposition() == JavaApiDisposition.BINDABLE));
    JavaApiMethod reverse =
        tools.methods().stream()
            .filter(value -> value.name().equals("reverse"))
            .findFirst()
            .orElseThrow();
    assertEquals("text", reverse.parameters().getFirst().name().orElseThrow());
    assertEquals(1, reverse.parameters().getFirst().annotations().size());
    JavaApiMethod generic =
        tools.methods().stream()
            .filter(value -> value.name().equals("generic"))
            .findFirst()
            .orElseThrow();
    assertEquals("E", generic.signature().typeParameters().getFirst().name());
    assertEquals("E", ((JavaTypeVariableSignature) generic.signature().returnType()).name());
    assertEquals(List.of("java.io.IOException"), generic.exceptions());
    assertEquals(JavaApiDisposition.BINDABLE, generic.disposition());
    JavaBindingCallable genericBinding = generic.binding().orElseThrow();
    assertEquals("E", genericBinding.typeParameters().getFirst().name());
    assertEquals("E", ((JavaBindingTypeVariable) genericBinding.returnType()).name());
    JavaApiMethod bounded =
        tools.methods().stream()
            .filter(value -> value.name().equals("bounded"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, bounded.disposition());
    JavaBindingTypeParameter boundedParameter =
        bounded.binding().orElseThrow().typeParameters().getFirst();
    assertEquals("E", boundedParameter.name());
    JavaReferenceType comparable = (JavaReferenceType) boundedParameter.bound().orElseThrow();
    assertEquals("java.lang.Comparable", comparable.binaryName());
    assertEquals(
        new JavaBindingTypeVariable(
            "E", new JavaReferenceType("java.lang.Comparable", JavaReferenceKind.OPAQUE)),
        comparable.arguments().getFirst().type().orElseThrow());
    JavaBindingTypeParameter throwableParameter =
        tools.methods().stream()
            .filter(value -> value.name().equals("throwable"))
            .findFirst()
            .orElseThrow()
            .binding()
            .orElseThrow()
            .typeParameters()
            .getFirst();
    assertEquals(
        new JavaReferenceType("java.lang.Throwable", JavaReferenceKind.OPAQUE),
        throwableParameter.bound().orElseThrow());
    JavaApiMethod failure =
        tools.methods().stream()
            .filter(value -> value.name().equals("failure"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, failure.disposition());
    assertEquals(
        new JavaReferenceType("java.lang.Throwable", JavaReferenceKind.EXCEPTION),
        failure.binding().orElseThrow().returnType());
    JavaApiMethod path =
        tools.methods().stream()
            .filter(value -> value.name().equals("path"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, path.disposition());
    assertEquals(
        new JavaReferenceType("java.nio.file.Path", JavaReferenceKind.PATH),
        path.binding().orElseThrow().returnType());
    JavaApiMethod inputStream =
        tools.methods().stream()
            .filter(value -> value.name().equals("inputStream"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, inputStream.disposition());
    assertEquals(
        new JavaReferenceType("java.io.InputStream", JavaReferenceKind.INPUT_STREAM),
        inputStream.binding().orElseThrow().returnType());
    JavaApiMethod outputStream =
        tools.methods().stream()
            .filter(value -> value.name().equals("outputStream"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, outputStream.disposition());
    assertEquals(
        new JavaReferenceType("java.io.OutputStream", JavaReferenceKind.OUTPUT_STREAM),
        outputStream.binding().orElseThrow().returnType());
    JavaApiMethod text =
        tools.methods().stream()
            .filter(value -> value.name().equals("text"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, text.disposition());
    assertEquals(
        new JavaReferenceType("java.lang.CharSequence", JavaReferenceKind.CHAR_SEQUENCE),
        text.binding().orElseThrow().returnType());
    JavaApiMethod charset =
        tools.methods().stream()
            .filter(value -> value.name().equals("charset"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, charset.disposition());
    assertEquals(
        new JavaReferenceType("java.nio.charset.Charset", JavaReferenceKind.CHARSET),
        charset.binding().orElseThrow().returnType());
    JavaApiMethod object =
        tools.methods().stream()
            .filter(value -> value.name().equals("object"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, object.disposition());
    assertEquals(
        new JavaReferenceType("java.lang.Object", JavaReferenceKind.OBJECT),
        object.binding().orElseThrow().returnType());
    JavaApiMethod classToken =
        tools.methods().stream()
            .filter(value -> value.name().equals("classToken"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, classToken.disposition());
    JavaReferenceType classType =
        (JavaReferenceType) classToken.binding().orElseThrow().returnType();
    assertEquals(JavaReferenceKind.CLASS, classType.kind());
    assertEquals(JavaTypeVariance.UNBOUNDED, classType.arguments().getFirst().variance());
    JavaApiMethod optionalClassToken =
        tools.methods().stream()
            .filter(value -> value.name().equals("optionalClassToken"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.UNSUPPORTED, optionalClassToken.disposition());
    assertEquals(JavaApiIssueCode.GENERIC_MAPPING, optionalClassToken.issue().orElseThrow().code());
    JavaApiMethod optional =
        tools.methods().stream()
            .filter(value -> value.name().equals("optional"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, optional.disposition());
    JavaReferenceType optionalType =
        (JavaReferenceType) optional.binding().orElseThrow().returnType();
    assertEquals(JavaReferenceKind.OPTIONAL, optionalType.kind());
    assertEquals(
        new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING),
        optionalType.arguments().getFirst().type().orElseThrow());
    JavaApiMethod supplier =
        tools.methods().stream()
            .filter(value -> value.name().equals("supplier"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, supplier.disposition());
    JavaCallbackType supplierType =
        (JavaCallbackType) supplier.binding().orElseThrow().parameters().getFirst();
    assertEquals("java.util.function.Supplier", supplierType.binaryName());
    assertEquals("get", supplierType.methodName());
    assertEquals(List.of(), supplierType.parameters());
    assertEquals(
        new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING),
        supplierType.returnType());
    JavaApiMethod function =
        tools.methods().stream()
            .filter(value -> value.name().equals("function"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, function.disposition());
    JavaCallbackType functionType =
        (JavaCallbackType) function.binding().orElseThrow().parameters().getFirst();
    assertEquals("apply", functionType.methodName());
    assertEquals(
        List.of(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)),
        functionType.parameters());
    assertEquals(
        new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING),
        functionType.returnType());
    JavaApiMethod list =
        tools.methods().stream()
            .filter(value -> value.name().equals("list"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, list.disposition());
    JavaReferenceType listType = (JavaReferenceType) list.binding().orElseThrow().returnType();
    assertEquals(JavaReferenceKind.LIST, listType.kind());
    assertEquals(
        new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING),
        listType.arguments().getFirst().type().orElseThrow());
    JavaApiMethod set =
        tools.methods().stream()
            .filter(value -> value.name().equals("set"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, set.disposition());
    assertEquals(
        JavaReferenceKind.SET,
        ((JavaReferenceType) set.binding().orElseThrow().returnType()).kind());
    JavaApiMethod map =
        tools.methods().stream()
            .filter(value -> value.name().equals("map"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, map.disposition());
    JavaReferenceType mapType = (JavaReferenceType) map.binding().orElseThrow().returnType();
    assertEquals(JavaReferenceKind.MAP, mapType.kind());
    assertEquals(2, mapType.arguments().size());
    JavaApiMethod collection =
        tools.methods().stream()
            .filter(value -> value.name().equals("collection"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, collection.disposition());
    assertEquals(
        JavaReferenceKind.COLLECTION,
        ((JavaReferenceType) collection.binding().orElseThrow().returnType()).kind());
    JavaApiMethod iterable =
        tools.methods().stream()
            .filter(value -> value.name().equals("iterable"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, iterable.disposition());
    assertEquals(
        JavaReferenceKind.ITERABLE,
        ((JavaReferenceType) iterable.binding().orElseThrow().returnType()).kind());
    JavaApiMethod iterator =
        tools.methods().stream()
            .filter(value -> value.name().equals("iterator"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, iterator.disposition());
    assertEquals(
        JavaReferenceKind.ITERATOR,
        ((JavaReferenceType) iterator.binding().orElseThrow().returnType()).kind());
    JavaApiMethod model =
        tools.methods().stream()
            .filter(value -> value.name().equals("model"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, model.disposition());
    assertEquals(
        new JavaReferenceType("sample.Model", JavaReferenceKind.OPAQUE),
        model.binding().orElseThrow().returnType());
    JavaApiMethod deprecated =
        tools.methods().stream()
            .filter(value -> value.name().equals("deprecatedMethod"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.EXCLUDED_DEPRECATED, deprecated.disposition());
    JavaApiField constant =
        tools.fields().stream()
            .filter(value -> value.name().equals("VALUE"))
            .findFirst()
            .orElseThrow();
    assertEquals("Ljava/lang/String;", constant.descriptor());
    assertEquals("value", constant.constantValue().orElseThrow());
    assertEquals(JavaApiDisposition.BINDABLE, constant.disposition());
    assertEquals(JavaCallableKind.STATIC_FIELD_GET, constant.bindings().getFirst().kind());
    assertEquals(1, constant.bindings().size());
    JavaApiField genericField =
        tools.fields().stream()
            .filter(value -> value.name().equals("item"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, genericField.disposition());
    assertEquals(
        "T", ((JavaBindingTypeVariable) genericField.bindings().getFirst().returnType()).name());
    JavaApiMethod instance =
        tools.methods().stream()
            .filter(value -> value.name().equals("instance"))
            .findFirst()
            .orElseThrow();
    assertEquals(JavaApiDisposition.BINDABLE, instance.disposition());
    JavaBindingCallable strings =
        tools.methods().stream()
            .filter(value -> value.name().equals("strings"))
            .findFirst()
            .orElseThrow()
            .binding()
            .orElseThrow();
    assertEquals(
        new JavaArrayType(new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING)),
        strings.parameters().getFirst());
    assertEquals(strings.parameters().getFirst(), strings.returnType());
    assertTrue(
        schema.types().stream()
            .anyMatch(value -> value.binaryName().equals("sample.Tools$Nested")));
    assertEquals("sample.Tools", type(schema, "sample.Tools$Nested").enclosingType().orElseThrow());
    assertTrue(
        schema.types().stream().noneMatch(value -> value.binaryName().equals("sample.Hidden")));
    assertTrue(
        schema.types().stream()
            .noneMatch(value -> value.binaryName().equals("sample.Hidden$PublicNested")));
    assertEquals(64, schema.apiId().value().length());
  }

  @Test
  void inventoriesAnnotationDefaultsRecordsAndSealedTypes() throws Exception {
    JarApiSchema schema = scan(libraryJar(temporaryDirectory.resolve("modern.jar"), "value"));

    JavaApiType marker = type(schema, "sample.Marker");
    assertEquals(JavaApiTypeKind.ANNOTATION, marker.kind());
    assertEquals(
        "default",
        ((JavaAnnotationConstantValue)
                marker.methods().getFirst().annotationDefault().orElseThrow())
            .value());
    JavaApiType model = type(schema, "sample.Model");
    assertEquals(JavaApiTypeKind.RECORD, model.kind());
    assertEquals("name", model.recordComponents().getFirst().name());
    assertEquals("Ljava/lang/String;", model.recordComponents().getFirst().descriptor());
    JavaApiType parent = type(schema, "sample.Parent");
    assertEquals(List.of("sample.Child"), parent.permittedSubclasses());
  }

  @Test
  void apiIdentityIncludesCompletePublicDeclarations() throws Exception {
    JarApiSchema first = scan(libraryJar(temporaryDirectory.resolve("first.jar"), "first"));
    JarApiSchema second = scan(libraryJar(temporaryDirectory.resolve("second.jar"), "second"));

    assertNotEquals(first.apiId(), second.apiId());
  }

  @Test
  void projectsJavaFutureTypesAsStandardNormTasks() throws Exception {
    JarApiSchema schema = scan(futureJar(temporaryDirectory.resolve("future.jar")));
    JavaApiType api = type(schema, "sample.FutureApi");

    JavaBindingCallable completed =
        api.methods().stream()
            .filter(method -> method.name().equals("completed"))
            .findFirst()
            .orElseThrow()
            .binding()
            .orElseThrow();
    JavaReferenceType task = (JavaReferenceType) completed.returnType();
    assertEquals(JavaReferenceKind.TASK, task.kind());
    assertEquals(
        new JavaReferenceType("java.lang.String", JavaReferenceKind.STRING),
        task.arguments().getFirst().type().orElseThrow());
    JavaBindingCallable inspect =
        api.methods().stream()
            .filter(method -> method.name().equals("inspect"))
            .findFirst()
            .orElseThrow()
            .binding()
            .orElseThrow();
    assertEquals(
        JavaReferenceKind.TASK, ((JavaReferenceType) inspect.parameters().getFirst()).kind());
    JavaReferenceType emptyTask =
        (JavaReferenceType)
            api.methods().stream()
                .filter(method -> method.name().equals("nothing"))
                .findFirst()
                .orElseThrow()
                .binding()
                .orElseThrow()
                .returnType();
    assertEquals(
        JavaReferenceKind.UNIT,
        ((JavaReferenceType) emptyTask.arguments().getFirst().type().orElseThrow()).kind());
  }

  @Test
  void scansApacheCommonsLangStringUtils() throws Exception {
    var coordinate = new MavenArtifactCoordinate("org.apache.commons", "commons-lang3", "3.20.0");
    try (JarResolver resolver = new JarResolver(temporaryDirectory.resolve("maven-cache"))) {
      ResolvedJarGraph graph =
          resolver.resolve(
              temporaryDirectory,
              new dev.w0fv1.norm.value.JarBinding(
                  new dev.w0fv1.norm.value.MavenJarTarget(coordinate, java.util.Optional.empty())));

      JarApiSchema schema = new JarApiScanner().scan(graph);

      assertTrue(
          type(schema, "org.apache.commons.lang3.StringUtils").methods().stream()
              .anyMatch(
                  value ->
                      value.kind() == JavaCallableKind.STATIC_METHOD
                          && value.name().equals("reverse")
                          && value.descriptor().equals("(Ljava/lang/String;)Ljava/lang/String;")));
    }
  }

  private static JarApiSchema scan(Path jar) throws IOException {
    ResolvedJarArtifact root =
        new ResolvedJarArtifact(
            new LocalJarIdentity(Sha256Digest.compute(jar)), jar, Sha256Digest.compute(jar));
    return new JarApiScanner().scan(new ResolvedJarGraph(root, List.of(root), List.of()));
  }

  @Test
  void preservesPublicInterfacesInheritedThroughPackagePrivateSuperclasses() throws Exception {
    JarApiSchema schema =
        scan(hiddenGenericSuperclassJar(temporaryDirectory.resolve("hierarchy.jar")));
    JavaApiType child = type(schema, "sample.Child");

    JavaClassTypeSignature relation = child.signature().interfaces().getFirst();
    assertEquals("sample.Multi", relation.binaryName());
    List<JavaTypeArgument> arguments = relation.segments().getFirst().arguments();
    assertEquals("K", ((JavaTypeVariableSignature) arguments.get(0).type().orElseThrow()).name());
    assertEquals("V", ((JavaTypeVariableSignature) arguments.get(1).type().orElseThrow()).name());
    assertTrue(child.methods().stream().noneMatch(method -> method.name().equals("get")));

    JavaApiMethod inherited =
        child.effectiveMethods().stream()
            .filter(method -> method.name().equals("get"))
            .findFirst()
            .orElseThrow();
    assertEquals(
        "K", ((JavaTypeVariableSignature) inherited.signature().parameters().getFirst()).name());
    assertEquals("V", ((JavaTypeVariableSignature) inherited.signature().returnType()).name());
    JavaBindingCallable binding = inherited.binding().orElseThrow();
    assertEquals("sample.Child", binding.owner());
    assertEquals("K", ((JavaBindingTypeVariable) binding.parameters().getFirst()).name());
    assertEquals("V", ((JavaBindingTypeVariable) binding.returnType()).name());
  }

  @Test
  void usesSyntheticBridgeMethodsToSuppressOverriddenGenericMethods() throws Exception {
    JarApiSchema schema = scan(genericBridgeJar(temporaryDirectory.resolve("bridge.jar")));
    JavaApiType concrete = type(schema, "sample.Concrete");

    List<JavaApiMethod> setters =
        concrete.effectiveMethods().stream()
            .filter(method -> method.name().equals("setValue"))
            .toList();

    assertEquals(1, setters.size());
    assertEquals("(Ljava/lang/String;)V", setters.getFirst().descriptor());
    JavaApiMethod getter =
        concrete.effectiveMethods().stream()
            .filter(method -> method.name().equals("getValue"))
            .findFirst()
            .orElseThrow();
    assertEquals("()Ljava/lang/String;", getter.descriptor());
  }

  private static JavaApiType type(JarApiSchema schema, String binaryName) {
    return schema.types().stream()
        .filter(value -> value.binaryName().equals(binaryName))
        .findFirst()
        .orElseThrow();
  }

  private static Path libraryJar(Path path, String constantValue) throws IOException {
    ClassWriter tools = new ClassWriter(0);
    tools.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/Tools",
        "<T:Ljava/lang/Object;>Ljava/lang/Object;Ljava/io/Serializable;",
        "java/lang/Object",
        new String[] {"java/io/Serializable"});
    AnnotationVisitor markerUse = tools.visitAnnotation("Lsample/Marker;", true);
    markerUse.visit("value", "tools");
    markerUse.visitEnd();
    tools
        .visitTypeAnnotation(
            TypeReference.newSuperTypeReference(-1).getValue(), null, "Lsample/Marker;", true)
        .visitEnd();
    tools.visitInnerClass(
        "sample/Tools$Nested", "sample/Tools", "Nested", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
    FieldVisitor field =
        tools.visitField(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
            "VALUE",
            "Ljava/lang/String;",
            null,
            constantValue);
    field.visitEnd();
    tools.visitField(Opcodes.ACC_PUBLIC, "item", "Ljava/lang/Object;", "TT;", null).visitEnd();
    MethodVisitor constructor = tools.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(1, 1);
    constructor.visitEnd();
    MethodVisitor reverse =
        tools.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "reverse",
            "(Ljava/lang/String;)Ljava/lang/String;",
            null,
            null);
    reverse.visitParameter("text", Opcodes.ACC_FINAL);
    AnnotationVisitor parameterMarker =
        reverse.visitParameterAnnotation(0, "Lsample/Marker;", true);
    parameterMarker.visit("value", "parameter");
    parameterMarker.visitEnd();
    reverse.visitEnd();
    tools
        .visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "instance", "()I", null, null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "generic",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            "<E:Ljava/lang/Object;>(TE;)TE;",
            new String[] {"java/io/IOException"})
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "path",
            "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            null,
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "supplier",
            "(Ljava/util/function/Supplier;)Ljava/lang/String;",
            "(Ljava/util/function/Supplier<Ljava/lang/String;>;)Ljava/lang/String;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "function",
            "(Ljava/util/function/Function;Ljava/lang/String;)Ljava/lang/String;",
            "(Ljava/util/function/Function<-Ljava/lang/String;+Ljava/lang/String;>;Ljava/lang/String;)Ljava/lang/String;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "inputStream",
            "(Ljava/io/InputStream;)Ljava/io/InputStream;",
            null,
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "outputStream",
            "(Ljava/io/OutputStream;)Ljava/io/OutputStream;",
            null,
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "iterator",
            "(Ljava/util/Iterator;)Ljava/util/Iterator;",
            "(Ljava/util/Iterator<Ljava/lang/String;>;)Ljava/util/Iterator<Ljava/lang/String;>;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "iterable",
            "(Ljava/lang/Iterable;)Ljava/lang/Iterable;",
            "(Ljava/lang/Iterable<Ljava/lang/String;>;)Ljava/lang/Iterable<Ljava/lang/String;>;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "collection",
            "(Ljava/util/Collection;)Ljava/util/Collection;",
            "(Ljava/util/Collection<Ljava/lang/String;>;)Ljava/util/Collection<Ljava/lang/String;>;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "map",
            "(Ljava/util/Map;)Ljava/util/Map;",
            "(Ljava/util/Map<Ljava/lang/String;Ljava/lang/Integer;>;)Ljava/util/Map<Ljava/lang/String;Ljava/lang/Integer;>;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "set",
            "(Ljava/util/Set;)Ljava/util/Set;",
            "(Ljava/util/Set<Ljava/lang/String;>;)Ljava/util/Set<Ljava/lang/String;>;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "optional",
            "(Ljava/util/Optional;)Ljava/util/Optional;",
            "(Ljava/util/Optional<Ljava/lang/String;>;)Ljava/util/Optional<Ljava/lang/String;>;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "list",
            "(Ljava/util/List;)Ljava/util/List;",
            "(Ljava/util/List<Ljava/lang/String;>;)Ljava/util/List<Ljava/lang/String;>;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "text",
            "(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;",
            null,
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "charset",
            "(Ljava/nio/charset/Charset;)Ljava/nio/charset/Charset;",
            null,
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "object",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            null,
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "classToken",
            "(Ljava/lang/Class;)Ljava/lang/Class;",
            "(Ljava/lang/Class<*>;)Ljava/lang/Class<*>;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "optionalClassToken",
            "(Ljava/lang/Class;)Ljava/lang/Class;",
            "(Ljava/lang/Class<Ljava/util/Optional<Ljava/lang/String;>;>;)Ljava/lang/Class<Ljava/util/Optional<Ljava/lang/String;>;>;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "model",
            "(Lsample/Model;)Lsample/Model;",
            null,
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "failure",
            "(Ljava/lang/Throwable;)Ljava/lang/Throwable;",
            null,
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "bounded",
            "(Ljava/lang/Comparable;)Ljava/lang/Comparable;",
            "<E::Ljava/lang/Comparable<-TE;>;>(TE;)TE;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "throwable",
            "(Ljava/lang/Throwable;)Ljava/lang/Throwable;",
            "<E:Ljava/lang/Throwable;>(TE;)TE;",
            null)
        .visitEnd();
    tools
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "strings",
            "([Ljava/lang/String;)[Ljava/lang/String;",
            null,
            null)
        .visitEnd();
    MethodVisitor deprecatedMethod =
        tools.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_DEPRECATED, "deprecatedMethod", "()V", null, null);
    AnnotationVisitor deprecated = deprecatedMethod.visitAnnotation("Ljava/lang/Deprecated;", true);
    deprecated.visitEnd();
    deprecatedMethod.visitEnd();
    tools.visitMethod(Opcodes.ACC_PRIVATE, "hidden", "()V", null, null).visitEnd();
    tools.visitEnd();

    ClassWriter nested = new ClassWriter(0);
    nested.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SUPER,
        "sample/Tools$Nested",
        null,
        "java/lang/Object",
        null);
    nested.visitInnerClass(
        "sample/Tools$Nested", "sample/Tools", "Nested", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
    nested.visitEnd();

    ClassWriter hidden = new ClassWriter(0);
    hidden.visit(Opcodes.V17, Opcodes.ACC_SUPER, "sample/Hidden", null, "java/lang/Object", null);
    hidden.visitInnerClass(
        "sample/Hidden$PublicNested",
        "sample/Hidden",
        "PublicNested",
        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
    hidden.visitEnd();

    ClassWriter hiddenNested = new ClassWriter(0);
    hiddenNested.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SUPER,
        "sample/Hidden$PublicNested",
        null,
        "java/lang/Object",
        null);
    hiddenNested.visitInnerClass(
        "sample/Hidden$PublicNested",
        "sample/Hidden",
        "PublicNested",
        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
    hiddenNested.visitEnd();

    ClassWriter marker = new ClassWriter(0);
    marker.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION,
        "sample/Marker",
        null,
        "java/lang/Object",
        new String[] {"java/lang/annotation/Annotation"});
    MethodVisitor annotationValue =
        marker.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "value", "()Ljava/lang/String;", null, null);
    annotationValue.visitAnnotationDefault().visit(null, "default");
    annotationValue.visitEnd();
    marker.visitEnd();

    ClassWriter model = new ClassWriter(0);
    model.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_RECORD,
        "sample/Model",
        null,
        "java/lang/Record",
        null);
    model.visitRecordComponent("name", "Ljava/lang/String;", null).visitEnd();
    model.visitEnd();

    ClassWriter parent = new ClassWriter(0);
    parent.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER,
        "sample/Parent",
        null,
        "java/lang/Object",
        null);
    parent.visitPermittedSubclass("sample/Child");
    parent.visitEnd();

    ClassWriter child = new ClassWriter(0);
    child.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
        "sample/Child",
        null,
        "sample/Parent",
        null);
    child.visitEnd();

    Files.createDirectories(path.getParent());
    try (var output = new JarOutputStream(Files.newOutputStream(path))) {
      writeClass(output, "sample/Tools.class", tools);
      writeClass(output, "sample/Tools$Nested.class", nested);
      writeClass(output, "sample/Hidden.class", hidden);
      writeClass(output, "sample/Hidden$PublicNested.class", hiddenNested);
      writeClass(output, "sample/Marker.class", marker);
      writeClass(output, "sample/Model.class", model);
      writeClass(output, "sample/Parent.class", parent);
      writeClass(output, "sample/Child.class", child);
    }
    return path;
  }

  private static Path futureJar(Path path) throws IOException {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/FutureApi",
        null,
        "java/lang/Object",
        null);
    writer
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "completed",
            "(Ljava/lang/String;)Ljava/util/concurrent/CompletionStage;",
            "(Ljava/lang/String;)Ljava/util/concurrent/CompletionStage<Ljava/lang/String;>;",
            null)
        .visitEnd();
    writer
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "nothing",
            "()Ljava/util/concurrent/CompletionStage;",
            "()Ljava/util/concurrent/CompletionStage<Ljava/lang/Void;>;",
            null)
        .visitEnd();
    writer
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE,
            "inspect",
            "(Ljava/util/concurrent/Future;)Z",
            "(Ljava/util/concurrent/Future<Ljava/lang/String;>;)Z",
            null)
        .visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      writeClass(output, "sample/FutureApi.class", writer);
    }
    return path;
  }

  private static Path hiddenGenericSuperclassJar(Path path) throws IOException {
    ClassWriter multimap = new ClassWriter(0);
    multimap.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
        "sample/Multi",
        "<K:Ljava/lang/Object;V:Ljava/lang/Object;>Ljava/lang/Object;",
        "java/lang/Object",
        null);
    multimap.visitEnd();

    ClassWriter base = new ClassWriter(0);
    base.visit(
        Opcodes.V17,
        Opcodes.ACC_ABSTRACT | Opcodes.ACC_SUPER,
        "sample/Base",
        "<K:Ljava/lang/Object;V:Ljava/lang/Object;>Ljava/lang/Object;Lsample/Multi<TK;TV;>;",
        "java/lang/Object",
        new String[] {"sample/Multi"});
    base.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE,
            "get",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            "(TK;)TV;",
            null)
        .visitEnd();
    base.visitEnd();

    ClassWriter child = new ClassWriter(0);
    child.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
        "sample/Child",
        "<K:Ljava/lang/Object;V:Ljava/lang/Object;>Lsample/Base<TK;TV;>;",
        "sample/Base",
        null);
    child.visitEnd();

    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      writeClass(output, "sample/Multi.class", multimap);
      writeClass(output, "sample/Base.class", base);
      writeClass(output, "sample/Child.class", child);
    }
    return path;
  }

  private static Path genericBridgeJar(Path path) throws IOException {
    ClassWriter mutable = new ClassWriter(0);
    mutable.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT,
        "sample/Mutable",
        "<T:Ljava/lang/Object;>Ljava/lang/Object;",
        "java/lang/Object",
        null);
    mutable
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "setValue",
            "(Ljava/lang/Object;)V",
            "(TT;)V",
            null)
        .visitEnd();
    mutable
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            "getValue",
            "()Ljava/lang/Object;",
            "()TT;",
            null)
        .visitEnd();
    mutable.visitEnd();

    ClassWriter concrete = new ClassWriter(0);
    concrete.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER,
        "sample/Concrete",
        "Ljava/lang/Object;Lsample/Mutable<Ljava/lang/String;>;",
        "java/lang/Object",
        new String[] {"sample/Mutable"});
    concrete
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE,
            "setValue",
            "(Ljava/lang/String;)V",
            null,
            null)
        .visitEnd();
    concrete
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_NATIVE,
            "setValue",
            "(Ljava/lang/Object;)V",
            null,
            null)
        .visitEnd();
    concrete
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE, "getValue", "()Ljava/lang/String;", null, null)
        .visitEnd();
    concrete
        .visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_NATIVE,
            "getValue",
            "()Ljava/lang/Object;",
            null,
            null)
        .visitEnd();
    concrete.visitEnd();

    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      writeClass(output, "sample/Mutable.class", mutable);
      writeClass(output, "sample/Concrete.class", concrete);
    }
    return path;
  }

  private static void writeClass(JarOutputStream output, String name, ClassWriter writer)
      throws IOException {
    output.putNextEntry(new JarEntry(name));
    output.write(writer.toByteArray());
    output.closeEntry();
  }
}
