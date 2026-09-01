package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import dev.w0fv1.norm.value.Sha256Digest;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class JarBindingRuntimeIntegrationTest {
  private static final String RESOURCE_CLOSE_PROPERTY = "norm.test.java.resource.close-count";
  @TempDir Path temporaryDirectory;

  @Test
  void catchesAJavaInvocationFailureAsANormException() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("sample/binding"));
    Path jar = failureJar(moduleRoot.resolve("lib/failure.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "sample.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/failure.jar",
                integrity: sha256("%s")
              ),
              api: [jarType(name: "FailureApi", members: ["fail", "identity"])]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package sample.binding
        import std.core.Exception

        Void main() {
          try {
            failureApiFail("boom")
          } catch Exception failure {
            printLine(failure.message)
            Exception returned = failureApiIdentity(failure) ?? Exception(message: "missing")
            printLine(returned.message)
          }
          Exception local = Exception(message: "local")
          Exception localReturned =
            failureApiIdentity(local) ?? Exception(message: "missing")
          printLine(localReturned.message)
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("failure-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        "boom"
            + System.lineSeparator()
            + "boom"
            + System.lineSeparator()
            + "local"
            + System.lineSeparator(),
        output.toString());
  }

  @Test
  void closesJavaResourcesExplicitlyAndAtExecutionScopeExit() throws Exception {
    System.clearProperty(RESOURCE_CLOSE_PROPERTY);
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("resource/binding"));
    Path jar = resourceJar(moduleRoot.resolve("lib/resource.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "resource.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/resource.jar",
                integrity: sha256("%s")
              ),
              api: [jarType(name: "Managed", members: ["new", "close", "closeCount", "closed"])]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package resource.binding
        import std.io.use

        Void main() {
          Managed explicit = managedNew()
          printLine(managedCloseCount())
          Integer used = use<Integer>(resource: explicit, body: () {
            printLine("body")
            return 1
          })
          printLine(managedCloseCount())
          Managed automatic = managedNew()
          printLine(automatic.closed())
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("resource-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        "0"
            + System.lineSeparator()
            + "body"
            + System.lineSeparator()
            + "1"
            + System.lineSeparator()
            + "false"
            + System.lineSeparator(),
        output.toString());
    assertEquals("2", System.getProperty(RESOURCE_CLOSE_PROPERTY));
  }

  @Test
  void readsAndWritesJavaByteStreamsThroughStandardNormIo() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("stream/binding"));
    Path jar = streamJar(moduleRoot.resolve("lib/stream.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "stream.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/stream.jar",
                integrity: sha256("%s")
              ),
              api: [jarType(name: "StreamApi", members: ["input", "output", "outputText"])]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package stream.binding
        import std.io.Bytes
        import std.io.InputStream
        import std.io.OutputStream
        import std.io.TextEncoding
        import std.io.decodeText
        import std.io.encodeText
        import std.io.readAll
        import std.io.writeAll

        Void main() {
          InputStream? input = streamApiInput("Norm")
          if input != null {
            Bytes content = readAll(reader: input, maximumBytes: 16)
            printLine(decodeText(content: content, encoding: TextEncoding.Utf8))
            input.close()
          }
          OutputStream? output = streamApiOutput()
          if output != null {
            Bytes content = encodeText(text: "NAR", encoding: TextEncoding.Utf8)
            writeAll(writer: output, content: content)
            output.flush()
            printLine(streamApiOutputText(output) ?? "missing")
            output.close()
          }
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("stream-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        "Norm" + System.lineSeparator() + "NAR" + System.lineSeparator(), output.toString());
  }

  @Test
  void convertsNormPathsForJavaPathAndFileApis() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("path/binding"));
    Path jar = pathJar(moduleRoot.resolve("lib/path.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "path.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/path.jar",
                integrity: sha256("%s")
              ),
              api: [jarType(name: "PathApi", members: ["file", "path", "uri", "url"])]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package path.binding
        import std.filesystem.Path
        import std.http.Uri

        Void main() {
          Path path = pathApiPath(Path(value: "entry.nar")) ?? Path(value: "missing")
          Path file = pathApiFile(Path(value: "module.norm")) ?? Path(value: "missing")
          printLine(path.value)
          printLine(file.value)
          Uri uri = pathApiUri(Uri(value: "https://example.com/a")) ?? Uri(value: "missing")
          Uri url = pathApiUrl(Uri(value: "https://example.com/b")) ?? Uri(value: "missing")
          printLine(uri.value)
          printLine(url.value)
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("path-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        String.join(
            System.lineSeparator(),
            "entry.nar",
            "module.norm",
            "https://example.com/a",
            "https://example.com/b",
            ""),
        output.toString());
  }

  @Test
  void roundTripsJavaObjectsThroughNormAny() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("object/binding"));
    Path jar = objectJar(moduleRoot.resolve("lib/object.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "object.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/object.jar",
                integrity: sha256("%s")
              ),
              api: [jarType(name: "ObjectApi", members: ["identity", "newValue", "same"])]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package object.binding

        Void main() {
          Any? value = objectApiNewValue()
          printLine(value != null)
          printLine(objectApiSame(arg0: value, arg1: value))
          printLine(objectApiIdentity("Norm") == "Norm")
          printLine(objectApiIdentity(null) == null)
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("object-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        String.join(System.lineSeparator(), "true", "true", "true", "true", ""), output.toString());
  }

  @Test
  void roundTripsNormDeclarationReferencesThroughJavaClass() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("types/binding"));
    Path jar = classTokenJar(moduleRoot.resolve("lib/class-token.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "types.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/class-token.jar",
                integrity: sha256("%s")
              ),
              api: [jarType(name: "ClassTokenApi", members: ["identity", "name"])]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package types.binding

        Void main() {
          Class<String>? stringType = classTokenApiIdentity<String>(String.class)
          Class<ClassTokenApi>? apiType =
            classTokenApiIdentity<ClassTokenApi>(ClassTokenApi.class)
          printLine((stringType ?? String.class).name())
          printLine((apiType ?? ClassTokenApi.class).name())
          printLine(classTokenApiName(String.class) ?? "")
          printLine(classTokenApiName(ClassTokenApi.class) ?? "")
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("class-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        String.join(
            System.lineSeparator(),
            "String",
            "ClassTokenApi",
            "java.lang.String",
            "sample.ClassTokenApi",
            ""),
        output.toString());
  }

  @Test
  void invokesJavaEnumsAsNativeNormEnums() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("levels/binding"));
    Path jar = enumJar(moduleRoot.resolve("lib/enum.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "levels.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/enum.jar",
                integrity: sha256("%s")
              ),
              api: [jarType(name: "Level", members: ["echo", "label", "values"])]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package levels.binding

        Void main() {
          printLine(levelLabel(Level.HIGH) ?? "")
          Level value = levelEcho(Level.LOW) ?? Level.HIGH
          JavaLevelArray values = levelValues() ?? javaLevelArrayNew(0)
          String label = switch value {
            case HIGH { break "high" }
            case LOW { break "low" }
          }
          printLine(label)
          printLine(levelLabel(values.get(1) ?? Level.HIGH) ?? "")
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("enum-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(String.join(System.lineSeparator(), "HIGH", "low", "LOW", ""), output.toString());
  }

  @Test
  void mapsJavaOptionalValuesToNormNullability() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("options/binding"));
    Path jar = optionalJar(moduleRoot.resolve("lib/optional.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "options.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/optional.jar",
                integrity: sha256("%s")
              ),
              api: [
                jarType(
                  name: "OptionalApi",
                  members: ["echo", "echoDouble", "echoInt", "echoLong", "state"]
                )
              ]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package options.binding

        Void main() {
          printLine(optionalApiEcho("Norm") ?? "")
          printLine(optionalApiEcho(null) == null)
          printLine(optionalApiState(null) ?? "")
          printLine(optionalApiEchoInt(7) ?? 0)
          printLine(optionalApiEchoInt(null) == null)
          printLine(optionalApiEchoLong(8) ?? 0)
          printLine(optionalApiEchoDouble(2.5) ?? 0.0)
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("optional-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        String.join(System.lineSeparator(), "Norm", "true", "empty", "7", "true", "8", "2.5", ""),
        output.toString());
  }

  @Test
  void mapsJavaListsAsLiveNormLists() throws Exception {
    Path moduleRoot = Files.createDirectories(temporaryDirectory.resolve("lists/binding"));
    Path jar = listJar(moduleRoot.resolve("lib/lists.jar"));
    Files.writeString(
        moduleRoot.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "lists.binding",
            version: 1,
            binding: jarBinding(
              target: localJar(
                path: "lib/lists.jar",
                integrity: sha256("%s")
              ),
              api: [
                jarType(
                  name: "ListApi",
                  members: [
                    "append", "asIterable", "collectionAdd", "create", "createMap", "createSet",
                    "hasSet", "head", "iterator", "mapValue", "same"
                  ]
                )
              ]
            )
          )
        }
        """
            .formatted(Sha256Digest.compute(jar).value()));
    Path entry = moduleRoot.resolve("Main.norm");
    Files.writeString(
        entry,
        """
        package lists.binding

        import std.collections.IterableView
        import std.collections.IteratorView
        import std.collections.MutableList
        import std.collections.MutableMap
        import std.collections.MutableSet

        Void main() {
          MutableList<String?>? values = listApiCreate(arg0: "first")
          if values != null {
            listApiAppend(arg0: values, arg1: "second")
            printLine(values.size())
            printLine(values.get(index: 1) ?? "")
            values.set(index: 0, element: "changed")
            printLine(listApiHead(arg0: values) ?? "")
            printLine(listApiSame(arg0: values, arg1: values))
            printLine(listApiCollectionAdd(arg0: values, arg1: "third"))
            printLine(values.size())
            IterableView<String?>? iterable = listApiAsIterable(arg0: values)
            if iterable != null {
              for String? item : iterable {
                printLine(item ?? "")
              }
            }
            IteratorView<String?>? cursor = listApiIterator(arg0: values)
            if cursor != null {
              printLine(cursor.hasNext())
              printLine(cursor.next() ?? "")
            }
          }
          MutableSet<String?>? names = listApiCreateSet(arg0: "first")
          if names != null {
            printLine(names.add(element: "second"))
            printLine(names.add(element: "second"))
            printLine(listApiHasSet(arg0: names, arg1: "second"))
            printLine(names.size())
          }
          MutableMap<String?, Integer?>? counts = listApiCreateMap(arg0: "first", arg1: 1)
          if counts != null {
            printLine(counts.size())
            printLine(counts.put(key: "second", value: 2) == null)
            printLine(listApiMapValue(arg0: counts, arg1: "second") ?? 0)
            printLine(counts.containsKey(key: "second"))
            printLine(counts.remove(key: "second") ?? 0)
            printLine(counts.containsKey(key: "second"))
          }
        }
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    StringWriter output = new StringWriter();
    try (ProjectLoader projects =
            environment.projectLoader(temporaryDirectory.resolve("list-cache"));
        ProjectLauncher launcher =
            new ProjectLauncher(projects, environment.compilerSession(), backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }

    assertEquals(
        String.join(
            System.lineSeparator(),
            "2",
            "second",
            "changed",
            "true",
            "true",
            "3",
            "changed",
            "second",
            "third",
            "true",
            "changed",
            "true",
            "false",
            "true",
            "2",
            "1",
            "true",
            "2",
            "true",
            "2",
            "false",
            ""),
        output.toString());
  }

  private static Path listJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    String owner = "sample/ListApi";
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
    MethodVisitor create =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "create",
            "(Ljava/lang/String;)Ljava/util/List;",
            "(Ljava/lang/String;)Ljava/util/List<Ljava/lang/String;>;",
            null);
    create.visitCode();
    create.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
    create.visitInsn(Opcodes.DUP);
    create.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
    create.visitInsn(Opcodes.DUP);
    create.visitVarInsn(Opcodes.ALOAD, 0);
    create.visitMethodInsn(
        Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
    create.visitInsn(Opcodes.POP);
    create.visitInsn(Opcodes.ARETURN);
    create.visitMaxs(0, 0);
    create.visitEnd();
    MethodVisitor append =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "append",
            "(Ljava/util/List;Ljava/lang/String;)Ljava/util/List;",
            "(Ljava/util/List<Ljava/lang/String;>;Ljava/lang/String;)Ljava/util/List<Ljava/lang/String;>;",
            null);
    append.visitCode();
    append.visitVarInsn(Opcodes.ALOAD, 0);
    append.visitVarInsn(Opcodes.ALOAD, 1);
    append.visitMethodInsn(
        Opcodes.INVOKEINTERFACE, "java/util/List", "add", "(Ljava/lang/Object;)Z", true);
    append.visitInsn(Opcodes.POP);
    append.visitVarInsn(Opcodes.ALOAD, 0);
    append.visitInsn(Opcodes.ARETURN);
    append.visitMaxs(0, 0);
    append.visitEnd();
    MethodVisitor head =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "head",
            "(Ljava/util/List;)Ljava/lang/String;",
            "(Ljava/util/List<Ljava/lang/String;>;)Ljava/lang/String;",
            null);
    head.visitCode();
    head.visitVarInsn(Opcodes.ALOAD, 0);
    head.visitInsn(Opcodes.ICONST_0);
    head.visitMethodInsn(
        Opcodes.INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);
    head.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
    head.visitInsn(Opcodes.ARETURN);
    head.visitMaxs(0, 0);
    head.visitEnd();
    MethodVisitor same =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "same",
            "(Ljava/util/List;Ljava/util/List;)Z",
            "(Ljava/util/List<Ljava/lang/String;>;Ljava/util/List<Ljava/lang/String;>;)Z",
            null);
    same.visitCode();
    same.visitVarInsn(Opcodes.ALOAD, 0);
    same.visitVarInsn(Opcodes.ALOAD, 1);
    org.objectweb.asm.Label different = new org.objectweb.asm.Label();
    same.visitJumpInsn(Opcodes.IF_ACMPNE, different);
    same.visitInsn(Opcodes.ICONST_1);
    same.visitInsn(Opcodes.IRETURN);
    same.visitLabel(different);
    same.visitInsn(Opcodes.ICONST_0);
    same.visitInsn(Opcodes.IRETURN);
    same.visitMaxs(0, 0);
    same.visitEnd();
    MethodVisitor collectionAdd =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "collectionAdd",
            "(Ljava/util/Collection;Ljava/lang/String;)Z",
            "(Ljava/util/Collection<Ljava/lang/String;>;Ljava/lang/String;)Z",
            null);
    collectionAdd.visitCode();
    collectionAdd.visitVarInsn(Opcodes.ALOAD, 0);
    collectionAdd.visitVarInsn(Opcodes.ALOAD, 1);
    collectionAdd.visitMethodInsn(
        Opcodes.INVOKEINTERFACE, "java/util/Collection", "add", "(Ljava/lang/Object;)Z", true);
    collectionAdd.visitInsn(Opcodes.IRETURN);
    collectionAdd.visitMaxs(0, 0);
    collectionAdd.visitEnd();
    MethodVisitor asIterable =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "asIterable",
            "(Ljava/util/Collection;)Ljava/lang/Iterable;",
            "(Ljava/util/Collection<Ljava/lang/String;>;)Ljava/lang/Iterable<Ljava/lang/String;>;",
            null);
    asIterable.visitCode();
    asIterable.visitVarInsn(Opcodes.ALOAD, 0);
    asIterable.visitInsn(Opcodes.ARETURN);
    asIterable.visitMaxs(0, 0);
    asIterable.visitEnd();
    MethodVisitor iterator =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "iterator",
            "(Ljava/util/Collection;)Ljava/util/Iterator;",
            "(Ljava/util/Collection<Ljava/lang/String;>;)Ljava/util/Iterator<Ljava/lang/String;>;",
            null);
    iterator.visitCode();
    iterator.visitVarInsn(Opcodes.ALOAD, 0);
    iterator.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "java/util/Collection",
        "iterator",
        "()Ljava/util/Iterator;",
        true);
    iterator.visitInsn(Opcodes.ARETURN);
    iterator.visitMaxs(0, 0);
    iterator.visitEnd();
    MethodVisitor createSet =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "createSet",
            "(Ljava/lang/String;)Ljava/util/Set;",
            "(Ljava/lang/String;)Ljava/util/Set<Ljava/lang/String;>;",
            null);
    createSet.visitCode();
    createSet.visitTypeInsn(Opcodes.NEW, "java/util/HashSet");
    createSet.visitInsn(Opcodes.DUP);
    createSet.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashSet", "<init>", "()V", false);
    createSet.visitInsn(Opcodes.DUP);
    createSet.visitVarInsn(Opcodes.ALOAD, 0);
    createSet.visitMethodInsn(
        Opcodes.INVOKEINTERFACE, "java/util/Set", "add", "(Ljava/lang/Object;)Z", true);
    createSet.visitInsn(Opcodes.POP);
    createSet.visitInsn(Opcodes.ARETURN);
    createSet.visitMaxs(0, 0);
    createSet.visitEnd();
    MethodVisitor hasSet =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "hasSet",
            "(Ljava/util/Set;Ljava/lang/String;)Z",
            "(Ljava/util/Set<Ljava/lang/String;>;Ljava/lang/String;)Z",
            null);
    hasSet.visitCode();
    hasSet.visitVarInsn(Opcodes.ALOAD, 0);
    hasSet.visitVarInsn(Opcodes.ALOAD, 1);
    hasSet.visitMethodInsn(
        Opcodes.INVOKEINTERFACE, "java/util/Set", "contains", "(Ljava/lang/Object;)Z", true);
    hasSet.visitInsn(Opcodes.IRETURN);
    hasSet.visitMaxs(0, 0);
    hasSet.visitEnd();
    MethodVisitor createMap =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "createMap",
            "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map;",
            "(Ljava/lang/String;Ljava/lang/Integer;)Ljava/util/Map<Ljava/lang/String;Ljava/lang/Integer;>;",
            null);
    createMap.visitCode();
    createMap.visitTypeInsn(Opcodes.NEW, "java/util/HashMap");
    createMap.visitInsn(Opcodes.DUP);
    createMap.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
    createMap.visitInsn(Opcodes.DUP);
    createMap.visitVarInsn(Opcodes.ALOAD, 0);
    createMap.visitVarInsn(Opcodes.ALOAD, 1);
    createMap.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "java/util/Map",
        "put",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        true);
    createMap.visitInsn(Opcodes.POP);
    createMap.visitInsn(Opcodes.ARETURN);
    createMap.visitMaxs(0, 0);
    createMap.visitEnd();
    MethodVisitor mapValue =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "mapValue",
            "(Ljava/util/Map;Ljava/lang/String;)Ljava/lang/Integer;",
            "(Ljava/util/Map<Ljava/lang/String;Ljava/lang/Integer;>;Ljava/lang/String;)Ljava/lang/Integer;",
            null);
    mapValue.visitCode();
    mapValue.visitVarInsn(Opcodes.ALOAD, 0);
    mapValue.visitVarInsn(Opcodes.ALOAD, 1);
    mapValue.visitMethodInsn(
        Opcodes.INVOKEINTERFACE,
        "java/util/Map",
        "get",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        true);
    mapValue.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Integer");
    mapValue.visitInsn(Opcodes.ARETURN);
    mapValue.visitMaxs(0, 0);
    mapValue.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/ListApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path optionalJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    String owner = "sample/OptionalApi";
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
    MethodVisitor echo =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "echo",
            "(Ljava/util/Optional;)Ljava/util/Optional;",
            "(Ljava/util/Optional<Ljava/lang/String;>;)Ljava/util/Optional<Ljava/lang/String;>;",
            null);
    echo.visitCode();
    echo.visitVarInsn(Opcodes.ALOAD, 0);
    echo.visitInsn(Opcodes.ARETURN);
    echo.visitMaxs(0, 0);
    echo.visitEnd();
    MethodVisitor state =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "state",
            "(Ljava/util/Optional;)Ljava/lang/String;",
            "(Ljava/util/Optional<Ljava/lang/String;>;)Ljava/lang/String;",
            null);
    state.visitCode();
    state.visitVarInsn(Opcodes.ALOAD, 0);
    state.visitLdcInsn("empty");
    state.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/util/Optional",
        "orElse",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        false);
    state.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/String");
    state.visitInsn(Opcodes.ARETURN);
    state.visitMaxs(0, 0);
    state.visitEnd();
    MethodVisitor echoInt =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "echoInt",
            "(Ljava/util/OptionalInt;)Ljava/util/OptionalInt;",
            null,
            null);
    echoInt.visitCode();
    echoInt.visitVarInsn(Opcodes.ALOAD, 0);
    echoInt.visitInsn(Opcodes.ARETURN);
    echoInt.visitMaxs(0, 0);
    echoInt.visitEnd();
    MethodVisitor echoLong =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "echoLong",
            "(Ljava/util/OptionalLong;)Ljava/util/OptionalLong;",
            null,
            null);
    echoLong.visitCode();
    echoLong.visitVarInsn(Opcodes.ALOAD, 0);
    echoLong.visitInsn(Opcodes.ARETURN);
    echoLong.visitMaxs(0, 0);
    echoLong.visitEnd();
    MethodVisitor echoDouble =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "echoDouble",
            "(Ljava/util/OptionalDouble;)Ljava/util/OptionalDouble;",
            null,
            null);
    echoDouble.visitCode();
    echoDouble.visitVarInsn(Opcodes.ALOAD, 0);
    echoDouble.visitInsn(Opcodes.ARETURN);
    echoDouble.visitMaxs(0, 0);
    echoDouble.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/OptionalApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path enumJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    String owner = "sample/Level";
    String descriptor = "Lsample/Level;";
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_ENUM,
        owner,
        "Ljava/lang/Enum<Lsample/Level;>;",
        "java/lang/Enum",
        null);
    writer
        .visitField(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
            "HIGH",
            descriptor,
            null,
            null)
        .visitEnd();
    writer
        .visitField(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_ENUM,
            "LOW",
            descriptor,
            null,
            null)
        .visitEnd();
    writer
        .visitField(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
            "$VALUES",
            "[Lsample/Level;",
            null,
            null)
        .visitEnd();
    MethodVisitor constructor =
        writer.visitMethod(Opcodes.ACC_PRIVATE, "<init>", "(Ljava/lang/String;I)V", "()V", null);
    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitVarInsn(Opcodes.ALOAD, 1);
    constructor.visitVarInsn(Opcodes.ILOAD, 2);
    constructor.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "java/lang/Enum", "<init>", "(Ljava/lang/String;I)V", false);
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(0, 0);
    constructor.visitEnd();
    MethodVisitor label =
        writer.visitMethod(Opcodes.ACC_PUBLIC, "label", "()Ljava/lang/String;", null, null);
    label.visitCode();
    label.visitVarInsn(Opcodes.ALOAD, 0);
    label.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/lang/Enum", "name", "()Ljava/lang/String;", false);
    label.visitInsn(Opcodes.ARETURN);
    label.visitMaxs(0, 0);
    label.visitEnd();
    MethodVisitor echo =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "echo",
            "(Lsample/Level;)Lsample/Level;",
            null,
            null);
    echo.visitCode();
    echo.visitVarInsn(Opcodes.ALOAD, 0);
    echo.visitInsn(Opcodes.ARETURN);
    echo.visitMaxs(0, 0);
    echo.visitEnd();
    MethodVisitor values =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "values", "()[Lsample/Level;", null, null);
    values.visitCode();
    values.visitFieldInsn(Opcodes.GETSTATIC, owner, "$VALUES", "[Lsample/Level;");
    values.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "[Lsample/Level;", "clone", "()Ljava/lang/Object;", false);
    values.visitTypeInsn(Opcodes.CHECKCAST, "[Lsample/Level;");
    values.visitInsn(Opcodes.ARETURN);
    values.visitMaxs(0, 0);
    values.visitEnd();
    MethodVisitor valueOf =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "valueOf",
            "(Ljava/lang/String;)Lsample/Level;",
            null,
            null);
    valueOf.visitCode();
    valueOf.visitLdcInsn(org.objectweb.asm.Type.getType(descriptor));
    valueOf.visitVarInsn(Opcodes.ALOAD, 0);
    valueOf.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/Enum",
        "valueOf",
        "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;",
        false);
    valueOf.visitTypeInsn(Opcodes.CHECKCAST, owner);
    valueOf.visitInsn(Opcodes.ARETURN);
    valueOf.visitMaxs(0, 0);
    valueOf.visitEnd();
    MethodVisitor classInitializer =
        writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
    classInitializer.visitCode();
    classInitializer.visitTypeInsn(Opcodes.NEW, owner);
    classInitializer.visitInsn(Opcodes.DUP);
    classInitializer.visitLdcInsn("HIGH");
    classInitializer.visitInsn(Opcodes.ICONST_0);
    classInitializer.visitMethodInsn(
        Opcodes.INVOKESPECIAL, owner, "<init>", "(Ljava/lang/String;I)V", false);
    classInitializer.visitFieldInsn(Opcodes.PUTSTATIC, owner, "HIGH", descriptor);
    classInitializer.visitTypeInsn(Opcodes.NEW, owner);
    classInitializer.visitInsn(Opcodes.DUP);
    classInitializer.visitLdcInsn("LOW");
    classInitializer.visitInsn(Opcodes.ICONST_1);
    classInitializer.visitMethodInsn(
        Opcodes.INVOKESPECIAL, owner, "<init>", "(Ljava/lang/String;I)V", false);
    classInitializer.visitFieldInsn(Opcodes.PUTSTATIC, owner, "LOW", descriptor);
    classInitializer.visitInsn(Opcodes.ICONST_2);
    classInitializer.visitTypeInsn(Opcodes.ANEWARRAY, owner);
    classInitializer.visitInsn(Opcodes.DUP);
    classInitializer.visitInsn(Opcodes.ICONST_0);
    classInitializer.visitFieldInsn(Opcodes.GETSTATIC, owner, "HIGH", descriptor);
    classInitializer.visitInsn(Opcodes.AASTORE);
    classInitializer.visitInsn(Opcodes.DUP);
    classInitializer.visitInsn(Opcodes.ICONST_1);
    classInitializer.visitFieldInsn(Opcodes.GETSTATIC, owner, "LOW", descriptor);
    classInitializer.visitInsn(Opcodes.AASTORE);
    classInitializer.visitFieldInsn(Opcodes.PUTSTATIC, owner, "$VALUES", "[Lsample/Level;");
    classInitializer.visitInsn(Opcodes.RETURN);
    classInitializer.visitMaxs(0, 0);
    classInitializer.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/Level.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path classTokenJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/ClassTokenApi",
        null,
        "java/lang/Object",
        null);
    MethodVisitor identity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "identity",
            "(Ljava/lang/Class;)Ljava/lang/Class;",
            "<T:Ljava/lang/Object;>(Ljava/lang/Class<TT;>;)Ljava/lang/Class<TT;>;",
            null);
    identity.visitCode();
    identity.visitVarInsn(Opcodes.ALOAD, 0);
    identity.visitInsn(Opcodes.ARETURN);
    identity.visitMaxs(0, 0);
    identity.visitEnd();
    MethodVisitor name =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "name",
            "(Ljava/lang/Class;)Ljava/lang/String;",
            "(Ljava/lang/Class<*>;)Ljava/lang/String;",
            null);
    name.visitCode();
    name.visitVarInsn(Opcodes.ALOAD, 0);
    name.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getName", "()Ljava/lang/String;", false);
    name.visitInsn(Opcodes.ARETURN);
    name.visitMaxs(0, 0);
    name.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/ClassTokenApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path objectJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/ObjectApi",
        null,
        "java/lang/Object",
        null);
    MethodVisitor newValue =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "newValue",
            "()Ljava/lang/Object;",
            null,
            null);
    newValue.visitCode();
    newValue.visitTypeInsn(Opcodes.NEW, "java/lang/Object");
    newValue.visitInsn(Opcodes.DUP);
    newValue.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    newValue.visitInsn(Opcodes.ARETURN);
    newValue.visitMaxs(0, 0);
    newValue.visitEnd();
    MethodVisitor identity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "identity",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            null,
            null);
    identity.visitCode();
    identity.visitVarInsn(Opcodes.ALOAD, 0);
    identity.visitInsn(Opcodes.ARETURN);
    identity.visitMaxs(0, 0);
    identity.visitEnd();
    MethodVisitor same =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "same",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z",
            null,
            null);
    same.visitCode();
    same.visitVarInsn(Opcodes.ALOAD, 0);
    same.visitVarInsn(Opcodes.ALOAD, 1);
    org.objectweb.asm.Label different = new org.objectweb.asm.Label();
    same.visitJumpInsn(Opcodes.IF_ACMPNE, different);
    same.visitInsn(Opcodes.ICONST_1);
    same.visitInsn(Opcodes.IRETURN);
    same.visitLabel(different);
    same.visitInsn(Opcodes.ICONST_0);
    same.visitInsn(Opcodes.IRETURN);
    same.visitMaxs(0, 0);
    same.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/ObjectApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path failureJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/FailureApi",
        null,
        "java/lang/Object",
        null);
    MethodVisitor failure =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "fail",
            "(Ljava/lang/String;)Ljava/lang/String;",
            null,
            null);
    failure.visitCode();
    failure.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
    failure.visitInsn(Opcodes.DUP);
    failure.visitVarInsn(Opcodes.ALOAD, 0);
    failure.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "java/lang/IllegalStateException",
        "<init>",
        "(Ljava/lang/String;)V",
        false);
    failure.visitInsn(Opcodes.ATHROW);
    failure.visitMaxs(3, 1);
    failure.visitEnd();
    MethodVisitor identity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "identity",
            "(Ljava/lang/Throwable;)Ljava/lang/Throwable;",
            null,
            null);
    identity.visitCode();
    identity.visitVarInsn(Opcodes.ALOAD, 0);
    identity.visitInsn(Opcodes.ARETURN);
    identity.visitMaxs(1, 1);
    identity.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/FailureApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path resourceJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/Managed",
        null,
        "java/lang/Object",
        new String[] {"java/lang/AutoCloseable"});
    writer.visitField(Opcodes.ACC_PRIVATE, "closed", "Z", null, null).visitEnd();
    writer
        .visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "count", "I", null, null)
        .visitEnd();
    MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(1, 1);
    constructor.visitEnd();
    MethodVisitor closed = writer.visitMethod(Opcodes.ACC_PUBLIC, "closed", "()Z", null, null);
    closed.visitCode();
    closed.visitVarInsn(Opcodes.ALOAD, 0);
    closed.visitFieldInsn(Opcodes.GETFIELD, "sample/Managed", "closed", "Z");
    closed.visitInsn(Opcodes.IRETURN);
    closed.visitMaxs(1, 1);
    closed.visitEnd();
    MethodVisitor count =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "closeCount", "()I", null, null);
    count.visitCode();
    count.visitFieldInsn(Opcodes.GETSTATIC, "sample/Managed", "count", "I");
    count.visitInsn(Opcodes.IRETURN);
    count.visitMaxs(1, 0);
    count.visitEnd();
    MethodVisitor close = writer.visitMethod(Opcodes.ACC_PUBLIC, "close", "()V", null, null);
    org.objectweb.asm.Label firstClose = new org.objectweb.asm.Label();
    close.visitCode();
    close.visitVarInsn(Opcodes.ALOAD, 0);
    close.visitFieldInsn(Opcodes.GETFIELD, "sample/Managed", "closed", "Z");
    close.visitJumpInsn(Opcodes.IFEQ, firstClose);
    close.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalStateException");
    close.visitInsn(Opcodes.DUP);
    close.visitLdcInsn("closed twice");
    close.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        "java/lang/IllegalStateException",
        "<init>",
        "(Ljava/lang/String;)V",
        false);
    close.visitInsn(Opcodes.ATHROW);
    close.visitLabel(firstClose);
    close.visitVarInsn(Opcodes.ALOAD, 0);
    close.visitInsn(Opcodes.ICONST_1);
    close.visitFieldInsn(Opcodes.PUTFIELD, "sample/Managed", "closed", "Z");
    close.visitFieldInsn(Opcodes.GETSTATIC, "sample/Managed", "count", "I");
    close.visitInsn(Opcodes.ICONST_1);
    close.visitInsn(Opcodes.IADD);
    close.visitFieldInsn(Opcodes.PUTSTATIC, "sample/Managed", "count", "I");
    close.visitLdcInsn(RESOURCE_CLOSE_PROPERTY);
    close.visitFieldInsn(Opcodes.GETSTATIC, "sample/Managed", "count", "I");
    close.visitMethodInsn(
        Opcodes.INVOKESTATIC, "java/lang/Integer", "toString", "(I)Ljava/lang/String;", false);
    close.visitMethodInsn(
        Opcodes.INVOKESTATIC,
        "java/lang/System",
        "setProperty",
        "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        false);
    close.visitInsn(Opcodes.POP);
    close.visitInsn(Opcodes.RETURN);
    close.visitMaxs(3, 1);
    close.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/Managed.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }

  private static Path streamJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
    String owner = "sample/StreamApi";
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, owner, null, "java/lang/Object", null);
    MethodVisitor input =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "input",
            "(Ljava/lang/String;)Ljava/io/InputStream;",
            null,
            null);
    input.visitCode();
    input.visitTypeInsn(Opcodes.NEW, "java/io/ByteArrayInputStream");
    input.visitInsn(Opcodes.DUP);
    input.visitVarInsn(Opcodes.ALOAD, 0);
    input.visitFieldInsn(
        Opcodes.GETSTATIC,
        "java/nio/charset/StandardCharsets",
        "UTF_8",
        "Ljava/nio/charset/Charset;");
    input.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/lang/String",
        "getBytes",
        "(Ljava/nio/charset/Charset;)[B",
        false);
    input.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "java/io/ByteArrayInputStream", "<init>", "([B)V", false);
    input.visitInsn(Opcodes.ARETURN);
    input.visitMaxs(0, 0);
    input.visitEnd();
    MethodVisitor output =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "output",
            "()Ljava/io/OutputStream;",
            null,
            null);
    output.visitCode();
    output.visitTypeInsn(Opcodes.NEW, "java/io/ByteArrayOutputStream");
    output.visitInsn(Opcodes.DUP);
    output.visitMethodInsn(
        Opcodes.INVOKESPECIAL, "java/io/ByteArrayOutputStream", "<init>", "()V", false);
    output.visitInsn(Opcodes.ARETURN);
    output.visitMaxs(0, 0);
    output.visitEnd();
    MethodVisitor outputText =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "outputText",
            "(Ljava/io/OutputStream;)Ljava/lang/String;",
            null,
            null);
    outputText.visitCode();
    outputText.visitVarInsn(Opcodes.ALOAD, 0);
    outputText.visitTypeInsn(Opcodes.CHECKCAST, "java/io/ByteArrayOutputStream");
    outputText.visitFieldInsn(
        Opcodes.GETSTATIC,
        "java/nio/charset/StandardCharsets",
        "UTF_8",
        "Ljava/nio/charset/Charset;");
    outputText.visitMethodInsn(
        Opcodes.INVOKEVIRTUAL,
        "java/io/ByteArrayOutputStream",
        "toString",
        "(Ljava/nio/charset/Charset;)Ljava/lang/String;",
        false);
    outputText.visitInsn(Opcodes.ARETURN);
    outputText.visitMaxs(0, 0);
    outputText.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream archive = new JarOutputStream(Files.newOutputStream(path))) {
      archive.putNextEntry(new JarEntry("sample/StreamApi.class"));
      archive.write(writer.toByteArray());
      archive.closeEntry();
    }
    return path;
  }

  private static Path pathJar(Path path) throws Exception {
    ClassWriter writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
        "sample/PathApi",
        null,
        "java/lang/Object",
        null);
    MethodVisitor pathIdentity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "path",
            "(Ljava/nio/file/Path;)Ljava/nio/file/Path;",
            null,
            null);
    pathIdentity.visitCode();
    pathIdentity.visitVarInsn(Opcodes.ALOAD, 0);
    pathIdentity.visitInsn(Opcodes.ARETURN);
    pathIdentity.visitMaxs(1, 1);
    pathIdentity.visitEnd();
    MethodVisitor fileIdentity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "file",
            "(Ljava/io/File;)Ljava/io/File;",
            null,
            null);
    fileIdentity.visitCode();
    fileIdentity.visitVarInsn(Opcodes.ALOAD, 0);
    fileIdentity.visitInsn(Opcodes.ARETURN);
    fileIdentity.visitMaxs(1, 1);
    fileIdentity.visitEnd();
    MethodVisitor uriIdentity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "uri",
            "(Ljava/net/URI;)Ljava/net/URI;",
            null,
            null);
    uriIdentity.visitCode();
    uriIdentity.visitVarInsn(Opcodes.ALOAD, 0);
    uriIdentity.visitInsn(Opcodes.ARETURN);
    uriIdentity.visitMaxs(1, 1);
    uriIdentity.visitEnd();
    MethodVisitor urlIdentity =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "url",
            "(Ljava/net/URL;)Ljava/net/URL;",
            null,
            null);
    urlIdentity.visitCode();
    urlIdentity.visitVarInsn(Opcodes.ALOAD, 0);
    urlIdentity.visitInsn(Opcodes.ARETURN);
    urlIdentity.visitMaxs(1, 1);
    urlIdentity.visitEnd();
    writer.visitEnd();
    Files.createDirectories(path.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(path))) {
      output.putNextEntry(new JarEntry("sample/PathApi.class"));
      output.write(writer.toByteArray());
      output.closeEntry();
    }
    return path;
  }
}
