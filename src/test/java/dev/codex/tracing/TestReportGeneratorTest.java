package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestReportGeneratorTest {
  @TempDir
  Path tempDir;

  @Test
  void rendersSuitesTileAndCompactSummaryLayout() throws Exception {
    Path surefire = Files.createDirectories(tempDir.resolve("target").resolve("surefire-reports"));
    Path failsafe = Files.createDirectories(tempDir.resolve("target").resolve("failsafe-reports"));

    Files.writeString(
        surefire.resolve("TEST-dev.codex.tracing.UnitSuite.xml"),
        """
            <testsuite name="dev.codex.tracing.UnitSuite" tests="2" failures="0" errors="0" skipped="0">
              <testcase classname="dev.codex.tracing.UnitSuite" name="passesOne" time="0.01"/>
              <testcase classname="dev.codex.tracing.UnitSuite" name="passesTwo" time="0.02"/>
            </testsuite>
            """);

    Files.writeString(
        failsafe.resolve("TEST-dev.codex.tracing.SystemSuite.xml"),
        """
            <testsuite name="dev.codex.tracing.SystemSuite" tests="2" failures="1" errors="0" skipped="0">
              <testcase classname="dev.codex.tracing.SystemSuite" name="passes" time="0.03"/>
              <testcase classname="dev.codex.tracing.SystemSuite" name="fails" time="0.04">
                <failure message="boom">stack</failure>
              </testcase>
            </testsuite>
            """);

    dev.codex.tracing.report.TestReportGenerator.main(new String[]{tempDir.toString()});

    String html = Files.readString(tempDir.resolve("target").resolve("test-report").resolve("index.html"));

    assertTrue(html.contains("summary-layout"));
    assertTrue(html.contains("run-card"));
    assertTrue(html.contains("report-board"));
    assertTrue(html.contains("<h2>Suites &amp; Tests</h2>"));
    assertTrue(html.contains("UnitSuite"));
    assertTrue(html.contains("SystemSuite"));
    assertTrue(html.contains("suite-progress"));
    assertTrue(html.contains("suite-item"));
    assertTrue(html.contains("suite-test"));
    assertTrue(html.contains("No trace"));
    assertTrue(!html.contains("case-dev.codex.tracing.SystemSuite.fails"));
    assertTrue(html.contains("2 suites"));
  }
}
