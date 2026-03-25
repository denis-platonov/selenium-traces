package dev.codex.tracing;

import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.enabled;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.closeWebDriver;
import static com.codeborne.selenide.Selenide.open;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SelenideWaitSuiteST {
  @RegisterExtension
  static final SelenideTraceExtension trace = new SelenideTraceExtension();

  @BeforeAll
  static void configureBrowser() {
    BrowserTestSupport.configureSelenideChrome();
  }

  @AfterEach
  void tearDown() {
    closeWebDriver();
  }

  @Test
  void waitsForVisibilityAndReadsText() {
    open(BrowserTestSupport.traceCoveragePageUrl());

    $("#pageTitle").shouldHave(exactText("Trace Coverage Playground"));
    $("#delayedVisible").shouldBe(visible).shouldHave(exactText("Now visible"));

    assertEquals("Now visible", $("#delayedVisible").getText());
  }

  @Test
  void waitsForEnabledAndClicks() {
    open(BrowserTestSupport.traceCoveragePageUrl());

    $("#delayedEnable").shouldBe(enabled).click();
    $("#buttonStatus").shouldHave(exactText("Enabled button clicked"));
  }

  @Test
  void waitsForPresenceAndReadsAttribute() {
    open(BrowserTestSupport.traceCoveragePageUrl());

    $("#lateElement").should(exist).shouldHave(attribute("data-role", "late"));

    assertEquals("late", $("#lateElement").getAttribute("data-role"));
  }
}
