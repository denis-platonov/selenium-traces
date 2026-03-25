package dev.codex.tracing;

import com.codeborne.selenide.WebDriverRunner;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.logging.LogEntries;
import org.openqa.selenium.logging.LogEntry;

final class SelenideTraceSession {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };
  private static final Pattern HEAD_OPEN_TAG = Pattern.compile("(?i)<head([^>]*)>");
  private static final Pattern HTML_OPEN_TAG = Pattern.compile("(?i)<html([^>]*)>");
  private static final Pattern CSP_META_TAG = Pattern.compile(
      "(?i)<meta[^>]+http-equiv\\s*=\\s*(['\"])content-security-policy\\1[^>]*>");

  private final String testName;
  private final SelenideTraceConfig config;
  private final SelenideTraceWriter writer;
  private final Clock clock;
  private final WebDriver primaryDriver;
  private final SelenideTraceReportRenderer reportRenderer = new SelenideTraceReportRenderer();
  private final Deque<TraceStep> activeSteps = new ArrayDeque<>();
  private final List<SelenideTraceEvent> events = new ArrayList<>();
  private final List<SelenideTraceAttachment> pendingActionAttachments = new ArrayList<>();
  private final Map<String, NetworkCall> networkCalls = new LinkedHashMap<>();
  private final AtomicInteger resourceCounter = new AtomicInteger();

  SelenideTraceSession(String testName, SelenideTraceConfig config, Path sessionDirectory, WebDriver primaryDriver) {
    this.testName = testName;
    this.config = config;
    this.writer = new SelenideTraceWriter(sessionDirectory);
    this.clock = config.clock();
    this.primaryDriver = primaryDriver;
  }

  void start() {
    try {
      writer.initialize();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to initialize trace session", exception);
    }

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("testName", testName);
    data.put("startedAt", now().toString());
    data.put("traceFormat", "playwright-inspired");

    writeEvent(new SelenideTraceEvent(
        "test",
        testName,
        "started",
        epochMs(),
        epochMs(),
        0L,
        null,
        data,
        List.of()));
  }

  void recordSelenideStep(String stepName, String actionSubject, String actionElement, Throwable error, Long durationMs) {
    long startedAt = epochMs();
    long finishedAt = durationMs == null ? startedAt : startedAt + Math.max(durationMs, 0L);
    ArtifactBundle bundle = captureArtifacts("event-" + resourceCounter.incrementAndGet());
    List<SelenideTraceAttachment> attachments = new ArrayList<>(drainPendingActionAttachments());
    attachments.addAll(bundle.attachments());

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("source", "selenide");
    data.put("url", currentUrl().orElse(null));
    data.put("title", currentTitle().orElse(null));
    data.put("subject", actionSubject);
    data.put("element", actionElement);
    data.put("networkCalls", bundle.networkCalls());

    writeEvent(new SelenideTraceEvent(
        "action",
        stepName,
        error == null ? "passed" : "failed",
        startedAt,
        finishedAt,
        Math.max(finishedAt - startedAt, 0L),
        stackTrace(error),
        data,
        attachments));
  }

  void recordExternalStep(String stepName, String actionSubject, String actionElement, Throwable error, Long durationMs, WebDriver driver) {
    long finishedAt = epochMs();
    long startedAt = finishedAt - Math.max(durationMs == null ? 0L : durationMs, 0L);
    ArtifactBundle bundle = captureArtifacts("event-" + resourceCounter.incrementAndGet(), driver);
    List<SelenideTraceAttachment> attachments = new ArrayList<>(drainPendingActionAttachments());
    attachments.addAll(bundle.attachments());

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("source", actionSubject);
    data.put("url", currentUrl(driver).orElse(null));
    data.put("title", currentTitle(driver).orElse(null));
    data.put("element", actionElement);
    data.put("networkCalls", bundle.networkCalls());

    writeEvent(new SelenideTraceEvent(
        "action",
        stepName,
        error == null ? "passed" : "failed",
        startedAt,
        finishedAt,
        Math.max(finishedAt - startedAt, 0L),
        stackTrace(error),
        data,
        attachments));
  }

  void captureActionTarget(String actionSubject, String actionElement) {
    captureActionTarget(resolveDriver(), actionElement);
  }

  void captureActionTarget(WebDriver driver, Object actionTarget) {
    if (actionTarget == null) {
      return;
    }
    WebDriver activeDriver = driver == null ? resolveDriver() : driver;
    if (activeDriver == null) {
      return;
    }

    try {
      if (!(activeDriver instanceof JavascriptExecutor executor)) {
        return;
      }

      Object token = executor.executeScript(INJECT_TRACE_MARKER_SCRIPT, actionTarget);
      if (token == null) {
        return;
      }

      try {
        pendingActionAttachments.addAll(
            captureArtifacts("event-" + resourceCounter.incrementAndGet() + "-target", activeDriver).attachments());
      } finally {
        executor.executeScript(REMOVE_TRACE_MARKER_SCRIPT, token);
      }
    } catch (Exception ignored) {
      // Missing marker snapshots should never break test execution.
    }
  }

  TraceStep pushManualStep(String name) {
    TraceStep step = new TraceStep(name, epochMs());
    activeSteps.push(step);
    return step;
  }

  void completeManualStep(TraceStep step, Throwable error) {
    if (!activeSteps.remove(step)) {
      return;
    }

    long finishedAt = epochMs();
    ArtifactBundle bundle = captureArtifacts("manual-" + resourceCounter.incrementAndGet());
    writeEvent(new SelenideTraceEvent(
        "step",
        step.name(),
        error == null ? "passed" : "failed",
        step.startedAtEpochMs(),
        finishedAt,
        Math.max(finishedAt - step.startedAtEpochMs(), 0L),
        stackTrace(error),
        Map.of(
            "source", "manual",
            "networkCalls", bundle.networkCalls()),
        bundle.attachments()));
  }

  void finish(String status, Throwable error) {
    if (config.captureBrowserLogsOnFinish()) {
      captureBrowserLogs(resolveDriver());
    }

    ArtifactBundle bundle = captureArtifacts("final", resolveDriver());
    long finishedAt = epochMs();
    writeEvent(new SelenideTraceEvent(
        "test",
        testName,
        status,
        finishedAt,
        finishedAt,
        0L,
        stackTrace(error),
        Map.of(
            "finishedAt", now().toString(),
            "networkCalls", bundle.networkCalls()),
        bundle.attachments()));

    writer.writeTextResource("index.html", reportRenderer.render(testName, List.copyOf(events), clock.getZone()));
    Path zipPath = writer.directory().resolveSibling(writer.directory().getFileName() + ".zip");
    writer.zipTo(zipPath);
  }

  private ArtifactBundle captureArtifacts(String stem, WebDriver explicitDriver) {
    List<SelenideTraceAttachment> attachments = new ArrayList<>();
    List<Map<String, Object>> networkCalls = List.of();
    WebDriver driver = explicitDriver == null ? resolveDriver() : explicitDriver;
    if (driver == null) {
      return new ArtifactBundle(attachments, networkCalls);
    }

    try {
      if (config.captureScreenshotsOnEveryEvent()) {
        screenshot(driver, stem).ifPresent(attachments::add);
      }
      if (config.captureDomOnEveryEvent()) {
        domSnapshot(driver, stem).ifPresent(attachments::add);
      }
      networkCalls = drainNetworkCalls(driver, stem);
      if (!networkCalls.isEmpty()) {
        networkAttachment(stem, networkCalls).ifPresent(attachments::add);
      }
    } catch (Exception ignored) {
      // Trace capture must never hide the original test result.
    }
    return new ArtifactBundle(attachments, networkCalls);
  }

  private ArtifactBundle captureArtifacts(String stem) {
    return captureArtifacts(stem, null);
  }

  private Optional<SelenideTraceAttachment> screenshot(WebDriver driver, String stem) {
    if (!(driver instanceof TakesScreenshot screenshots)) {
      return Optional.empty();
    }

    byte[] content = screenshots.getScreenshotAs(OutputType.BYTES);
    String relativePath = "resources/" + stem + ".png";
    writer.writeResource(relativePath, content);
    return Optional.of(new SelenideTraceAttachment(stem + "-screenshot", relativePath, "image/png"));
  }

  private Optional<SelenideTraceAttachment> domSnapshot(WebDriver driver, String stem) {
    String relativePath = "resources/" + stem + ".html";
    writer.writeTextResource(relativePath, toRenderableHtml(driver.getPageSource(), safeCurrentUrl()));
    return Optional.of(new SelenideTraceAttachment(stem + "-dom", relativePath, "text/html"));
  }

  private Optional<SelenideTraceAttachment> networkAttachment(String stem, List<Map<String, Object>> calls) {
    if (calls == null || calls.isEmpty()) {
      return Optional.empty();
    }

    String relativePath = "resources/" + stem + "-network.json";
    writer.writeTextResource(relativePath, toJson(calls));
    return Optional.of(new SelenideTraceAttachment(
        stem + "-network",
        relativePath,
        "application/vnd.selenide-trace-network+json"));
  }

  private String toRenderableHtml(String pageSource, String currentUrl) {
    String html = CSP_META_TAG.matcher(pageSource).replaceAll("");
    String baseTag = "<base href=\"" + escapeHtmlAttribute(currentUrl) + "\">";

    if (HEAD_OPEN_TAG.matcher(html).find()) {
      return HEAD_OPEN_TAG.matcher(html).replaceFirst("<head$1>" + baseTag);
    }
    if (HTML_OPEN_TAG.matcher(html).find()) {
      return HTML_OPEN_TAG.matcher(html).replaceFirst("<html$1><head>" + baseTag + "</head>");
    }
    return "<!doctype html><html><head>" + baseTag + "</head><body>" + html + "</body></html>";
  }

  private String safeCurrentUrl() {
    return currentUrl().orElse("about:blank");
  }

  private static String escapeHtmlAttribute(String value) {
    return value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }

  private void captureBrowserLogs(WebDriver explicitDriver) {
    WebDriver driver = explicitDriver == null ? resolveDriver() : explicitDriver;
    if (driver == null) {
      return;
    }
    for (String logType : List.of("browser", "driver")) {
      try {
        LogEntries entries = driver.manage().logs().get(logType);
        if (entries == null || !entries.iterator().hasNext()) {
          continue;
        }

        StringBuilder content = new StringBuilder();
        for (LogEntry entry : entries) {
          content
              .append(DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(entry.getTimestamp())))
              .append(' ')
              .append(entry.getLevel())
              .append(' ')
              .append(entry.getMessage())
              .append(System.lineSeparator());
        }

        String relativePath = "resources/" + logType + "-log.txt";
        writer.writeTextResource(relativePath, content.toString());
        writeEvent(new SelenideTraceEvent(
            "attachment",
            logType + "-log",
            "captured",
            epochMs(),
            epochMs(),
            0L,
            null,
            Map.of("source", "webdriver-logs"),
            List.of(new SelenideTraceAttachment(logType + "-log", relativePath, "text/plain"))));
      } catch (Exception ignored) {
        // Logging is browser-specific. Missing log types should not fail the trace.
      }
    }
  }

  private List<Map<String, Object>> drainNetworkCalls(WebDriver driver, String stem) {
    try {
      LogEntries entries = driver.manage().logs().get("performance");
      if (entries == null || !entries.iterator().hasNext()) {
        return List.of();
      }

      TreeSet<String> touchedIds = new TreeSet<>();
      for (LogEntry entry : entries) {
        parsePerformanceEntry(entry, touchedIds, driver, stem);
      }

      List<Map<String, Object>> calls = new ArrayList<>();
      for (String requestId : touchedIds) {
        NetworkCall call = networkCalls.get(requestId);
        if (call != null) {
          calls.add(call.toMap());
        }
      }
      return calls;
    } catch (Exception ignored) {
      return List.of();
    }
  }

  @SuppressWarnings("unchecked")
  private void parsePerformanceEntry(LogEntry entry, TreeSet<String> touchedIds, WebDriver driver, String stem) {
    try {
      Map<String, Object> root = MAPPER.readValue(entry.getMessage(), MAP_TYPE);
      Map<String, Object> message = asMap(root.get("message"));
      String method = asString(message.get("method"));
      Map<String, Object> params = asMap(message.get("params"));
      if (method == null || params.isEmpty()) {
        return;
      }

      String requestId = asString(params.get("requestId"));
      if (requestId == null || requestId.isBlank()) {
        return;
      }

      NetworkCall call = networkCalls.computeIfAbsent(requestId, ignored -> new NetworkCall(requestId));
      touchedIds.add(requestId);

      switch (method) {
        case "Network.requestWillBeSent" -> {
          Map<String, Object> request = asMap(params.get("request"));
          call.url = asString(request.get("url"));
          call.path = extractPath(call.url);
          call.method = asString(request.get("method"));
          call.resourceType = asString(params.get("type"));
          call.requestHeaders = sanitizeMap(asMap(request.get("headers")));
          call.requestHeaderText = toPrettyJson(call.requestHeaders);
          call.postData = asString(request.get("postData"));
          if (call.postData != null && !call.postData.isBlank()) {
            call.requestBodyPreview = truncate(call.postData, 1200);
            call.requestBodyPath = writeNetworkBodyResource(stem, requestId, "request", call.postData);
          }
          call.startedAt = entry.getTimestamp();
          call.wallTime = asDouble(params.get("wallTime"));
          call.hasResponse = false;
          call.failed = false;
        }
        case "Network.responseReceived" -> {
          Map<String, Object> response = asMap(params.get("response"));
          call.status = asInteger(response.get("status"));
          call.statusText = asString(response.get("statusText"));
          call.mimeType = asString(response.get("mimeType"));
          call.protocol = asString(response.get("protocol"));
          call.remoteIpAddress = asString(response.get("remoteIPAddress"));
          call.responseHeaders = sanitizeMap(asMap(response.get("headers")));
          call.responseHeaderText = toPrettyJson(call.responseHeaders);
          call.responseTime = asDouble(response.get("responseTime"));
          call.connectionReused = asBoolean(response.get("connectionReused"));
          call.connectionId = asString(response.get("connectionId"));
          call.fromCache = asBoolean(response.get("fromDiskCache")) || asBoolean(response.get("fromPrefetchCache"));
          call.fromServiceWorker = asBoolean(response.get("fromServiceWorker"));
          call.hasResponse = true;
        }
        case "Network.loadingFinished" -> {
          call.finishedAt = entry.getTimestamp();
          call.encodedDataLength = asDouble(params.get("encodedDataLength"));
          call.finished = true;
          captureResponseBody(driver, stem, call);
        }
        case "Network.loadingFailed" -> {
          call.finishedAt = entry.getTimestamp();
          call.failed = true;
          call.finished = true;
          call.errorText = asString(params.get("errorText"));
        }
        default -> {
        }
      }
    } catch (Exception ignored) {
      // Performance log payloads vary by browser version. Ignore malformed entries.
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static boolean asBoolean(Object value) {
    return value instanceof Boolean bool && bool;
  }

  private static Integer asInteger(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return null;
  }

  private static Double asDouble(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return null;
  }

  private static String toJson(Object value) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to serialize network trace payload", exception);
    }
  }

  private static String toPrettyJson(Object value) {
    try {
      return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (Exception ignored) {
      return String.valueOf(value);
    }
  }

  private static Map<String, String> sanitizeMap(Map<String, Object> source) {
    if (source.isEmpty()) {
      return Map.of();
    }
    Map<String, String> result = new LinkedHashMap<>();
    source.forEach((key, value) -> result.put(String.valueOf(key), value == null ? "" : String.valueOf(value)));
    return result;
  }

  private static String extractPath(String url) {
    if (url == null || url.isBlank()) {
      return null;
    }
    try {
      URI uri = URI.create(url);
      String path = uri.getRawPath();
      String query = uri.getRawQuery();
      if (path == null || path.isBlank()) {
        return "/";
      }
      return query == null || query.isBlank() ? path : path + "?" + query;
    } catch (Exception ignored) {
      return url;
    }
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...";
  }

  private String writeNetworkBodyResource(String stem, String requestId, String prefix, String body) {
    String safeId = requestId.replaceAll("[^a-zA-Z0-9._-]+", "_");
    String relativePath = "resources/" + stem + "-" + prefix + "-" + safeId + ".txt";
    writer.writeTextResource(relativePath, body);
    return relativePath;
  }

  @SuppressWarnings("unchecked")
  private void captureResponseBody(WebDriver driver, String stem, NetworkCall call) {
    if (call.responseBodyPath != null || call.requestId == null) {
      return;
    }
    try {
      Object response = driver.getClass()
          .getMethod("executeCdpCommand", String.class, Map.class)
          .invoke(driver, "Network.getResponseBody", Map.of("requestId", call.requestId));
      if (!(response instanceof Map<?, ?> bodyMap)) {
        return;
      }
      String body = asString(bodyMap.get("body"));
      boolean base64Encoded = asBoolean(bodyMap.get("base64Encoded"));
      if (body == null || body.isBlank()) {
        return;
      }
      if (base64Encoded) {
        body = new String(Base64.getDecoder().decode(body));
      }
      call.responseBodyPreview = truncate(body, 1200);
      call.responseBodyPath = writeNetworkBodyResource(stem, call.requestId, "response", body);
    } catch (Exception ignored) {
      // Response body fetch is best-effort and browser-specific.
    }
  }

  private void writeEvent(SelenideTraceEvent event) {
    events.add(event);
    writer.writeEvent(event);
  }

  private List<SelenideTraceAttachment> drainPendingActionAttachments() {
    List<SelenideTraceAttachment> drained = new ArrayList<>(pendingActionAttachments);
    pendingActionAttachments.clear();
    return drained;
  }

  private Optional<String> currentUrl() {
    return currentUrl(resolveDriver());
  }

  private Optional<String> currentUrl(WebDriver driver) {
    if (driver == null) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(driver.getCurrentUrl());
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private Optional<String> currentTitle() {
    return currentTitle(resolveDriver());
  }

  private Optional<String> currentTitle(WebDriver driver) {
    if (driver == null) {
      return Optional.empty();
    }
    try {
      return Optional.ofNullable(driver.getTitle());
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private long epochMs() {
    return clock.millis();
  }

  private Instant now() {
    return clock.instant();
  }

  private static String stackTrace(Throwable throwable) {
    if (throwable == null) {
      return null;
    }
    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }

  private WebDriver resolveDriver() {
    if (primaryDriver != null) {
      return primaryDriver;
    }
    if (WebDriverRunner.hasWebDriverStarted()) {
      return WebDriverRunner.getWebDriver();
    }
    return null;
  }

  private static final String INJECT_TRACE_MARKER_SCRIPT = """
      const descriptor = arguments[0];
      function resolveTargets(value) {
        if (!value) return [];
        if (value instanceof Element) return [value];
        if (Array.isArray(value)) return value.filter(node => node instanceof Element);
        if (typeof NodeList !== 'undefined' && value instanceof NodeList) {
          return Array.from(value).filter(node => node instanceof Element);
        }
        const candidate = String(value).trim();
        const xpathPrefixes = ['By.xpath: ', 'xpath: '];
        for (const prefix of xpathPrefixes) {
          if (candidate.startsWith(prefix)) {
            const xpath = candidate.substring(prefix.length).trim();
            const snapshot = document.evaluate(xpath, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
            const matches = [];
            for (let index = 0; index < snapshot.snapshotLength; index += 1) {
              const node = snapshot.snapshotItem(index);
              if (node instanceof Element) matches.push(node);
            }
            return matches;
          }
        }
        if (candidate.startsWith('/') || candidate.startsWith('(')) {
          const snapshot = document.evaluate(candidate, document, null, XPathResult.ORDERED_NODE_SNAPSHOT_TYPE, null);
          const matches = [];
          for (let index = 0; index < snapshot.snapshotLength; index += 1) {
            const node = snapshot.snapshotItem(index);
            if (node instanceof Element) matches.push(node);
          }
          return matches;
        }
        const cssPrefixes = ['By.cssSelector: ', 'css selector: '];
        for (const prefix of cssPrefixes) {
          if (candidate.startsWith(prefix)) {
            return Array.from(document.querySelectorAll(candidate.substring(prefix.length).trim()));
          }
        }
        return Array.from(document.querySelectorAll(candidate));
      }
      const targets = resolveTargets(descriptor).filter(node => node instanceof Element);
      if (!targets.length) return null;
      const token = 'selenide-trace-marker-' + Date.now() + '-' + Math.random().toString(16).slice(2);
      targets.forEach(target => {
        target.scrollIntoView({ block: 'center', inline: 'center' });
        const computedStyle = window.getComputedStyle(target);
        if (computedStyle.position === 'static') {
          target.dataset.traceMarkerOriginalPosition = target.style.position || '';
          target.style.position = 'relative';
        }
        const dot = document.createElement('div');
        dot.dataset.traceMarker = token;
        dot.style.position = 'absolute';
        dot.style.left = '50%';
        dot.style.top = '50%';
        dot.style.transform = 'translate(-50%, -50%)';
        dot.style.width = '12px';
        dot.style.height = '12px';
        dot.style.borderRadius = '999px';
        dot.style.background = '#a855f7';
        dot.style.boxShadow = '0 0 0 4px rgba(168,85,247,0.18)';
        dot.style.pointerEvents = 'none';
        dot.style.zIndex = '2147483647';
        target.append(dot);
      });
      return token;
      """;

  private static final String REMOVE_TRACE_MARKER_SCRIPT = """
      const token = arguments[0];
      document.querySelectorAll(`[data-trace-marker="${token}"]`).forEach(node => {
        const parent = node.parentElement;
        node.remove();
        if (parent && parent.dataset.traceMarkerOriginalPosition !== undefined) {
          parent.style.position = parent.dataset.traceMarkerOriginalPosition;
          delete parent.dataset.traceMarkerOriginalPosition;
        }
      });
      """;

  record TraceStep(String name, long startedAtEpochMs) {
    TraceStep {
      Objects.requireNonNull(name, "name");
    }
  }

  private record ArtifactBundle(
      List<SelenideTraceAttachment> attachments,
      List<Map<String, Object>> networkCalls
  ) {
  }

  private static final class NetworkCall {
    private final String requestId;
    private String url;
    private String method;
    private String path;
    private String resourceType;
    private Integer status;
    private String statusText;
    private String mimeType;
    private String protocol;
    private String remoteIpAddress;
    private Map<String, String> requestHeaders = Map.of();
    private Map<String, String> responseHeaders = Map.of();
    private String requestHeaderText;
    private String responseHeaderText;
    private String postData;
    private String requestBodyPreview;
    private String requestBodyPath;
    private String responseBodyPreview;
    private String responseBodyPath;
    private Double encodedDataLength;
    private Double wallTime;
    private Double responseTime;
    private Long startedAt;
    private Long finishedAt;
    private boolean fromCache;
    private boolean fromServiceWorker;
    private boolean connectionReused;
    private String connectionId;
    private boolean hasResponse;
    private boolean finished;
    private boolean failed;
    private String errorText;

    private NetworkCall(String requestId) {
      this.requestId = requestId;
    }

    private Map<String, Object> toMap() {
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("requestId", requestId);
      result.put("url", url);
      result.put("path", path);
      result.put("method", method);
      result.put("resourceType", resourceType);
      result.put("status", status);
      result.put("statusText", statusText);
      result.put("mimeType", mimeType);
      result.put("protocol", protocol);
      result.put("remoteIpAddress", remoteIpAddress);
      result.put("requestHeaders", requestHeaders);
      result.put("responseHeaders", responseHeaders);
      result.put("requestHeaderText", requestHeaderText);
      result.put("responseHeaderText", responseHeaderText);
      result.put("requestBodyPreview", requestBodyPreview);
      result.put("requestBodyPath", requestBodyPath);
      result.put("responseBodyPreview", responseBodyPreview);
      result.put("responseBodyPath", responseBodyPath);
      result.put("postData", postData == null ? null : truncate(postData, 4000));
      result.put("encodedDataLength", encodedDataLength == null ? null : Math.round(encodedDataLength));
      result.put("responseTime", responseTime);
      result.put("startedAt", startedAt);
      result.put("finishedAt", finishedAt);
      result.put("durationMs", startedAt == null || finishedAt == null ? null : Math.max(finishedAt - startedAt, 0L));
      result.put("fromCache", fromCache);
      result.put("fromServiceWorker", fromServiceWorker);
      result.put("connectionReused", connectionReused);
      result.put("connectionId", connectionId);
      result.put("state", failed ? "failed" : finished ? "finished" : hasResponse ? "response" : "started");
      result.put("errorText", errorText);
      return result;
    }
  }
}
