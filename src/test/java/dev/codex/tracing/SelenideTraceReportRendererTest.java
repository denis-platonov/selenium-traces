package dev.codex.tracing;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SelenideTraceReportRendererTest {
  @Test
  void rendersPreviewModeButtonsAndMetadataPanels() {
    SelenideTraceEvent event = new SelenideTraceEvent(
        "action",
        "click() #submit",
        "passed",
        1_700_000_000_000L,
        1_700_000_000_250L,
        250L,
        null,
        Map.of(
            "source", "selenide",
            "url", "https://example.com/form",
            "title", "Example Form",
            "networkCalls", List.of(Map.of(
                "method", "GET",
                "url", "https://example.com/api/submit",
                "path", "/api/submit",
                "status", 200,
                "resourceType", "XHR",
                "requestHeaderText", "{\\n  \\\"accept\\\": \\\"application/json\\\"\\n}",
                "responseHeaderText", "{\\n  \\\"content-type\\\": \\\"application/json\\\"\\n}",
                "responseBodyPreview", "{\"ok\":true}",
                "responseBodyPath", "resources/event-network-response.txt"))),
        List.of(
            new SelenideTraceAttachment("event-network", "resources/event-network.json", "application/vnd.selenide-trace-network+json"),
            new SelenideTraceAttachment("event-dom", "resources/event.html", "text/html"),
            new SelenideTraceAttachment("event-screenshot", "resources/event.png", "image/png")));

    String html = new SelenideTraceReportRenderer().render("RendererTest.demo", List.of(event), ZoneOffset.UTC);

    assertTrue(html.contains("preview-modes"));
    assertTrue(html.contains("preview-mode-label"));
    assertTrue(html.contains("preview-mode-open"));
    assertTrue(html.contains("preferredPreviewMode"));
    assertTrue(html.contains("renderPreview(preferredPreviewMode"));
    assertTrue(html.contains("Metadata"));
    assertTrue(html.contains("Network"));
    assertTrue(html.contains("network-list"));
    assertTrue(html.contains("networkCalls"));
    assertTrue(html.contains("renderBodySection"));
    assertTrue(html.contains("Request Headers"));
    assertTrue(html.contains("Response Headers"));
    assertTrue(html.contains("Open full body"));
    assertTrue(html.contains("Attachments"));
  }
}
