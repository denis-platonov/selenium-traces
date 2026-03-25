package dev.codex.tracing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SelenideTraceReportRenderer {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSS z", Locale.ROOT);

  String render(String testName, List<SelenideTraceEvent> events, ZoneId zoneId) {
    String eventsJson = toJson(events);
    String summaryJson = toJson(buildSummary(testName, events, zoneId));

    return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Selenide Trace Viewer</title>
          <style>
            :root {
              color-scheme: dark;
              --bg: #15171b;
              --bg-2: #1b1f24;
              --panel: #20242b;
              --panel-2: #262b33;
              --panel-3: #0f1216;
              --line: #343a46;
              --line-soft: #2a2f37;
              --text: #e7eaef;
              --muted: #98a2b3;
              --muted-2: #7b8493;
              --blue: #4ea1ff;
              --green: #2dc26b;
              --red: #ff6b6b;
              --amber: #ffb454;
            }
            * { box-sizing: border-box; }
            html, body { height: 100%; }
            body {
              margin: 0;
              background: var(--bg);
              color: var(--text);
              font: 13px/1.4 "Segoe UI", "SF Pro Text", system-ui, sans-serif;
            }
            button, input, select, textarea {
              font: inherit;
            }
            .app {
              height: 100vh;
              display: grid;
              grid-template-rows: 46px 1fr;
            }
            .topbar {
              display: grid;
              grid-template-columns: 1fr auto auto;
              gap: 14px;
              align-items: center;
              padding: 0 14px;
              border-bottom: 1px solid var(--line);
              background: linear-gradient(180deg, #20242a 0%, #1a1e24 100%);
            }
            .brand {
              display: flex;
              align-items: center;
              gap: 10px;
              min-width: 0;
            }
            .brand-dot {
              width: 9px;
              height: 9px;
              border-radius: 999px;
              background: var(--blue);
              box-shadow: 0 0 0 4px rgba(78, 161, 255, 0.14);
            }
            .brand-title {
              font-weight: 600;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }
            .topbar-meta {
              color: var(--muted);
              white-space: nowrap;
            }
            .topbar-status {
              padding: 3px 8px;
              border-radius: 999px;
              border: 1px solid var(--line);
              background: var(--panel-2);
              font-size: 12px;
              font-weight: 600;
              text-transform: uppercase;
            }
            .topbar-status.passed, .topbar-status.started, .topbar-status.captured {
              color: var(--green);
            }
            .topbar-status.failed {
              color: var(--red);
            }
            .workspace {
              min-height: 0;
              display: grid;
              grid-template-columns: 320px minmax(0, 1fr) 340px;
            }
            .sidebar, .snapshot, .details {
              min-width: 0;
              min-height: 0;
            }
            .sidebar {
              border-right: 1px solid var(--line);
              background: var(--bg-2);
              display: grid;
              grid-template-rows: auto 1fr;
            }
            .sidebar-head {
              padding: 12px 12px 10px;
              border-bottom: 1px solid var(--line);
            }
            .sidebar-title {
              font-size: 12px;
              font-weight: 700;
              letter-spacing: 0.08em;
              text-transform: uppercase;
              color: var(--muted);
            }
            .sidebar-sub {
              margin-top: 6px;
              color: var(--muted-2);
            }
            .timeline {
              overflow: auto;
              padding: 8px;
            }
            .event {
              width: 100%;
              display: grid;
              grid-template-columns: 12px 1fr auto;
              gap: 10px;
              align-items: start;
              text-align: left;
              margin: 0 0 6px;
              padding: 9px 10px;
              border: 1px solid transparent;
              border-radius: 8px;
              background: transparent;
              color: inherit;
              cursor: pointer;
            }
            .event:hover {
              background: rgba(255,255,255,0.03);
            }
            .event.active {
              background: rgba(78, 161, 255, 0.08);
              border-color: rgba(78, 161, 255, 0.26);
            }
            .event-marker {
              width: 8px;
              height: 8px;
              border-radius: 999px;
              margin-top: 5px;
              background: var(--muted-2);
            }
            .event-marker.passed, .event-marker.started, .event-marker.captured {
              background: var(--green);
            }
            .event-marker.failed {
              background: var(--red);
            }
            .event-name {
              font-weight: 500;
              color: var(--text);
              overflow-wrap: anywhere;
            }
            .event-meta {
              margin-top: 2px;
              color: var(--muted-2);
              font-size: 12px;
            }
            .event-duration {
              color: var(--muted);
              font-variant-numeric: tabular-nums;
              font-size: 12px;
              padding-left: 8px;
            }
            .snapshot {
              display: grid;
              grid-template-rows: auto 1fr auto;
              background: #171a1f;
            }
            .snapshot-head {
              display: flex;
              justify-content: space-between;
              gap: 12px;
              align-items: center;
              padding: 10px 14px;
              border-bottom: 1px solid var(--line);
              background: var(--panel-3);
            }
            .snapshot-title {
              min-width: 0;
            }
            .snapshot-title strong {
              display: block;
              font-size: 14px;
              font-weight: 600;
              white-space: nowrap;
              overflow: hidden;
              text-overflow: ellipsis;
            }
            .snapshot-title span {
              color: var(--muted-2);
            }
            .snapshot-links {
              display: flex;
              gap: 8px;
              flex-wrap: wrap;
            }
            .preview-modes {
              display: flex;
              gap: 6px;
              flex-wrap: wrap;
            }
            .preview-mode {
              border: 1px solid var(--line);
              border-radius: 6px;
              background: var(--panel);
              color: var(--muted);
              padding: 0;
              cursor: pointer;
              display: inline-flex;
              align-items: stretch;
              overflow: hidden;
            }
            .preview-mode.active {
              color: var(--text);
              border-color: rgba(78, 161, 255, 0.35);
              background: #28303a;
            }
            .preview-mode-label {
              display: inline-flex;
              align-items: center;
              padding: 5px 9px;
            }
            .preview-mode-open {
              display: inline-flex;
              align-items: center;
              gap: 6px;
              justify-content: center;
              min-width: 24px;
              padding: 5px 7px;
              border-left: 1px solid var(--line);
              color: var(--muted);
              text-decoration: none;
              background: rgba(255,255,255,0.02);
            }
            .preview-mode-open:hover {
              border-color: rgba(78, 161, 255, 0.35);
              background: #28303a;
              color: var(--text);
            }
            .stage {
              min-height: 0;
              display: grid;
              place-items: center;
              padding: 18px;
              background:
                linear-gradient(45deg, rgba(255,255,255,0.03) 25%, transparent 25%),
                linear-gradient(-45deg, rgba(255,255,255,0.03) 25%, transparent 25%),
                linear-gradient(45deg, transparent 75%, rgba(255,255,255,0.03) 75%),
                linear-gradient(-45deg, transparent 75%, rgba(255,255,255,0.03) 75%);
              background-size: 22px 22px;
              background-position: 0 0, 0 11px, 11px -11px, -11px 0;
            }
            .stage > * {
              width: 100%;
              height: 100%;
            }
            .stage-frame {
              width: 100%;
              height: 100%;
              min-height: 560px;
              border: 1px solid var(--line);
              border-radius: 10px;
              background: white;
            }
            .stage-image {
              object-fit: contain;
              width: 100%;
              height: 100%;
              min-height: 560px;
              border: 1px solid var(--line);
              border-radius: 10px;
              background: #fff;
            }
            .stage-empty {
              width: 100%;
              height: 100%;
              min-height: 560px;
              border: 1px dashed var(--line);
              border-radius: 10px;
              display: grid;
              place-items: center;
              color: var(--muted);
              background: rgba(255,255,255,0.02);
            }
            .snapshot-foot {
              display: flex;
              justify-content: space-between;
              gap: 16px;
              padding: 10px 14px;
              border-top: 1px solid var(--line);
              background: var(--panel-3);
              color: var(--muted);
              font-size: 12px;
            }
            .details {
              border-left: 1px solid var(--line);
              background: var(--bg-2);
              display: grid;
              grid-template-rows: auto 1fr;
            }
            .tabs {
              display: flex;
              gap: 2px;
              padding: 10px;
              border-bottom: 1px solid var(--line);
              background: var(--panel-3);
            }
            .tab {
              border: 0;
              background: transparent;
              color: var(--muted);
              padding: 8px 10px;
              border-radius: 6px;
              cursor: pointer;
            }
            .tab.active {
              background: var(--panel);
              color: var(--text);
            }
            .panels {
              overflow: auto;
              padding: 14px;
            }
            .panel-view {
              display: none;
            }
            .panel-view.active {
              display: block;
            }
            .section {
              margin-bottom: 18px;
            }
            .section:last-child {
              margin-bottom: 0;
            }
            .section-title {
              margin: 0 0 10px;
              color: var(--muted);
              font-size: 12px;
              font-weight: 700;
              text-transform: uppercase;
              letter-spacing: 0.08em;
            }
            .kv {
              display: grid;
              grid-template-columns: 92px 1fr;
              gap: 8px 10px;
            }
            .kv dt {
              margin: 0;
              color: var(--muted-2);
            }
            .kv dd {
              margin: 0;
              color: var(--text);
              overflow-wrap: anywhere;
            }
            .badge-inline {
              display: inline-flex;
              align-items: center;
              gap: 6px;
              padding: 2px 8px;
              border-radius: 999px;
              border: 1px solid var(--line);
              background: var(--panel);
              color: var(--text);
            }
            .attachment-list {
              display: grid;
              gap: 8px;
            }
            .network-list {
              display: grid;
              gap: 8px;
            }
            .network-item {
              border: 1px solid var(--line-soft);
              border-radius: 8px;
              background: var(--panel);
              overflow: hidden;
            }
            .network-summary {
              list-style: none;
              cursor: pointer;
              padding: 10px;
              display: grid;
              grid-template-columns: auto auto minmax(0, 1fr) auto;
              gap: 8px;
              align-items: start;
            }
            .network-summary::-webkit-details-marker { display: none; }
            .network-summary::after {
              content: "^";
              display: inline-flex;
              align-items: center;
              justify-content: center;
              width: 22px;
              height: 22px;
              border: 1px solid var(--line);
              border-radius: 999px;
              color: var(--muted);
              transform: rotate(180deg);
              transition: transform 120ms ease, color 120ms ease;
            }
            .network-item[open] .network-summary::after {
              transform: rotate(0deg);
              color: var(--blue);
            }
            .network-method {
              color: var(--blue);
              font-weight: 700;
            }
            .network-status {
              color: var(--muted);
              font-weight: 600;
            }
            .network-url {
              color: var(--text);
              overflow-wrap: anywhere;
            }
            .network-url small {
              display: block;
              color: var(--muted);
              margin-top: 3px;
            }
            .network-meta {
              color: var(--muted);
              font-size: 12px;
              display: flex;
              gap: 10px;
              flex-wrap: wrap;
            }
            .network-body {
              padding: 0 10px 10px;
              border-top: 1px solid var(--line-soft);
              display: grid;
              gap: 10px;
            }
            .network-section {
              display: grid;
              gap: 6px;
            }
            .network-section h3 {
              margin: 0;
              font-size: 12px;
              color: var(--muted);
              text-transform: uppercase;
              letter-spacing: 0.08em;
            }
            .network-code {
              margin: 0;
              padding: 10px;
              border-radius: 8px;
              border: 1px solid var(--line-soft);
              background: #181c22;
              white-space: pre-wrap;
              word-break: break-word;
              font: 12px/1.45 ui-monospace, "Cascadia Code", monospace;
              color: #d9e2ef;
            }
            .network-links {
              display: flex;
              gap: 8px;
              flex-wrap: wrap;
            }
            .network-link {
              color: var(--blue);
              text-decoration: none;
            }
            .attachment {
              display: block;
              padding: 10px;
              border: 1px solid var(--line-soft);
              border-radius: 8px;
              background: var(--panel);
              color: inherit;
              text-decoration: none;
            }
            .attachment:hover {
              border-color: rgba(78, 161, 255, 0.35);
            }
            .attachment strong {
              display: block;
              margin-bottom: 4px;
            }
            .attachment span {
              color: var(--muted);
              font-size: 12px;
            }
            .error-box {
              padding: 10px;
              border-radius: 8px;
              border: 1px solid rgba(255, 107, 107, 0.28);
              background: rgba(255, 107, 107, 0.08);
            }
            pre {
              margin: 0;
              white-space: pre-wrap;
              word-break: break-word;
              font: 12px/1.5 ui-monospace, "Cascadia Code", monospace;
              color: #ffd6d6;
            }
            @media (max-width: 1280px) {
              .workspace {
                grid-template-columns: 280px minmax(0, 1fr) 300px;
              }
            }
            @media (max-width: 960px) {
              .workspace {
                grid-template-columns: 1fr;
                grid-template-rows: 260px minmax(420px, 1fr) 320px;
              }
              .sidebar, .details {
                border: 0;
              }
              .sidebar {
                border-bottom: 1px solid var(--line);
              }
              .details {
                border-top: 1px solid var(--line);
              }
            }
          </style>
        </head>
        <body>
          <div class="app">
            <header class="topbar">
              <div class="brand">
                <span class="brand-dot"></span>
                <div class="brand-title" id="brand-title"></div>
              </div>
              <div class="topbar-meta" id="topbar-meta"></div>
              <div class="topbar-status" id="topbar-status"></div>
            </header>
            <main class="workspace">
              <aside class="sidebar">
                <div class="sidebar-head">
                  <div class="sidebar-title">Actions</div>
                  <div class="sidebar-sub" id="sidebar-sub"></div>
                </div>
                <div class="timeline" id="timeline"></div>
              </aside>
              <section class="snapshot">
                <div class="snapshot-head">
                  <div class="snapshot-title">
                    <strong id="snapshot-name"></strong>
                    <span id="snapshot-subtitle"></span>
                  </div>
                  <div class="preview-modes" id="preview-modes"></div>
                </div>
                <div class="stage" id="stage"></div>
                <div class="snapshot-foot">
                  <span id="snapshot-url"></span>
                  <span id="snapshot-time"></span>
                </div>
              </section>
              <aside class="details">
                <div class="tabs">
                  <button class="tab active" data-tab="meta">Metadata</button>
                  <button class="tab" data-tab="network">Network</button>
                  <button class="tab" data-tab="attachments">Attachments</button>
                  <button class="tab" data-tab="error">Error</button>
                </div>
                <div class="panels">
                  <section class="panel-view active" data-panel="meta">
                    <div class="section">
                      <h2 class="section-title">Overview</h2>
                      <dl class="kv" id="meta-overview"></dl>
                    </div>
                    <div class="section">
                      <h2 class="section-title">Context</h2>
                      <dl class="kv" id="meta-context"></dl>
                    </div>
                  </section>
                  <section class="panel-view" data-panel="network">
                    <div class="section">
                      <h2 class="section-title">Network</h2>
                      <div class="network-list" id="network-list"></div>
                    </div>
                  </section>
                  <section class="panel-view" data-panel="attachments">
                    <div class="section">
                      <h2 class="section-title">Artifacts</h2>
                      <div class="attachment-list" id="attachment-list"></div>
                    </div>
                  </section>
                  <section class="panel-view" data-panel="error">
                    <div class="section">
                      <h2 class="section-title">Failure</h2>
                      <div id="error-panel"></div>
                    </div>
                  </section>
                </div>
              </aside>
            </main>
          </div>
          <script id="trace-events" type="application/json">__EVENTS__</script>
          <script id="trace-summary" type="application/json">__SUMMARY__</script>
          <script>
            const events = JSON.parse(document.getElementById('trace-events').textContent);
            const summary = JSON.parse(document.getElementById('trace-summary').textContent);
            let activeTab = 'meta';
            let preferredPreviewMode = 'dom';
            let networkRenderToken = 0;

            document.getElementById('brand-title').textContent = summary.testName;
            document.getElementById('topbar-meta').textContent =
              summary.startedAt + ' | ' + summary.durationMs + ' ms | ' + summary.eventCount + ' events';
            const topbarStatus = document.getElementById('topbar-status');
            topbarStatus.textContent = summary.status;
            topbarStatus.classList.add(summary.status || 'unknown');
            document.getElementById('sidebar-sub').textContent = 'Recorded actions and checkpoints';

            const timeline = document.getElementById('timeline');
            events.forEach((event, index) => {
              const button = document.createElement('button');
              button.className = 'event' + (index === events.length - 1 ? ' active' : '');
              button.innerHTML = `
                <span class="event-marker ${event.status || ''}"></span>
                <span>
                  <span class="event-name">${escapeHtml(event.name || '(unnamed event)')}</span>
                  <span class="event-meta">${escapeHtml(event.type || 'event')} | ${escapeHtml(event.status || 'unknown')}</span>
                </span>
                <span class="event-duration">${escapeHtml(String(event.durationMs || 0))} ms</span>
              `;
              button.addEventListener('click', () => {
                document.querySelectorAll('.event').forEach(node => node.classList.remove('active'));
                button.classList.add('active');
                renderEvent(event);
              });
              timeline.appendChild(button);
            });

            document.querySelectorAll('.tab').forEach(tab => {
              tab.addEventListener('click', () => {
                activeTab = tab.dataset.tab;
                document.querySelectorAll('.tab').forEach(node => node.classList.toggle('active', node === tab));
                document.querySelectorAll('.panel-view').forEach(panel => {
                  panel.classList.toggle('active', panel.dataset.panel === activeTab);
                });
              });
            });

            renderEvent(events[events.length - 1] || null);

            function renderEvent(event) {
              if (!event) {
                document.getElementById('snapshot-name').textContent = 'No events recorded';
                document.getElementById('snapshot-subtitle').textContent = '';
                document.getElementById('stage').innerHTML = '<div class="stage-empty">No trace data available.</div>';
                document.getElementById('snapshot-url').textContent = '';
                document.getElementById('snapshot-time').textContent = '';
                document.getElementById('meta-overview').innerHTML = '';
                document.getElementById('meta-context').innerHTML = '';
                document.getElementById('network-list').innerHTML = '<div class="badge-inline">No network data</div>';
                document.getElementById('attachment-list').innerHTML = '';
                document.getElementById('error-panel').innerHTML = '<div class="badge-inline">No error</div>';
                return;
              }

              const attachments = event.attachments || [];
              const dom = attachments.find(item => item.contentType === 'text/html');
              const screenshot = attachments.find(item => item.contentType === 'image/png');
              const textAttachment = attachments.find(item => item.contentType === 'text/plain');
              const networkAttachment = attachments.find(item => item.contentType === 'application/vnd.selenide-trace-network+json');
              const networkCalls = Array.isArray(event.data && event.data.networkCalls) ? event.data.networkCalls : [];
              const stage = document.getElementById('stage');
              const previewModes = document.getElementById('preview-modes');
              const availableModes = [];

              if (dom) availableModes.push({ key: 'dom', label: 'DOM', attachment: dom });
              if (screenshot) availableModes.push({ key: 'screenshot', label: 'Screenshot', attachment: screenshot });
              if (textAttachment) availableModes.push({ key: 'log', label: 'Log', attachment: textAttachment });

              document.getElementById('snapshot-name').textContent = event.name || '(unnamed event)';
              document.getElementById('snapshot-subtitle').textContent =
                (event.type || 'event') + ' | ' + (event.status || 'unknown');
              document.getElementById('snapshot-url').textContent = (event.data && event.data.url) || 'No URL recorded';
              document.getElementById('snapshot-time').textContent =
                new Date(event.startedAtEpochMs).toLocaleString() + ' -> ' +
                new Date(event.finishedAtEpochMs).toLocaleString();

              if (!availableModes.some(mode => mode.key === preferredPreviewMode) && availableModes.length) {
                preferredPreviewMode = availableModes[0].key;
              }

              previewModes.innerHTML = availableModes.map(mode => `
                <div class="preview-mode ${mode.key === preferredPreviewMode ? 'active' : ''}">
                  <button class="preview-mode-label" data-preview-mode="${mode.key}">${escapeHtml(mode.label)}</button>
                  <a class="preview-mode-open" href="${encodeURI(mode.attachment.file)}" target="_blank" rel="noreferrer" title="Open ${escapeHtml(mode.label)} fullscreen">&rsaquo;</a>
                </div>
              `).join('');

              previewModes.querySelectorAll('.preview-mode-label').forEach(button => {
                button.addEventListener('click', () => {
                  preferredPreviewMode = button.dataset.previewMode;
                  renderEvent(event);
                });
              });

              previewModes.querySelectorAll('.preview-mode-open').forEach(link => {
                link.addEventListener('click', clickEvent => {
                  clickEvent.stopPropagation();
                });
              });

              renderPreview(preferredPreviewMode, { dom, screenshot, textAttachment }, stage);

              document.getElementById('meta-overview').innerHTML = [
                row('Name', event.name || '-'),
                row('Type', event.type || '-'),
                row('Status', '<span class="badge-inline">' + escapeHtml(event.status || '-') + '</span>', true),
                row('Duration', String(event.durationMs || 0) + ' ms'),
                row('Started', new Date(event.startedAtEpochMs).toLocaleString()),
                row('Finished', new Date(event.finishedAtEpochMs).toLocaleString())
              ].join('');

              document.getElementById('meta-context').innerHTML = [
                row('URL', (event.data && event.data.url) || '-'),
                row('Title', (event.data && event.data.title) || '-'),
                row('Source', (event.data && event.data.source) || '-'),
                row('Network', networkCalls.length ? String(networkCalls.length) + ' calls' : 'No calls'),
                row('Artifacts', String(attachments.length))
              ].join('');

              renderNetwork(networkCalls, networkAttachment);

              document.getElementById('attachment-list').innerHTML = attachments.length
                ? attachments.map(attachment => `
                    <a class="attachment" href="${encodeURI(attachment.file)}" target="_blank" rel="noreferrer">
                      <strong>${escapeHtml(attachment.name)}</strong>
                      <span>${escapeHtml(attachment.contentType)} | ${escapeHtml(attachment.file)}</span>
                    </a>
                  `).join('')
                : '<div class="badge-inline">No attachments</div>';

              document.getElementById('error-panel').innerHTML = event.error
                ? '<div class="error-box"><pre>' + escapeHtml(event.error) + '</pre></div>'
                : '<div class="badge-inline">No error</div>';
            }

            async function renderNetwork(networkCalls, networkAttachment) {
              const panel = document.getElementById('network-list');
              if (Array.isArray(networkCalls) && networkCalls.length) {
                panel.innerHTML = networkCalls.map(call => `
                  <details class="network-item">
                    <summary class="network-summary">
                      <span class="network-method">${escapeHtml(call.method || 'REQUEST')}</span>
                      <span class="network-status">${escapeHtml(String(call.status || call.state || '-'))}</span>
                      <span class="network-url">
                        ${escapeHtml(call.url || '-')}
                        <small>${escapeHtml(call.path || '/')}</small>
                      </span>
                      <span class="network-status">${escapeHtml(formatBytes(call.encodedDataLength))}</span>
                    </summary>
                    <div class="network-body">
                      <div class="network-meta">
                        <span>${escapeHtml(call.resourceType || 'resource')}</span>
                        <span>${escapeHtml(call.mimeType || 'unknown mime')}</span>
                        <span>${escapeHtml(call.protocol || 'protocol n/a')}</span>
                        <span>${escapeHtml(call.durationMs == null ? '-' : String(call.durationMs) + ' ms')}</span>
                        <span>${escapeHtml(call.fromCache ? 'cache' : call.fromServiceWorker ? 'service worker' : 'network')}</span>
                        <span>${escapeHtml(call.remoteIpAddress || 'ip n/a')}</span>
                      </div>
                      ${renderNetworkSection('Request Headers', call.requestHeaderText)}
                      ${renderBodySection('Request Body', call.requestBodyPreview, call.requestBodyPath)}
                      ${renderNetworkSection('Response Headers', call.responseHeaderText)}
                      ${renderBodySection('Response Body', call.responseBodyPreview, call.responseBodyPath)}
                      ${call.errorText ? '<div class="network-meta" style="color:#ffb4b4;">' + escapeHtml(call.errorText) + '</div>' : ''}
                    </div>
                  </details>
                `).join('');
                return;
              }
              if (!networkAttachment) {
                panel.innerHTML = '<div class="badge-inline">No network data</div>';
                return;
              }
              const token = ++networkRenderToken;
              panel.innerHTML = '<div class="badge-inline">Loading network calls...</div>';
              try {
                const response = await fetch(networkAttachment.file);
                const calls = await response.json();
                if (token !== networkRenderToken) {
                  return;
                }
                if (!Array.isArray(calls) || !calls.length) {
                  panel.innerHTML = '<div class="badge-inline">No network data</div>';
                  return;
                }
                panel.innerHTML = calls.map(call => `
                  <details class="network-item">
                    <summary class="network-summary">
                      <span class="network-method">${escapeHtml(call.method || 'REQUEST')}</span>
                      <span class="network-status">${escapeHtml(String(call.status || call.state || '-'))}</span>
                      <span class="network-url">
                        ${escapeHtml(call.url || '-')}
                        <small>${escapeHtml(call.path || '/')}</small>
                      </span>
                      <span class="network-status">${escapeHtml(formatBytes(call.encodedDataLength))}</span>
                    </summary>
                    <div class="network-body">
                      <div class="network-meta">
                        <span>${escapeHtml(call.resourceType || 'resource')}</span>
                        <span>${escapeHtml(call.mimeType || 'unknown mime')}</span>
                        <span>${escapeHtml(call.protocol || 'protocol n/a')}</span>
                        <span>${escapeHtml(call.durationMs == null ? '-' : String(call.durationMs) + ' ms')}</span>
                        <span>${escapeHtml(call.fromCache ? 'cache' : call.fromServiceWorker ? 'service worker' : 'network')}</span>
                        <span>${escapeHtml(call.remoteIpAddress || 'ip n/a')}</span>
                      </div>
                      ${renderNetworkSection('Request Headers', call.requestHeaderText)}
                      ${renderBodySection('Request Body', call.requestBodyPreview, call.requestBodyPath)}
                      ${renderNetworkSection('Response Headers', call.responseHeaderText)}
                      ${renderBodySection('Response Body', call.responseBodyPreview, call.responseBodyPath)}
                      ${call.errorText ? '<div class="network-meta" style="color:#ffb4b4;">' + escapeHtml(call.errorText) + '</div>' : ''}
                    </div>
                  </details>
                `).join('');
              } catch (error) {
                if (token !== networkRenderToken) {
                  return;
                }
                panel.innerHTML = '<div class="badge-inline">Failed to load network data</div>';
              }
            }

            function row(label, value, raw) {
              return '<dt>' + escapeHtml(label) + '</dt><dd>' + (raw ? value : escapeHtml(String(value))) + '</dd>';
            }

            function renderPreview(mode, artifacts, stage) {
              if (mode === 'dom' && artifacts.dom) {
                stage.innerHTML = '<iframe class="stage-frame" sandbox="allow-same-origin" src="' + encodeURI(artifacts.dom.file) + '"></iframe>';
                return;
              }
              if (mode === 'screenshot' && artifacts.screenshot) {
                stage.innerHTML = '<img class="stage-image" src="' + encodeURI(artifacts.screenshot.file) + '" alt="Trace screenshot">';
                return;
              }
              if (mode === 'log' && artifacts.textAttachment) {
                stage.innerHTML = '<iframe class="stage-frame" sandbox="allow-same-origin" src="' + encodeURI(artifacts.textAttachment.file) + '"></iframe>';
                return;
              }
              if (artifacts.dom) {
                stage.innerHTML = '<iframe class="stage-frame" sandbox="allow-same-origin" src="' + encodeURI(artifacts.dom.file) + '"></iframe>';
                return;
              }
              if (artifacts.screenshot) {
                stage.innerHTML = '<img class="stage-image" src="' + encodeURI(artifacts.screenshot.file) + '" alt="Trace screenshot">';
                return;
              }
              if (artifacts.textAttachment) {
                stage.innerHTML = '<iframe class="stage-frame" sandbox="allow-same-origin" src="' + encodeURI(artifacts.textAttachment.file) + '"></iframe>';
                return;
              }
              stage.innerHTML = '<div class="stage-empty">No preview for this event.</div>';
            }

            function labelForAttachment(attachment) {
              if (attachment.contentType === 'image/png') return 'Screenshot';
              if (attachment.contentType === 'text/html') return 'DOM';
              if (attachment.contentType === 'text/plain') return 'Log';
              if (attachment.contentType === 'application/vnd.selenide-trace-network+json') return 'Network';
              return attachment.name || 'Attachment';
            }

            function formatBytes(value) {
              if (value == null || value === '') return '-';
              const bytes = Number(value);
              if (!Number.isFinite(bytes) || bytes <= 0) return '-';
              if (bytes < 1024) return Math.round(bytes) + ' B';
              if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
              return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
            }

            function renderNetworkSection(title, content) {
              if (!content) return '';
              return `
                <section class="network-section">
                  <h3>${escapeHtml(title)}</h3>
                  <pre class="network-code">${escapeHtml(content)}</pre>
                </section>
              `;
            }

            function renderBodySection(title, content, file) {
              if (!content && !file) return '';
              return `
                <section class="network-section">
                  <h3>${escapeHtml(title)}</h3>
                  <pre class="network-code">${escapeHtml(content || 'Body captured in external file')}</pre>
                  ${file ? `<div class="network-links"><a class="network-link" href="${encodeURI(file)}" target="_blank" rel="noreferrer">Open full body</a></div>` : ''}
                </section>
              `;
            }

            function escapeHtml(value) {
              return String(value)
                .replaceAll('&', '&amp;')
                .replaceAll('<', '&lt;')
                .replaceAll('>', '&gt;')
                .replaceAll('"', '&quot;')
                .replaceAll("'", '&#39;');
            }
          </script>
        </body>
        </html>
        """
        .replace("__EVENTS__", eventsJson)
        .replace("__SUMMARY__", summaryJson);
  }

  private Map<String, Object> buildSummary(String testName, List<SelenideTraceEvent> events, ZoneId zoneId) {
    SelenideTraceEvent first = events.isEmpty() ? null : events.get(0);
    SelenideTraceEvent last = events.isEmpty() ? null : events.get(events.size() - 1);
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("testName", testName);
    summary.put("status", last == null ? "unknown" : last.status());
    summary.put("eventCount", events.size());
    summary.put("durationMs", first == null || last == null
        ? 0L
        : Math.max(last.finishedAtEpochMs() - first.startedAtEpochMs(), 0L));
    summary.put("startedAt", first == null ? "-" : formatInstant(first.startedAtEpochMs(), zoneId));
    summary.put("finishedAt", last == null ? "-" : formatInstant(last.finishedAtEpochMs(), zoneId));
    return summary;
  }

  private String formatInstant(long epochMs, ZoneId zoneId) {
    return TIME_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(zoneId));
  }

  private String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to serialize trace report data", exception);
    }
  }
}
