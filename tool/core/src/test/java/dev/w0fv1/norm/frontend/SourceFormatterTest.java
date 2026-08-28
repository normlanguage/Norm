package dev.w0fv1.norm.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.value.SourceFile;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class SourceFormatterTest {
  private final SourceFormatter formatter = new SourceFormatter();

  @Test
  void formatsDeclarationsAndOmitsDefaultPublicVisibility() {
    assertFormats(
        "public class Box{private Integer value public Integer get(){return value}}public main(){Box box=Box(value:1) printLine(box.get())}",
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
  void formatsInheritanceAndConstructors() {
    assertFormats(
        "class Child extends Base<String>{Integer rank Child(String name,Integer value){super(name:name) rank=value}}",
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
        "Void replace(ref<Integer> target,Integer value){*target=value}main(){Integer value=1 ref<Integer> location=&value\n*location=2 replace(target:location,value:*location+1)}",
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
        "annotation Label implements TypeTarget,RuntimeRetention{String text String? replacement}@Label(text:\"point\",replacement:null)value Point{@Label(text:\"x\",replacement:null)Integer x}Void main(){Label? label=reflect<Point>().annotation<Label>()}",
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
          Label? label = reflect<Point>().annotation<Label>()
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
  void formatsModuleConfigurationAsSourceCode() {
    SourceFile source =
        SourceFile.of(
            Path.of("module.norm"),
            "Module module(){return module(name:\"sample\",version:1,exports:[\"api.Names\",\"model.User\"])}");

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
