package dev.w0fv1.norm.truffle;

import static dev.w0fv1.norm.testing.NormTestKit.compile;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.w0fv1.norm.execution.ExecutionContext;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

final class SerializationRuntimeTest {
  @Test
  void cachesEachExactGenericShapeAndItsNestedShapesOnce() {
    var checked =
        compile(
                "import std.serialization.Serializable import std.json.toJson "
                    + "@Serializable() value Box<T> { T value } Void main() { "
                    + "Box<Integer>(value: 1).toJson() Box<Integer>(value: 2).toJson() "
                    + "Box<String>(value: \"a\").toJson() Box<String>(value: \"b\").toJson() }")
            .program()
            .orElseThrow();
    ExecutableProgram executable = new Lowerer(null).lower(checked.compilation().artifact());

    executable.execute(ExecutionContext.of(new PrintWriter(new StringWriter())));

    assertEquals(4, executable.annotations().serialization().cachedShapeCount());
    assertEquals(2, executable.annotations().mapper().cachedWriterCount());
    assertEquals(0, executable.annotations().mapper().cachedReaderCount());
  }

  @Test
  void separatesCompiledPlansByFormatAndDirection() {
    var checked =
        compile(
                "import std.serialization.Serializable import std.json.fromJson "
                    + "import std.json.toJson import std.xml.fromXml import std.xml.toXml "
                    + "import std.yaml.fromYaml import std.yaml.toYaml "
                    + "@Serializable() value Message { String text } Void main() { "
                    + "Message source = Message(text: \"Norm\") "
                    + "source.toJson().fromJson<Message>() "
                    + "source.toXml().fromXml<Message>() "
                    + "source.toYaml().fromYaml<Message>() }")
            .program()
            .orElseThrow();
    ExecutableProgram executable = new Lowerer(null).lower(checked.compilation().artifact());

    executable.execute(ExecutionContext.of(new PrintWriter(new StringWriter())));

    assertEquals(3, executable.annotations().mapper().cachedWriterCount());
    assertEquals(3, executable.annotations().mapper().cachedReaderCount());
  }
}
