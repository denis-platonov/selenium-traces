package dev.codex.tracing.report;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class TestReportGenerator {
  private static final Pattern TRACE_TIMESTAMP_SUFFIX = Pattern.compile("-\\d{8}-\\d{6}-\\d{3}$");
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss z", Locale.ROOT).withZone(ZoneId.systemDefault());

  private TestReportGenerator() {
  }

  public static void main(String[] args) throws Exception {
    Path root = Paths.get(args.length > 0 ? args[0] : ".");
    Path target = root.resolve("target");
    Path outputDir = target.resolve("test-report");
    Files.createDirectories(outputDir);

    List<TestCaseResult> cases = new ArrayList<>();
    cases.addAll(readSuiteReports(target.resolve("surefire-reports"), "unit"));
    cases.addAll(readSuiteReports(target.resolve("failsafe-reports"), "system"));

    Map<String, Path> traces = indexLatestTraces(target.resolve("selenide-traces"));
    String html = renderHtml(cases, traces, outputDir);
    Files.writeString(outputDir.resolve("index.html"), html);
  }

  private static List<TestCaseResult> readSuiteReports(Path reportsDir, String phase) throws Exception {
    if (!Files.isDirectory(reportsDir)) {
      return List.of();
    }

    List<Path> reportFiles;
    try (Stream<Path> stream = Files.list(reportsDir)) {
      reportFiles = stream
          .filter(path -> path.getFileName().toString().startsWith("TEST-"))
          .filter(path -> path.getFileName().toString().endsWith(".xml"))
          .sorted()
          .toList();
    }

    List<TestCaseResult> results = new ArrayList<>();
    for (Path reportFile : reportFiles) {
      Document document = parseXml(reportFile);
      Element suite = document.getDocumentElement();
      NodeList casesNodes = suite.getElementsByTagName("testcase");
      for (int index = 0; index < casesNodes.getLength(); index += 1) {
        Element testCase = (Element) casesNodes.item(index);
        results.add(readTestCase(phase, suite, testCase));
      }
    }
    return results;
  }

  private static TestCaseResult readTestCase(String phase, Element suite, Element testCase) {
    String suiteName = suite.getAttribute("name");
    String className = testCase.getAttribute("classname");
    String name = testCase.getAttribute("name");
    String key = className + "." + name;
    String status = "passed";
    String details = "";

    NodeList failureNodes = testCase.getElementsByTagName("failure");
    NodeList errorNodes = testCase.getElementsByTagName("error");
    NodeList skippedNodes = testCase.getElementsByTagName("skipped");
    if (failureNodes.getLength() > 0) {
      status = "failed";
      details = textContent((Element) failureNodes.item(0));
    } else if (errorNodes.getLength() > 0) {
      status = "error";
      details = textContent((Element) errorNodes.item(0));
    } else if (skippedNodes.getLength() > 0) {
      status = "skipped";
      details = textContent((Element) skippedNodes.item(0));
    }

    return new TestCaseResult(
        phase,
        suiteName,
        className,
        name,
        key,
        status,
        parseDouble(testCase.getAttribute("time")),
        details);
  }

  private static Map<String, Path> indexLatestTraces(Path traceRoot) throws IOException {
    if (!Files.isDirectory(traceRoot)) {
      return Map.of();
    }

    Map<String, Path> traces = new LinkedHashMap<>();
    try (Stream<Path> stream = Files.list(traceRoot)) {
      stream
          .filter(Files::isDirectory)
          .filter(path -> Files.exists(path.resolve("index.html")))
          .sorted(Comparator.comparing(TestReportGenerator::lastModified).reversed())
          .forEach(path -> traceKeys(path).forEach(key -> traces.putIfAbsent(key, path)));
    }
    return traces;
  }

  private static List<String> traceKeys(Path traceDir) {
    String name = traceDir.getFileName().toString();
    String base = TRACE_TIMESTAMP_SUFFIX.matcher(name).replaceFirst("");
    if (base.equals(name) || base.isBlank()) {
      return List.of(name);
    }
    LinkedHashSet<String> keys = new LinkedHashSet<>();
    keys.add(base);
    int firstDot = base.indexOf('.');
    if (firstDot > 0 && firstDot < base.length() - 1) {
      keys.add(base.substring(firstDot + 1));
    }
    return List.copyOf(keys);
  }

  private static String renderHtml(List<TestCaseResult> cases, Map<String, Path> traces, Path outputDir) {
    long passed = cases.stream().filter(testCase -> "passed".equals(testCase.status())).count();
    long failed = cases.stream().filter(testCase -> "failed".equals(testCase.status())).count();
    long errored = cases.stream().filter(testCase -> "error".equals(testCase.status())).count();
    long skipped = cases.stream().filter(testCase -> "skipped".equals(testCase.status())).count();
    long total = cases.size();
    String pieChart = renderPieChart(total, passed, failed, errored, skipped);
    String suitesTile = renderSuitesTile(summarizeSuites(cases), traces, outputDir);

    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Test Report</title>
          <style>
            :root {
              color-scheme: dark;
              --bg: #15171b;
              --panel: #1f232a;
              --panel-2: #252a33;
              --line: #343b46;
              --text: #ebeff5;
              --muted: #98a2b3;
              --green: #2dc26b;
              --red: #ff6b6b;
              --amber: #ffb454;
              --blue: #4ea1ff;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              background: var(--bg);
              color: var(--text);
              font: 14px/1.5 "Segoe UI", system-ui, sans-serif;
            }
            .shell {
              max-width: 1400px;
              margin: 0 auto;
              padding: 24px;
            }
            .summary, .report-board {
              background: var(--panel);
              border: 1px solid var(--line);
              border-radius: 14px;
            }
            .summary {
              display: block;
              padding: 16px;
              margin-bottom: 16px;
            }
            .summary-layout {
              display: grid;
              grid-template-columns: minmax(360px, 1fr) minmax(300px, 360px);
              gap: 16px;
              align-items: stretch;
            }
            .run-card {
              display: grid;
              align-content: start;
              gap: 14px;
            }
            .run-card h1 {
              margin: 0;
              font-size: 28px;
            }
            .run-card p {
              margin: 0;
              color: var(--muted);
            }
            .run-metrics {
              display: grid;
              grid-template-columns: repeat(2, minmax(0, 1fr));
              gap: 10px;
            }
            .metric {
              background: #20252d;
              border: 1px solid var(--line);
              border-radius: 12px;
              padding: 12px;
            }
            .metric label {
              display: block;
              color: var(--muted);
              font-size: 11px;
              text-transform: uppercase;
              letter-spacing: 0.08em;
              margin-bottom: 6px;
            }
            .metric strong {
              font-size: 20px;
            }
            .overview-card {
              display: grid;
              grid-template-columns: minmax(150px, 190px) minmax(110px, 130px);
              gap: 10px;
              align-items: center;
              justify-content: start;
              overflow: hidden;
            }
            .chart-wrap {
              width: 170px;
              max-width: 100%;
            }
            .chart-wrap svg {
              width: 100%;
              height: auto;
              display: block;
            }
            .chart-legend {
              width: 100%;
              display: grid;
              gap: 4px;
              min-width: 0;
            }
            .chart-legend .legend-row {
              display: grid;
              grid-template-columns: auto 1fr auto;
              gap: 6px;
              align-items: center;
              color: var(--muted);
              font-size: 11px;
              min-width: 0;
            }
            .chart-legend .legend-row span:nth-child(2) {
              min-width: 0;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
            }
            .chart-legend .legend-row strong {
              font-size: 13px;
            }
            .swatch {
              width: 8px;
              height: 8px;
              border-radius: 999px;
            }
            .card {
              background: var(--panel-2);
              border: 1px solid var(--line);
              border-radius: 12px;
              padding: 12px;
            }
            .card label {
              display: block;
              color: var(--muted);
              font-size: 12px;
              text-transform: uppercase;
              letter-spacing: 0.08em;
              margin-bottom: 6px;
            }
            .card strong {
              font-size: 24px;
            }
            .tile-header {
              display: flex;
              align-items: center;
              justify-content: space-between;
              gap: 12px;
              margin-bottom: 12px;
            }
            .tile-header h2 {
              margin: 0;
              font-size: 16px;
            }
            .tile-header span {
              color: var(--muted);
              font-size: 12px;
            }
            .suites-card {
              display: grid;
              align-content: start;
            }
            .suite-list {
              display: grid;
              gap: 10px;
            }
            .suite-item {
              border: 1px solid var(--line);
              border-radius: 12px;
              overflow: hidden;
              background: #21262f;
            }
            .suite-summary {
              list-style: none;
              cursor: pointer;
              padding: 12px;
              display: grid;
              grid-template-columns: minmax(0, 1fr) 160px auto 24px;
              gap: 12px;
              align-items: center;
            }
            .suite-summary::-webkit-details-marker { display: none; }
            .suite-summary::after {
              content: "^";
              display: inline-flex;
              align-items: center;
              justify-content: center;
              width: 24px;
              height: 24px;
              border: 1px solid var(--line);
              border-radius: 999px;
              color: var(--muted);
              font-size: 12px;
              line-height: 1;
              justify-self: end;
              transform: rotate(180deg);
              transition: transform 120ms ease, border-color 120ms ease, color 120ms ease;
            }
            .suite-item[open] .suite-summary::after {
              transform: rotate(0deg);
              color: var(--blue);
              border-color: #456289;
            }
            .suite-name {
              display: flex;
              align-items: baseline;
              gap: 8px;
              min-width: 0;
            }
            .suite-name strong {
              font-size: 13px;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }
            .suite-phase {
              color: var(--blue);
              text-transform: uppercase;
              font-size: 11px;
              letter-spacing: 0.06em;
            }
            .suite-meta {
              color: var(--muted);
              font-size: 10px;
            }
            .suite-progress {
              display: flex;
              height: 10px;
              overflow: hidden;
              border-radius: 999px;
              background: #2b3039;
              border: 1px solid #353b45;
            }
            .suite-segment {
              height: 100%;
            }
            .suite-segment.passed { background: var(--green); }
            .suite-segment.failed { background: var(--red); }
            .suite-segment.error { background: #ff8f6b; }
            .suite-segment.skipped { background: var(--amber); }
            .suite-score {
              color: var(--muted);
              font-size: 12px;
              white-space: nowrap;
            }
            .suite-tests {
              display: grid;
              gap: 8px;
              padding: 0 12px 12px;
              border-top: 1px solid var(--line);
            }
            .suite-test {
              display: grid;
              grid-template-columns: auto minmax(0, 1fr) auto;
              gap: 10px;
              align-items: center;
              padding: 10px 12px;
              border-radius: 10px;
              background: #1b1f26;
              border: 1px solid #313844;
              color: inherit;
              text-decoration: none;
            }
            .suite-test:hover {
              border-color: #4a5565;
              background: #20262f;
            }
            .suite-test-name {
              min-width: 0;
              overflow: hidden;
              text-overflow: ellipsis;
              white-space: nowrap;
              color: var(--text);
              font-size: 12px;
            }
            .suite-test-time {
              color: var(--muted);
              font-size: 11px;
              white-space: nowrap;
            }
            .report-board {
              padding: 16px;
            }
            .badge {
              display: inline-flex;
              justify-content: center;
              padding: 4px 8px;
              border-radius: 999px;
              border: 1px solid var(--line);
              text-transform: uppercase;
              font-size: 12px;
              font-weight: 700;
            }
            .passed { color: var(--green); }
            .failed, .error { color: var(--red); }
            .skipped { color: var(--amber); }
            .phase { color: var(--blue); }
            @media (max-width: 1100px) {
              .summary-layout { grid-template-columns: 1fr; }
              .run-metrics { grid-template-columns: 1fr 1fr; }
              .overview-card { grid-template-columns: 1fr; justify-items: center; }
              .chart-legend { width: 100%; }
              .suite-summary { grid-template-columns: 1fr 24px; }
              .suite-test { grid-template-columns: 1fr; }
            }
          </style>
        </head>
        <body>
          <div class="shell">
            <section class="summary">
              <div class="summary-layout">
                <div class="card run-card">
                  <div>
                    <h1>Test Report</h1>
                    <p>Generated __GENERATED__</p>
                  </div>
                  <div class="run-metrics">
                    <div class="metric"><label>Run Name</label><strong>verify</strong></div>
                    <div class="metric"><label>Suites</label><strong>__SUITE_COUNT__</strong></div>
                    <div class="metric"><label>Tests</label><strong>__TOTAL__</strong></div>
                    <div class="metric"><label>Status</label><strong class="__RUN_STATUS_CLASS__">__RUN_STATUS__</strong></div>
                  </div>
                </div>
                <div class="card overview-card">
                  <div class="chart-wrap">__PIE__</div>
                  <div class="chart-legend">
                    <div class="legend-row"><span class="swatch" style="background: #7b8495;"></span><span>Total</span><strong>__TOTAL__</strong></div>
                    <div class="legend-row"><span class="swatch" style="background: var(--green);"></span><span>Passed</span><strong class="passed">__PASSED__</strong></div>
                    <div class="legend-row"><span class="swatch" style="background: var(--red);"></span><span>Failed</span><strong class="failed">__FAILED__</strong></div>
                    <div class="legend-row"><span class="swatch" style="background: #ff8f6b;"></span><span>Errors</span><strong class="failed">__ERRORED__</strong></div>
                    <div class="legend-row"><span class="swatch" style="background: var(--amber);"></span><span>Skipped</span><strong class="skipped">__SKIPPED__</strong></div>
                  </div>
                </div>
              </div>
            </section>
            <section class="report-board">
              __SUITES_TILE__
            </section>
          </div>
        </body>
        </html>
        """
        .replace("__GENERATED__", escapeHtml(TIME_FORMAT.format(Instant.now())))
        .replace("__PIE__", pieChart)
        .replace("__SUITE_COUNT__", String.valueOf(summarizeSuites(cases).size()))
        .replace("__RUN_STATUS__", failed > 0 || errored > 0 ? "failed" : skipped > 0 ? "partial" : "passed")
        .replace("__RUN_STATUS_CLASS__", failed > 0 || errored > 0 ? "failed" : skipped > 0 ? "skipped" : "passed")
        .replace("__TOTAL__", String.valueOf(total))
        .replace("__PASSED__", String.valueOf(passed))
        .replace("__FAILED__", String.valueOf(failed))
        .replace("__ERRORED__", String.valueOf(errored))
        .replace("__SKIPPED__", String.valueOf(skipped))
        .replace("__SUITES_TILE__", suitesTile);
  }

  private static String renderPieChart(long total, long passed, long failed, long errored, long skipped) {
    double circumference = 2 * Math.PI * 54.0d;
    List<ChartSlice> slices = List.of(
        new ChartSlice("Passed", passed, "#2dc26b"),
        new ChartSlice("Failed", failed, "#ff6b6b"),
        new ChartSlice("Errors", errored, "#ff8f6b"),
        new ChartSlice("Skipped", skipped, "#ffb454"));

    double offset = 0.0d;
    StringBuilder segments = new StringBuilder();
    for (ChartSlice slice : slices) {
      if (total == 0 || slice.count() == 0) {
        continue;
      }
      double length = circumference * ((double) slice.count() / (double) total);
      segments.append("""
          <circle cx="80" cy="80" r="54" fill="none" stroke="%s" stroke-width="24" stroke-linecap="butt"
                  stroke-dasharray="%.3f %.3f" stroke-dashoffset="-%.3f"
                  transform="rotate(-90 80 80)"></circle>
          """.formatted(slice.color(), length, Math.max(circumference - length, 0.0d), offset));
      offset += length;
    }

    return """
        <svg viewBox="0 0 160 160" role="img" aria-label="Test result distribution">
          <circle cx="80" cy="80" r="54" fill="none" stroke="#2b3039" stroke-width="24"></circle>
          %s
          <circle cx="80" cy="80" r="38" fill="#1f232a"></circle>
          <text x="80" y="75" text-anchor="middle" fill="#98a2b3" font-size="11" font-family="Segoe UI, system-ui, sans-serif">TOTAL</text>
          <text x="80" y="95" text-anchor="middle" fill="#ebeff5" font-size="24" font-weight="700" font-family="Segoe UI, system-ui, sans-serif">%d</text>
        </svg>
        """.formatted(segments, total);
  }

  private static List<SuiteSummary> summarizeSuites(List<TestCaseResult> cases) {
    return cases.stream()
        .collect(Collectors.groupingBy(
            testCase -> testCase.phase() + "|" + simpleName(testCase.className()),
            LinkedHashMap::new,
            Collectors.toList()))
        .entrySet()
        .stream()
        .map(entry -> {
          List<TestCaseResult> suiteCases = entry.getValue();
          TestCaseResult first = suiteCases.get(0);
          long suitePassed = suiteCases.stream().filter(testCase -> "passed".equals(testCase.status())).count();
          long suiteFailed = suiteCases.stream().filter(testCase -> "failed".equals(testCase.status())).count();
          long suiteErrored = suiteCases.stream().filter(testCase -> "error".equals(testCase.status())).count();
          long suiteSkipped = suiteCases.stream().filter(testCase -> "skipped".equals(testCase.status())).count();
          return new SuiteSummary(
              first.phase(),
              simpleName(first.className()),
              suiteCases.size(),
              suitePassed,
              suiteFailed,
              suiteErrored,
              suiteSkipped,
              suiteCases.stream()
                  .sorted(Comparator.comparing(TestCaseResult::key))
                  .toList());
        })
        .sorted(Comparator.comparing(SuiteSummary::phase).thenComparing(SuiteSummary::name))
        .toList();
  }

  private static String renderSuitesTile(List<SuiteSummary> suites, Map<String, Path> traces, Path outputDir) {
    String rows = suites.stream()
        .map(suite -> renderSuiteRow(suite, traces, outputDir))
        .collect(Collectors.joining());
    return """
        <div class="card suites-card" style="margin-bottom: 16px;">
          <div class="tile-header">
            <h2>Suites &amp; Tests</h2>
            <span>%d suites</span>
          </div>
          <div class="suite-list">%s</div>
        </div>
        """.formatted(suites.size(), rows);
  }

  private static String renderSuiteRow(SuiteSummary suite, Map<String, Path> traces, Path outputDir) {
    long total = suite.total();
    String progress = renderSuiteProgressSegment(suite.passed(), total, "passed")
        + renderSuiteProgressSegment(suite.failed(), total, "failed")
        + renderSuiteProgressSegment(suite.errored(), total, "error")
        + renderSuiteProgressSegment(suite.skipped(), total, "skipped");
    String meta = "%d tests | %d pass | %d fail".formatted(total, suite.passed(), suite.failed() + suite.errored());
    String tests = suite.testCases().stream()
        .map(testCase -> renderSuiteTestLink(testCase, traces, outputDir))
        .collect(Collectors.joining());
    return """
        <details class="suite-item">
          <summary class="suite-summary">
            <div>
              <div class="suite-name">
                <strong>%s</strong>
                <span class="suite-phase">%s</span>
              </div>
              <div class="suite-meta">%s</div>
            </div>
            <div class="suite-progress">%s</div>
            <div class="suite-score">%d/%d</div>
          </summary>
          <div class="suite-tests">%s</div>
        </details>
        """.formatted(
        escapeHtml(suite.name()),
        escapeHtml(suite.phase()),
        escapeHtml(meta),
        progress,
        suite.passed(),
        total,
        tests);
  }

  private static String renderSuiteTestLink(TestCaseResult testCase, Map<String, Path> traces, Path outputDir) {
    Path traceDir = resolveTrace(testCase, traces);
    String href = "#";
    String target = "";
    String rel = "";
    String traceState = "No trace";
    if (traceDir != null) {
      Path reportPath = outputDir.resolve("index.html").getParent();
      href = reportPath.relativize(traceDir.resolve("index.html")).toString().replace('\\', '/');
      target = " target=\"_blank\"";
      rel = " rel=\"noreferrer\"";
      traceState = "Open trace";
    }
    return """
        <a class="suite-test" href="%s"%s%s>
          <span class="badge %s">%s</span>
          <span class="suite-test-name">%s</span>
          <span class="suite-test-time">%.3fs</span>
        </a>
        """.formatted(
        escapeHtml(href),
        target,
        rel,
        testCase.status(),
        escapeHtml(testCase.status()),
        escapeHtml(testCase.name() + " | " + traceState),
        testCase.timeSeconds());
  }

  private static String renderSuiteProgressSegment(long count, long total, String status) {
    if (count <= 0 || total <= 0) {
      return "";
    }
    double width = (100.0d * (double) count) / (double) total;
    return "<span class=\"suite-segment " + status + "\" style=\"width: %.2f%%\"></span>".formatted(width);
  }

  private static Path resolveTrace(TestCaseResult testCase, Map<String, Path> traces) {
    Path direct = traces.get(testCase.key());
    if (direct != null) {
      return direct;
    }
    String simpleClassName = testCase.className().contains(".")
        ? testCase.className().substring(testCase.className().lastIndexOf('.') + 1)
        : testCase.className();
    return traces.get(simpleClassName + "." + testCase.name());
  }

  private static Document parseXml(Path xmlPath) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    return factory.newDocumentBuilder().parse(xmlPath.toFile());
  }

  private static String textContent(Element element) {
    StringWriter writer = new StringWriter();
    writer.append(Optional.ofNullable(element.getAttribute("message")).orElse(""));
    String text = element.getTextContent();
    if (text != null && !text.isBlank()) {
      if (writer.getBuffer().length() > 0) {
        writer.append(System.lineSeparator()).append(System.lineSeparator());
      }
      writer.append(text.trim());
    }
    return writer.toString();
  }

  private static double parseDouble(String value) {
    if (value == null || value.isBlank()) {
      return 0.0d;
    }
    return Double.parseDouble(value);
  }

  private static Instant lastModified(Path path) {
    try {
      return Files.getLastModifiedTime(path).toInstant();
    } catch (IOException exception) {
      return Instant.EPOCH;
    }
  }

  private static String escapeHtml(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  private static String simpleName(String className) {
    int lastDot = className.lastIndexOf('.');
    return lastDot >= 0 ? className.substring(lastDot + 1) : className;
  }

  private record TestCaseResult(
      String phase,
      String suite,
      String className,
      String name,
      String key,
      String status,
      double timeSeconds,
      String details
  ) {
  }

  private record ChartSlice(
      String label,
      long count,
      String color
  ) {
  }

  private record SuiteSummary(
      String phase,
      String name,
      long total,
      long passed,
      long failed,
      long errored,
      long skipped,
      List<TestCaseResult> testCases
  ) {
  }
}
