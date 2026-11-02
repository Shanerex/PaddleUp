package com.paddleup.accounts.service;

import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.paddleup.accounts.config.AuthProperties;
import com.paddleup.support.AbstractPostgresIT;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class ConsoleEmailSenderIT extends AbstractPostgresIT {

  @RegisterExtension
  static WireMockExtension resend =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void resendProps(DynamicPropertyRegistry registry) {
    registry.add("paddleup.email.resend.base-url", resend::baseUrl);
    registry.add("paddleup.email.resend.api-key", () -> "re_test_key");
  }

  @Autowired EmailSignInService service;
  @Autowired JdbcTemplate jdbc;
  @Autowired AuthProperties auth;

  @Test
  void devProfileUsesPlainHttpCookies() {
    assertThat(auth.cookieSecure()).isFalse();
  }

  @Test
  void devProfileLogsLinkAndCodeAndNeverCallsResend(CapturedOutput output) {
    String email = "dev-" + UUID.randomUUID() + "@example.test";

    service.start(email, "203.0.113.9");

    Matcher link =
        Pattern.compile("/auth/verify\\?token=([A-Za-z0-9_-]+)").matcher(output.getAll());
    Matcher code = Pattern.compile("code: (\\d{6})").matcher(output.getAll());
    assertThat(link.find()).isTrue();
    assertThat(code.find()).isTrue();
    assertThat(service.redeemCode(email, code.group(1), "203.0.113.9").email()).isEqualTo(email);
    resend.verify(0, anyRequestedFor(anyUrl()));
  }
}
