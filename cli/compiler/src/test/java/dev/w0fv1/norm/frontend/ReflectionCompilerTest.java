package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.CompilationResult;
import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ReflectionCompilerTest {
  @Test
  void typesDeclarationReferencesAndReflectionCollections() {
    CompilationResult result =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("reflection.norm"),
                    "class User { "
                        + "public String name "
                        + "User(String name) { this.name = name } "
                        + "public String label(Integer prefix) { return name } "
                        + "} "
                        + "String findUser(Integer id) { return id.toString() } "
                        + "Void main() { "
                        + "User user = User(name: \"Ada\") "
                        + "Class<User> userClass = User.class "
                        + "Class<List<String>> listClass = List<String>.class "
                        + "Class<String?> nullableClass = String?.class "
                        + "Field<User, String> nameField = User.name.field "
                        + "Function<String(Integer)> lookup = findUser.function "
                        + "Function<String(User, Integer)> unbound = User.label.function "
                        + "Function<String(Integer)> bound = user.label "
                        + "List<Field<User, ?>> fields = userClass.fields() "
                        + "List<Function<?>> functions = userClass.functions() "
                        + "List<Constructor<User>> constructors = userClass.constructors() "
                        + "List<Parameter<?>> parameters = lookup.parameters() "
                        + "String parameterName = parameters[0].name() "
                        + "Class<?> parameterType = parameters[0].type() "
                        + "Function<?> parameterFunction = parameters[0].function() "
                        + "Class<User> owner = nameField.owner() "
                        + "Class<String> fieldType = nameField.type() "
                        + "String value = nameField.read(receiver: user) "
                        + "printLine(value) "
                        + "}"));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void overloadReferencesRequireAnExactExpectedFunctionType() {
    CompilationResult result =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("overload-reference.norm"),
                    "String parse(Integer value) { return value.toString() } "
                        + "String parse(String value) { return value } "
                        + "Void main() { "
                        + "Function<String(Integer)> integerParser = parse.function "
                        + "Function<String(String)> stringParser = parse.function "
                        + "}"));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void bindsInterfaceClassLiterals() {
    CompilationResult result =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("interface-reflection.norm"),
                    "interface Named { String name() } "
                        + "Void main() { Class<Named> type = Named.class printLine(type.name()) }"));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }

  @Test
  void bindsVoidClassAsAnUnboundedClassValue() {
    CompilationResult result =
        new CompilerSession()
            .compile(
                SourceFile.of(
                    Path.of("void-reflection.norm"),
                    "Void main() { Class<?> type = Void.class printLine(type.name()) }"));

    assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
  }
}
