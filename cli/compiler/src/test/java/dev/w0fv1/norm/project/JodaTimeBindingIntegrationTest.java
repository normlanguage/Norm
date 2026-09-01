package dev.w0fv1.norm.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.w0fv1.norm.execution.ExecutionContext;
import dev.w0fv1.norm.runtime.NormRuntime;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JodaTimeBindingIntegrationTest {
  @TempDir Path temporaryDirectory;

  @Test
  void consumesTheJodaTimeNarForImmutableDateTimeArithmeticZonesIntervalsAndFormatting()
      throws Exception {
    Path workspace = Path.of("").toAbsolutePath().normalize();
    while (workspace != null && !Files.isDirectory(workspace.resolve("java-binding/joda-time"))) {
      workspace = workspace.getParent();
    }
    assertTrue(workspace != null, "workspace root is unavailable");
    Path module = workspace.resolve("java-binding/joda-time/joda/time/module.norm");
    Path repository = temporaryDirectory.resolve("repository");
    Path jodaArtifact = Files.createDirectories(repository.resolve("joda-time/joda-time/2.14.3"));
    Files.copy(
        workspace.resolve("java-binding/joda-time/joda/time/lib/joda-time-2.14.3.jar"),
        jodaArtifact.resolve("joda-time-2.14.3.jar"));
    Files.writeString(
        jodaArtifact.resolve("joda-time-2.14.3.pom"),
        """
        <project xmlns="http://maven.apache.org/POM/4.0.0">
          <modelVersion>4.0.0</modelVersion>
          <groupId>joda-time</groupId>
          <artifactId>joda-time</artifactId>
          <version>2.14.3</version>
        </project>
        """);
    NormRuntime backend = new NormRuntime();
    ProjectEnvironment environment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLoader projects = environment.projectLoader(repository)) {
      new ModulePackager(projects).packageModule(module, repository);
    }

    Path app = Files.createDirectories(temporaryDirectory.resolve("app"));
    Path entry = app.resolve("Main.norm");
    Files.writeString(
        app.resolve("module.norm"),
        """
        Module module() {
          return module(
            name: "app",
            version: 1,
            exports: ["Main"],
            dependencies: [dependency(name: "joda.time", version: 1)]
          )
        }
        """);
    Files.writeString(
        entry,
        """
        package app

        import joda.time.DateTime
        import joda.time.DateTimeFormatter
        import joda.time.DateTimeZone
        import joda.time.Duration
        import joda.time.Days
        import joda.time.Instant
        import joda.time.Interval
        import joda.time.LocalDate
        import joda.time.LocalDateTime
        import joda.time.LocalTime
        import joda.time.dateTimeFormatForPattern
        import joda.time.dateTimeParse
        import joda.time.dateTimeZoneForID
        import joda.time.daysDaysBetween
        import joda.time.durationStandardMinutes
        import joda.time.instantOfEpochSecond
        import joda.time.intervalNew
        import joda.time.localDateParse
        import joda.time.localDateTimeParse
        import joda.time.localTimeParse
        import joda.time.periodDays

        Void main() {
          DateTime? instant = dateTimeParse("2024-02-29T23:45:30.000Z")
          if instant != null {
            printLine(instant.getYear())
            printLine(instant.getMonthOfYear())
            printLine(instant.getDayOfMonth())
            DateTime? tomorrow = instant.plusDays(1)
            if tomorrow != null {
              printLine(tomorrow.toString() ?? "missing")
            }
            DateTimeZone? london = dateTimeZoneForID("Europe/London")
            if london != null {
              DateTime? zoned = instant.withZone(london)
              if zoned != null {
                printLine(zoned.getZone()?.getID() ?? "missing")
              }
            }
            DateTimeFormatter? formatter = dateTimeFormatForPattern("yyyy-MM-dd HH:mm")
            if formatter != null {
              printLine(formatter.print(instant) ?? "missing")
            }
            Interval? day = intervalNew(
              arg0: instant.getMillis(),
              arg1: instant.plusDays(1)?.getMillis() ?? 0
            )
            if day != null {
              printLine(day.contains(instant.getMillis()))
              printLine(day.toDurationMillis())
            }
          }
          LocalDate? leapDay = localDateParse("2024-02-29")
          if leapDay != null {
            printLine(leapDay.plusDays(1)?.toString() ?? "missing")
            printLine(leapDay.getDayOfWeek())
            Days? tenDays = daysDaysBetween(
              arg0: leapDay,
              arg1: leapDay.plusDays(10)
            )
            printLine(tenDays?.getDays() ?? 0)
          }
          LocalDateTime? localDateTime = localDateTimeParse("2024-02-29T23:45:30")
          printLine(localDateTime?.plusMinutes(15)?.toString() ?? "missing")
          LocalTime? localTime = localTimeParse("23:50:00")
          printLine(localTime?.plusMinutes(15)?.toString() ?? "missing")
          Instant? epoch = instantOfEpochSecond(0)
          printLine(epoch?.getMillis() ?? -1)
          Duration? duration = durationStandardMinutes(90)
          if duration != null {
            printLine(duration.getStandardSeconds())
          }
          printLine(periodDays(3)?.plusHours(2)?.getHours() ?? 0)
        }
        """);
    StringWriter output = new StringWriter();
    ProjectEnvironment consumerEnvironment = ProjectEnvironment.bootstrap(backend);
    try (ProjectLauncher launcher =
        new ProjectLauncher(
            consumerEnvironment.projectLoader(repository),
            consumerEnvironment.compilerSession(),
            backend)) {
      var result = launcher.run(entry, ExecutionContext.of(new PrintWriter(output)));
      assertTrue(result.isSuccess(), () -> result.diagnostics().toString());
    }
    assertEquals(
        String.join(
            System.lineSeparator(),
            "2024",
            "2",
            "29",
            "2024-03-01T23:45:30.000Z",
            "Europe/London",
            "2024-02-29 23:45",
            "true",
            "86400000",
            "2024-03-01",
            "4",
            "10",
            "2024-03-01T00:00:30.000",
            "00:05:00.000",
            "0",
            "5400",
            "2",
            ""),
        output.toString());
  }
}
