package dev.codex.tracing;

import com.codeborne.selenide.Configuration;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.openqa.selenium.chrome.ChromeOptions;

final class BrowserTestSupport {
  private BrowserTestSupport() {
  }

  static void configureSelenideChrome() {
    Configuration.browser = "chrome";
    Configuration.headless = true;
    Configuration.timeout = 10_000;
    Configuration.pageLoadTimeout = 20_000;
    Configuration.browserSize = "1440x900";
    Configuration.webdriverLogsEnabled = true;
    Configuration.browserCapabilities = chromeOptions();
  }

  static ChromeOptions chromeOptions() {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new", "--window-size=1440,900", "--disable-dev-shm-usage", "--no-sandbox");
    options.setCapability("goog:loggingPrefs", Map.of("browser", "ALL", "performance", "ALL"));
    return options;
  }

  static String traceCoveragePageUrl() {
    String html = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <title>Trace Coverage Playground</title>
          <style>
            body { font-family: sans-serif; margin: 24px; }
            #delayedVisible { display: none; }
            #playground { display: grid; gap: 16px; max-width: 720px; }
            #hoverTarget, #clickTarget, #delayedEnable {
              display: inline-flex;
              align-items: center;
              justify-content: center;
              min-height: 40px;
              padding: 0 16px;
              border: 1px solid #888;
              border-radius: 10px;
              background: #f5f5f5;
            }
            ul { padding-left: 20px; }
          </style>
        </head>
        <body>
          <div id="playground">
            <h1 id="pageTitle">Trace Coverage Playground</h1>
            <div id="delayedVisible" data-state="visible-ready">Now visible</div>
            <button id="delayedEnable" type="button" disabled data-state="disabled">Delayed enable button</button>
            <div id="buttonStatus">Idle</div>
            <div id="hoverTarget">Hover target</div>
            <div id="hoverStatus">Idle</div>
            <button id="clickTarget" type="button">Click target</button>
            <div id="clickStatus">Not clicked</div>
            <label for="nameInput">Name</label>
            <input id="nameInput" type="text" autocomplete="off">
            <div id="typedValue"></div>
            <div id="attributeTarget" data-role="alpha" data-state="ready">Attribute target</div>
            <ul id="items">
              <li class="item" data-kind="primary">Alpha</li>
              <li class="item" data-kind="secondary">Beta</li>
              <li class="item" data-kind="tertiary">Gamma</li>
            </ul>
          </div>
          <script>
            const delayedVisible = document.getElementById('delayedVisible');
            const delayedEnable = document.getElementById('delayedEnable');
            const buttonStatus = document.getElementById('buttonStatus');
            const hoverTarget = document.getElementById('hoverTarget');
            const hoverStatus = document.getElementById('hoverStatus');
            const clickTarget = document.getElementById('clickTarget');
            const clickStatus = document.getElementById('clickStatus');
            const nameInput = document.getElementById('nameInput');
            const typedValue = document.getElementById('typedValue');

            setTimeout(() => {
              delayedVisible.style.display = 'block';
            }, 250);

            setTimeout(() => {
              delayedEnable.disabled = false;
              delayedEnable.dataset.state = 'ready';
            }, 450);

            setTimeout(() => {
              const lateElement = document.createElement('div');
              lateElement.id = 'lateElement';
              lateElement.dataset.role = 'late';
              lateElement.dataset.state = 'inserted';
              lateElement.textContent = 'Late element';
              document.getElementById('playground').appendChild(lateElement);
            }, 650);

            delayedEnable.addEventListener('click', () => {
              buttonStatus.textContent = 'Enabled button clicked';
            });

            hoverTarget.addEventListener('mouseenter', () => {
              hoverStatus.textContent = 'Hovered';
            });

            clickTarget.addEventListener('click', () => {
              clickStatus.textContent = 'Clicked';
            });

            nameInput.addEventListener('input', event => {
              typedValue.textContent = event.target.value;
            });
          </script>
        </body>
        </html>
        """;
    return "data:text/html;base64," + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
  }
}
