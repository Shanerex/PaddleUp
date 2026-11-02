package com.paddleup.accounts.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientIpTest {

  @Test
  void ignoresForwardedHeaderWhenNoProxyIsTrusted() {
    assertThat(ClientIp.resolve("10.0.0.1", "1.2.3.4", 0)).isEqualTo("10.0.0.1");
  }

  @Test
  void usesTheNthEntryFromTheRight() {
    assertThat(ClientIp.resolve("10.0.0.1", "6.6.6.6, 1.2.3.4, 9.9.9.9", 1)).isEqualTo("9.9.9.9");
    assertThat(ClientIp.resolve("10.0.0.1", "6.6.6.6, 1.2.3.4, 9.9.9.9", 2)).isEqualTo("1.2.3.4");
  }

  @Test
  void fallsBackToThePeerWhenTheHeaderIsMissingOrShort() {
    assertThat(ClientIp.resolve("10.0.0.1", null, 1)).isEqualTo("10.0.0.1");
    assertThat(ClientIp.resolve("10.0.0.1", " ", 1)).isEqualTo("10.0.0.1");
    assertThat(ClientIp.resolve("10.0.0.1", "1.2.3.4", 2)).isEqualTo("10.0.0.1");
  }
}
