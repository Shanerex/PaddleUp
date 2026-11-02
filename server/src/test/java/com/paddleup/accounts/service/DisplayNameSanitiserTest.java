package com.paddleup.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.accounts.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

class DisplayNameSanitiserTest {

  @Test
  void stripsMarkupAndScriptContent() {
    assertThat(DisplayNameSanitiser.requireValid("<b>Ann</b>")).isEqualTo("Ann");
    assertThat(DisplayNameSanitiser.requireValid("<script>alert(1)</script>Bob")).isEqualTo("Bob");
    assertThat(DisplayNameSanitiser.requireValid("<img src=x onerror=alert(1)>Cy")).isEqualTo("Cy");
  }

  @Test
  void escapedMarkupDoesNotComeBackAsMarkup() {
    assertThat(DisplayNameSanitiser.requireValid("&lt;b&gt;Dee&lt;/b&gt;"))
        .doesNotContain("<")
        .doesNotContain(">");
  }

  @Test
  void stripsControlAndInvisibleCharactersAndCollapsesWhitespace() {
    assertThat(DisplayNameSanitiser.requireValid("Ann\u0000\u0007Lee")).isEqualTo("AnnLee");
    assertThat(DisplayNameSanitiser.requireValid("  Ann \t\n  Lee  ")).isEqualTo("Ann Lee");
    assertThat(DisplayNameSanitiser.requireValid("A​n‮n")).isEqualTo("Ann");
  }

  @Test
  void normalisesToNfc() {
    assertThat(DisplayNameSanitiser.requireValid("Café")).isEqualTo("Café");
  }

  @Test
  void lengthIsCheckedAfterSanitising() {
    assertThat(DisplayNameSanitiser.requireValid("ab")).isEqualTo("ab");
    assertThat(DisplayNameSanitiser.requireValid("a".repeat(30))).hasSize(30);
    assertThat(DisplayNameSanitiser.requireValid("<b>" + "a".repeat(30) + "</b>")).hasSize(30);
    assertThatThrownBy(() -> DisplayNameSanitiser.requireValid("a"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> DisplayNameSanitiser.requireValid("a".repeat(31)))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> DisplayNameSanitiser.requireValid("<b></b> \u0000"))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> DisplayNameSanitiser.requireValid(null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void countsCodePointsNotUtf16Units() {
    assertThat(DisplayNameSanitiser.requireValid("😀".repeat(30))).isNotEmpty();
    assertThatThrownBy(() -> DisplayNameSanitiser.requireValid("😀".repeat(31)))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  void rejectsOversizedInputBeforeParsing() {
    assertThatThrownBy(() -> DisplayNameSanitiser.requireValid("a".repeat(5000)))
        .isInstanceOf(InvalidRequestException.class);
  }
}
