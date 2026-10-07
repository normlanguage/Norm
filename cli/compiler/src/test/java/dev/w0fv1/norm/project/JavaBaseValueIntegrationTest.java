package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.application.ApplicationRunner;
import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaBaseValueIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void executesCanonicalDateTimeAndDecimalValues() throws Exception {
    var entry =
        Files.writeString(
            temporaryDirectory.resolve("Main.norm"),
            """
        import java.base.lang.Record
            import java.base.lang.Cloneable
            import java.base.util.EventListener
            import java.base.util.EventObject
            import java.base.util.eventObjectNew
        import java.base.time.LocalDate
        import java.base.time.LocalTime
        import java.base.time.YearMonth
        import java.base.time.localDateOf
        import java.base.time.localTimeOf
        import java.base.time.yearMonthOf
        import java.base.math.BigDecimal
        import java.base.math.bigDecimalNew

        Void eventContracts(Cloneable copyable, EventListener listener) {}

            String recordText(Record value) { value.toString()!! }

        Void main() {
          LocalDate date = localDateOf(arg0: 2024, arg1: 2, arg2: 29)!!
          printLine(date.plusDays(arg0: 1)!!.toString()!!)
          LocalTime time = localTimeOf(arg0: 23, arg1: 59)!!
          printLine(time.plusMinutes(arg0: 2)!!.toString()!!)
          YearMonth month = yearMonthOf(arg0: 2024, arg1: 2)!!
          printLine(month.lengthOfMonth())
          printLine(month.atDay(arg0: 29)!!.toString()!!)
          BigDecimal amount = bigDecimalNew(arg0: "0.10")
          printLine(amount.add(arg0: bigDecimalNew(arg0: "0.20"))!!.toPlainString()!!)
          printLine(amount.compareTo(arg0: bigDecimalNew(arg0: "0.1")))
              EventObject event = eventObjectNew(arg0: "change")
              printLine(event.getSource())
        }
        """);
    var runtime = new NormRuntime();
    var output = new StringWriter();
    try (var environment = ProjectEnvironment.bootstrap(runtime);
        var projects = environment.projectLoader(temporaryDirectory.resolve("cache"));
        var runner = new ApplicationRunner(projects, environment.compilerSession(), runtime)) {
      var result = runner.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals(
        String.join(
            System.lineSeparator(),
            "2024-03-01",
            "00:01",
            "29",
            "2024-02-29",
            "0.30",
            "0",
            "change",
            ""),
        output.toString());
  }
}
