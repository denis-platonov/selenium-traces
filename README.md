# Selenide Tracing

This project adds Playwright-style trace artifacts to Selenide tests.

Each test run writes a folder and zip archive under `target/selenide-traces` containing:

- `trace.trace`: JSONL event stream with action timing, status, and attachments
- `index.html`: self-contained local trace viewer with timeline, previews, and attachment links
- screenshots after each Selenide action
- HTML snapshots after each Selenide action
- browser, driver, and performance logs when the browser exposes them

## Test Pyramid

- Unit tests: `mvn test`
- Integration tests: `mvn verify`
- System tests: `mvn verify -DrunSystemTests=true`

Naming conventions:

- `*Test.java` runs in Surefire as unit tests
- `*IT.java` runs in Failsafe as integration tests
- `*ST.java` runs in Failsafe when `-DrunSystemTests=true`

## Usage

Add the extension to your JUnit 5 test:

```java
import static com.codeborne.selenide.Selenide.*;

import dev.codex.tracing.SelenideTrace;
import dev.codex.tracing.SelenideTraceExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LoginTest {
  @RegisterExtension
  static final SelenideTraceExtension trace = new SelenideTraceExtension();

  @Test
  void login() {
    SelenideTrace.step("Open login page", () -> open("https://example.com/login"));
    SelenideTrace.step("Submit credentials", () -> {
      $("#email").setValue("user@example.com");
      $("#password").setValue("secret");
      $("button[type=submit]").click();
    });
    SelenideTrace.step("Assert user is signed in", () -> $(".profile").shouldBe(visible));
  }
}
```

## Notes

- The trace listener is thread-local, so parallel test execution creates separate artifacts.
- Browser log capture depends on the driver and browser.
- Screenshots and DOM snapshots are enabled per Selenide event by default. Use `SelenideTraceConfig` to disable heavy captures.

## Publishing

GitHub Actions workflows are included for:

- CI: [`.github/workflows/ci.yml`](/C:/Users/plato/Documents/New%20project/.github/workflows/ci.yml)
- publishing to GitHub Packages and Maven Central: [`.github/workflows/publish.yml`](/C:/Users/plato/Documents/New%20project/.github/workflows/publish.yml)

Repository variables expected by the publish workflow:

- `PUBLISHER_ID`
- `PUBLISHER_NAME`
- `PUBLISHER_EMAIL`

Repository secrets expected by the Maven Central publish job:

- `CENTRAL_TOKEN_USERNAME`
- `CENTRAL_TOKEN_PASSWORD`
- `GPG_PRIVATE_KEY`
- `GPG_PASSPHRASE`
