package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.source.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SourceFormatterTest {
  private final SourceFormatter formatter = new SourceFormatter();

  @Test
  void canonicalizesOnlyCompleteBlockCallsAndKeepsStatementsSeparate() {
    String expected =
        """
        Void main() {
          produce {
            1
          } map { item in
            item
          } finish {
            result
          }
        }
        """;
    assertFormats("Void main() { produce { 1 }.map { item in item }.finish { result } }", expected);
    assertFormats("Void main() { produce { 1 } map { item in item } finish { result } }", expected);
    assertFormats(
        "Void main() { produce { 1 }\n.map { item in item }\n.finish { result } }", expected);
    assertFormats(
        "Void main() { first {}; second {} }",
        """
        Void main() {
          first {

          }
          second {

          }
        }
        """);
  }

  @Test
  void retainsExplicitDotsOutsideTheBlockCallContract() {
    for (String call :
        java.util.List.of(
            "produce().map {}",
            "produce {}.map<Integer> {}",
            "produce {}.map(option: value) {}",
            "produce {}?.map {}",
            "produce {}.map",
            "produce {}[0].map {}")) {
      String formatted =
          formatter
              .format(SourceFile.of(Path.of("chain.norm"), "Void main() { " + call + " }"))
              .orElseThrow();
      assertTrue(formatted.contains(".map"), formatted);
      assertEquals(
          formatted,
          formatter.format(SourceFile.of(Path.of("chain.norm"), formatted)).orElseThrow());
    }
  }

  @Test
  void neverBreaksTheBlockConnectionHeaderEvenBeyondTheLineWidth() {
    String name = "map".repeat(40);
    String text = "Void main() { if (produce {}." + name + " {}) {} }";
    String formatted = formatter.format(SourceFile.of(Path.of("chain.norm"), text)).orElseThrow();
    assertTrue(formatted.contains("} " + name + " {"), formatted);
    assertTrue(formatted.contains("if (produce"), formatted);
    assertEquals(
        formatted, formatter.format(SourceFile.of(Path.of("chain.norm"), formatted)).orElseThrow());
  }

  @Test
  void breaksCollectionBranchesAroundMultilineComponents() {
    assertFormats(
        "Widget build(){[if(empty) Text(\"空\") else Scroll(Column([Button(\"添加\"){add()}]))]}",
        """
        Widget build() {
          [
            if (empty)
              Text("空")
            else
              Scroll(Column([
                Button("添加") {
                  add()
                }
              ]))
          ]
        }
        """);
  }

  @Test
  void laysOutNestedComponentsWithoutAccumulatingCallIndentation() {
    assertFormats(
        "Widget"
            + " build(){Column([Text(\"任务\"),Row([Button(\"全部\"){reload(null)},Button(\"未完成\"){reload(false)}])])}",
        """
        Widget build() {
          Column([
            Text("任务"),
            Row([
              Button("全部") {
                reload(null)
              },
              Button("未完成") {
                reload(false)
              }
            ])
          ])
        }
        """);
  }

  @Test
  void putsMultilineNamedCallbacksOnSeparateArgumentLines() {
    assertFormats(
        "Void submit(){change(write:(){save()},saved:(){reload()})}",
        """
        Void submit() {
          change(
            write: () {
              save()
            },
            saved: () {
              reload()
            }
          )
        }
        """);
  }

  @Test
  void preservesMultilineLiteralContentsAndExecution() {
    String literal = "\"\"\"\n  select \"todo\"\n  ${1 + 2}\n\"\"\"";
    var source = SourceFile.of(Path.of("query.norm"), "Void main(){printLine(" + literal + ")}");
    var formatted = formatter.format(source).orElseThrow();
    assertTrue(formatted.contains(literal));
    assertEquals(
        formatted, formatter.format(SourceFile.of(source.path(), formatted)).orElseThrow());
    assertEquals(
        "\n  select \"todo\"\n  3\n" + System.lineSeparator(),
        dev.w0fv1.norm.testing.NormTestKit.run(formatted));
  }

  @Test
  void preservesCoalescingThrowExpressions() {
    var source =
        SourceFile.of(
            Path.of("throw.norm"),
            """
            import std.core.Exception
            Long present(Long? id){id??throw Exception(message:"missing")}
            Void main(){}
            """);
    var formatted = formatter.format(source).orElseThrow();
    assertTrue(formatted.contains("id ?? throw Exception"));
    assertEquals(
        formatted, formatter.format(SourceFile.of(source.path(), formatted)).orElseThrow());
    assertTrue(dev.w0fv1.norm.testing.NormTestKit.compile(formatted).isSuccess());
  }

  @Test
  void preservesClassMethodDeclarationsWithoutInventingBodies() {
    var source =
        SourceFile.of(
            Path.of("repository.norm"),
            "class Repository<T>{T? find(Long id); Void clear() Void close(){}}");
    var formatted = formatter.format(source).orElseThrow();
    assertTrue(formatted.contains("T? find(Long id)\n"));
    assertTrue(formatted.contains("Void clear()\n"));
    assertTrue(formatted.contains("Void close() {}"));
    assertEquals(
        formatted, formatter.format(SourceFile.of(source.path(), formatted)).orElseThrow());
  }

  @Test
  void preservesPostfixNonNullAssertions() {
    var source =
        SourceFile.of(
            Path.of("assert.norm"),
            "String name(String? value){value!!} Boolean flag(Boolean? value){!value!!} Void"
                + " main(){}");
    var formatted = formatter.format(source).orElseThrow();
    assertTrue(formatted.contains("value!!"));
    assertTrue(formatted.contains("!value!!"));
    assertEquals(
        formatted,
        formatter.format(SourceFile.of(Path.of("assert.norm"), formatted)).orElseThrow());
    assertTrue(dev.w0fv1.norm.testing.NormTestKit.compile(formatted).isSuccess());
  }

  @Test
  void formatsCollectionControlFlowWithoutChangingItsMeaning() {
    var source =
        SourceFile.of(
            Path.of("collections.norm"),
            "Void main(){List<Integer> values=[1,if(true) 2 else 3,...[4],for(Integer"
                + " n,i:[5,6])n+i]}");
    String formatted = formatter.format(source).orElseThrow();
    assertTrue(formatted.contains("if (true) 2 else 3"));
    assertTrue(formatted.contains("...[4]"));
    assertTrue(formatted.contains("for (Integer n, i : [5, 6]) n + i"));
    assertEquals(
        formatted,
        formatter.format(SourceFile.of(Path.of("collections.norm"), formatted)).orElseThrow());
    assertTrue(dev.w0fv1.norm.testing.NormTestKit.compile(formatted).isSuccess());
  }

  @Test
  void preservesImplicitReturnsAndIfExpressions() {
    assertFormats(
        "String choose(Boolean flag){var selected=if flag{\"yes\"}else{\"no\"} selected}",
        """
        String choose(Boolean flag) {
          var selected = if flag "yes" else "no"
          selected
        }
        """);
  }

  @Test
  void formatsComputedPropertiesAsOneDeclaration() {
    assertFormats(
        "class Counter{Integer value{get{return 1}private set(next){printLine(next)}}}",
        """
        class Counter {
          Integer value {
            get {
              return 1
            }
            private set(next) {
              printLine(next)
            }
          }
        }
        """);
  }

  @Test
  void preservesTrailingLambdaSyntaxAndControlFlowBoundaries() {
    assertFormats(
        "main(){run(){value in value+1} if (test(){true}){printLine(1)}}",
        """
        main() {
          run { value in
            value + 1
          }
          if (test {
            true
          }) {
            printLine(1)
          }
        }
        """);
  }

  @Test
  void preservesExplicitGenericTrailingLambdaCalls() {
    assertFormats(
        "main(){submit<List<String>>{[\"Norm\"]} runner.run<Integer>{42}}",
        """
        main() {
          submit<List<String>> {
            ["Norm"]
          }
          runner.run<Integer> {
            42
          }
        }
        """);
  }

  @Test
  void formatsDeclarationsAndOmitsDefaultPublicVisibility() {
    assertFormats(
        "public class Box{private Integer value public Integer get(){return value}}public"
            + " main(){Box box=Box(value:1) printLine(box.get())}",
        """
        class Box {
          private Integer value

          Integer get() {
            return value
          }
        }

        main() {
          Box box = Box(value: 1)
          printLine(box.get())
        }
        """);
  }

  @Test
  void formatsValueDeclarations() {
    assertFormats(
        "value Point<T> implements Named<T>{T value public T read(){return value}}",
        """
        value Point<T> implements Named<T> {
          T value

          T read() {
            return value
          }
        }
        """);
  }

  @Test
  void formatsGenericTypeParameterDefaults() {
    assertFormats(
        "enum Result<T,E=String>{Ok(T value),Err(E error)}",
        """
        enum Result<T, E = String> { Ok(T value), Err(E error) }
        """);
  }

  @Test
  void formatsInheritanceAndConstructors() {
    assertFormats(
        "class Child extends Base<String>{Integer rank Child(String name,Integer"
            + " value){super(name:name) rank=value}}",
        """
        class Child extends Base<String> {
          Integer rank

          Child(String name, Integer value) {
            super(name: name)
            rank = value
          }
        }
        """);
  }

  @Test
  void formatsReferenceTypesAndOperations() {
    assertFormats(
        "Void replace(ref<Integer> target,Integer value){*target=value}main(){Integer value=1"
            + " ref<Integer> location=&value\n"
            + "*location=2 replace(target:location,value:*location+1)}",
        """
        Void replace(ref<Integer> target, Integer value) {
          *target = value
        }

        main() {
          Integer value = 1
          ref<Integer> location = &value
          *location = 2
          replace(target: location, value: *location + 1)
        }
        """);
  }

  @Test
  void formatsExceptionControlFlow() {
    assertFormats(
        "Void run(){try{load()}catch IOException error{throw error}finally{close()}}",
        """
        Void run() {
          try {
            load()
          } catch IOException error {
            throw error
          } finally {
            close()
          }
        }
        """);
  }

  @Test
  void formatsAnnotationsAndReflection() {
    assertFormats(
        "annotation Label implements TypeTarget,RuntimeRetention{String text String?"
            + " replacement}@Label(text:\"point\",replacement:null)value"
            + " Point{@Label(text:\"x\",replacement:null)Integer x}Void main(){Label?"
            + " label=Point.class.annotation<Label>()}",
        """
        annotation Label implements TypeTarget, RuntimeRetention {
          String text

          String? replacement
        }

        @Label(text: "point", replacement: null)
        value Point {
          @Label(text: "x", replacement: null)
          Integer x
        }

        Void main() {
          Label? label = Point.class.annotation<Label>()
        }
        """);
  }

  @Test
  void formatsNestedExpressionsAndControlFlow() {
    assertFormats(
        """
        Integer choose(Integer value){if value>0{return value}else{return -(value+1)}}
        main(){List<Integer> values=[1,2,3] for value,index:values{printLines([value,index])}}
        """,
        """
        Integer choose(Integer value) {
          if value > 0 {
            return value
          } else {
            return -(value + 1)
          }
        }

        main() {
          List<Integer> values = [1, 2, 3]
          for value, index : values {
            printLines([value, index])
          }
        }
        """);
  }

  @Test
  void formatsFunctionValuesAndSwitchExpressions() {
    assertFormats(
        """
        Integer apply(Integer transform(Integer value),Integer value){return transform(value)}
        main(){var doubled=(Integer value){value*2} Integer result=switch doubled(2){case 4{return 1}case _{return 0}} printLine(result)}
        """,
        """
        Integer apply(Integer transform(Integer value), Integer value) {
          return transform(value)
        }

        main() {
          var doubled = (Integer value) {
            value * 2
          }
          Integer result = switch doubled(2) {
            case 4 {
              return 1
            }
            case _ {
              return 0
            }
          }
          printLine(result)
        }
        """);
  }

  @Test
  void formatsExtensionFunctions() {
    assertFormats(
        "public extension String display<T>(T value){return value.toString()}",
        """
        extension String display<T>(T value) {
          return value.toString()
        }
        """);
  }

  @Test
  void preservesStringInterpolationAndLiteralPlaceholders() {
    assertFormats(
        "String greet(String name){return \"\\${literal}: ${name}\"}",
        """
        String greet(String name) {
          return "\\${literal}: ${name}"
        }
        """);
  }

  @Test
  void formatsModuleConfigurationAsSourceCode() {
    SourceFile source =
        SourceFile.of(
            Path.of("module.norm"),
            "Module module(){return"
                + " module(name:\"sample\",version:1,exports:[\"api.Names\",\"model.User\"])}");

    assertEquals(
        """
        Module module() {
          return module(name: "sample", version: 1, exports: ["api.Names", "model.User"])
        }
        """,
        formatter.format(source).orElseThrow());
  }

  @Test
  void returnsNoResultForInvalidSource() {
    SourceFile source = SourceFile.of(Path.of("invalid.norm"), "main( {");

    assertTrue(formatter.format(source).isEmpty());
  }

  @Test
  void formattingIsIdempotent() {
    SourceFile source =
        SourceFile.of(
            Path.of("stable.norm"),
            "main(){Map<String,Integer> values=Map<>() printLine((1+2)*3) printLine(1-(2-3))}");
    String formatted = formatter.format(source).orElseThrow();

    assertTrue(formatted.contains("Map<String, Integer> values"));
    assertTrue(formatted.contains("(1 + 2) * 3"));
    assertTrue(formatted.contains("1 - (2 - 3)"));
    assertEquals(
        formatted,
        formatter.format(SourceFile.of(Path.of("stable.norm"), formatted)).orElseThrow());
  }

  private void assertFormats(String source, String expected) {
    assertEquals(
        expected, formatter.format(SourceFile.of(Path.of("format.norm"), source)).orElseThrow());
  }
}
