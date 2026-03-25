package dev.codex.tracing;

import static com.codeborne.selenide.CollectionCondition.sizeGreaterThanOrEqual;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.closeWebDriver;
import static com.codeborne.selenide.Selenide.open;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.codeborne.selenide.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class HerokuInternetTraceST {
  @RegisterExtension
  static final SelenideTraceExtension trace = new SelenideTraceExtension();

  @BeforeAll
  static void configureBrowser() {
    Configuration.browser = "chrome";
    Configuration.headless = true;
    Configuration.timeout = 10_000;
    Configuration.pageLoadTimeout = 20_000;
    Configuration.browserSize = "1440x900";
    Configuration.webdriverLogsEnabled = true;
    Configuration.browserCapabilities = BrowserTestSupport.chromeOptions();
  }

  @AfterEach
  void tearDown() {
    closeWebDriver();
  }

  @Test
  void clickOpensNewWindow() {
    SelenideTrace.step("Open multiple windows page", () ->
        open("https://the-internet.herokuapp.com/windows"));

    SelenideTrace.step("Validate source page loaded", () ->
        $("h3").shouldHave(exactText("Opening a new window")));

    SelenideTrace.step("Click the link that opens a new window", () ->
        $("a[href='/windows/new']").shouldHave(exactText("Click Here")).click());

    SelenideTrace.step("Assert a second window exists", () ->
        assertTrue($$("body").size() >= 1));

    SelenideTrace.step("Switch focus and validate new page", () -> {
      assertTrue(com.codeborne.selenide.WebDriverRunner.getWebDriver().getWindowHandles().size() > 1);
      com.codeborne.selenide.Selenide.switchTo().window(1);
      $("h3").shouldHave(exactText("New Window"));
      assertTrue(com.codeborne.selenide.WebDriverRunner.url().endsWith("/windows/new"));
      $$("body").shouldHave(sizeGreaterThanOrEqual(1));
    });
  }
}
