package com.paddleup.rooms.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.junit.jupiter.api.Test;

class InviteCodesTest {

  @Test
  void codesAreTwelveCrockfordCharacters() {
    for (int i = 0; i < 200; i++) {
      assertThat(InviteCodes.generate(12)).matches("[0-9A-HJKMNP-TV-Z]{12}");
    }
  }

  @Test
  void codesDoNotRepeat() {
    var seen = new HashSet<String>();
    for (int i = 0; i < 5000; i++) {
      assertThat(seen.add(InviteCodes.generate(12))).isTrue();
    }
  }

  @Test
  void everyAlphabetCharacterTurnsUp() {
    var chars = new HashSet<Character>();
    for (int i = 0; i < 500; i++) {
      InviteCodes.generate(12).chars().forEach(c -> chars.add((char) c));
    }
    assertThat(chars).hasSize(InviteCodes.ALPHABET.length());
  }

  @Test
  void twelveCharactersCarrySixtyBits() {
    assertThat(InviteCodes.ALPHABET).hasSize(32);
    assertThat(12 * Integer.numberOfTrailingZeros(InviteCodes.ALPHABET.length())).isEqualTo(60);
  }

  @Test
  void normaliseFixesCaseSeparatorsAndLookAlikes() {
    assertThat(InviteCodes.normalise(" abcd-efgh jkmn ")).isEqualTo("ABCDEFGHJKMN");
    assertThat(InviteCodes.normalise("oIl0")).isEqualTo("0110");
  }

  @Test
  void normaliseRejectsCharactersThatCannotBeInACode() {
    assertThat(InviteCodes.normalise("ABC!")).isNull();
    assertThat(InviteCodes.normalise("ABCU")).isNull();
    assertThat(InviteCodes.normalise(null)).isNull();
    assertThat(InviteCodes.normalise("A".repeat(65))).isNull();
  }
}
