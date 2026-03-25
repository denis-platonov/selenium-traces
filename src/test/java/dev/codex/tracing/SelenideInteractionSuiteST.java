package dev.codex.tracing;

import static com.codeborne.selenide.CollectionCondition.size;
import static com.codeborne.selenide.Condition.attribute;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.closeWebDriver;
import static com.codeborne.selenide.Selenide.open;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.codeborne.selenide.ElementsCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SelenideInteractionSuiteST {
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
  void capturesHoverTypeClickAndAssertions() {
    open(BrowserTestSupport.traceCoveragePageUrl());

    $("#hoverTarget").hover();
    $("#hoverStatus").shouldHave(exactText("Hovered"));

    $("#nameInput").setValue("Codex");
    $("#typedValue").shouldHave(exactText("Codex"));

    $("#clickTarget").click();
    $("#clickStatus").shouldHave(exactText("Clicked"));
  }

  @Test
  void findsElementsReadsTextAndAttributes() {
    open(BrowserTestSupport.traceCoveragePageUrl());

    ElementsCollection items = $$(".item");
    items.shouldHave(size(3));
    items.findBy(exactText("Beta")).shouldBe(visible).shouldHave(attribute("data-kind", "secondary"));

    assertEquals("Beta", items.findBy(exactText("Beta")).getText());
    assertEquals("ready", $("#attributeTarget").getAttribute("data-state"));
  }
}
