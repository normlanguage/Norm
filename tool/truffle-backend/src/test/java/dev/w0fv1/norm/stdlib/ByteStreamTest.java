package dev.w0fv1.norm.stdlib;

import static dev.w0fv1.norm.testing.NormTestKit.assertOutput;

import org.junit.jupiter.api.Test;

final class ByteStreamTest {
  @Test
  void encodesAndDecodesStrictUtf8() {
    assertOutput(
        "import std.io.Bytes import std.io.TextEncoding import std.io.decodeText "
            + "import std.io.encodeText Void main() { "
            + "Bytes content = encodeText(text: \"Norm 文件\", encoding: TextEncoding.Utf8) "
            + "printLine(content.size()) "
            + "printLine(decodeText(content: content, encoding: TextEncoding.Utf8)) }",
        "11",
        "Norm 文件");
  }

  @Test
  void composesPartialReadsAndWritesWithoutConfusingEof() {
    assertOutput(
        "import std.io.ByteReader import std.io.ByteWriter import std.io.Bytes "
            + "import std.io.ReadChunk import std.io.StreamException import std.io.bytes "
            + "import std.io.readAll import std.io.writeAll "
            + "class Reader implements ByteReader { Integer index "
            + "public ReadChunk read(Integer maximumBytes) { index = index + 1 "
            + "if index == 1 { return ReadChunk.Data(bytes: bytes(values: [1])) } "
            + "if index == 2 { return ReadChunk.Data(bytes: bytes(values: [2, 3])) } "
            + "return ReadChunk.Eof } } "
            + "class Writer implements ByteWriter { List<Integer> values "
            + "public Integer write(Bytes content) { if content.size() == 0 { return 0 } "
            + "values.add(content.at(index: 0)) return 1 } } "
            + "class StalledWriter implements ByteWriter { public Integer write(Bytes content) { return 0 } } "
            + "Void main() { Bytes content = readAll(reader: Reader(index: 0), maximumBytes: 3) "
            + "printLine(content.at(index: 0)) printLine(content.at(index: 1)) "
            + "printLine(content.at(index: 2)) Writer writer = Writer(values: List<>()) "
            + "writeAll(writer: writer, content: content) printLine(writer.values[0]) "
            + "printLine(writer.values[1]) printLine(writer.values[2]) "
            + "try { readAll(reader: Reader(index: 0), maximumBytes: 2) } "
            + "catch StreamException error { printLine(error.reason) } "
            + "try { writeAll(writer: StalledWriter(), content: content) } "
            + "catch StreamException error { printLine(error.reason) } }",
        "1",
        "2",
        "3",
        "1",
        "2",
        "3",
        "StreamFailure.LimitExceeded",
        "StreamFailure.NoProgress");
  }
}
