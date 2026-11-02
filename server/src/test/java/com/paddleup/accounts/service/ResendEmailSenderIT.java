package com.paddleup.accounts.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.paddleup.accounts.exception.EmailDeliveryException;
import com.paddleup.support.AbstractPostgresIT;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

class ResendEmailSenderIT extends AbstractPostgresIT {

  @RegisterExtension
  static WireMockExtension resend =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @DynamicPropertySource
  static void resendProps(DynamicPropertyRegistry registry) {
    registry.add("paddleup.email.resend.base-url", resend::baseUrl);
    registry.add("paddleup.email.resend.api-key", () -> "re_test_key");
    registry.add("paddleup.email.resend.from", () -> "PaddleUp <login@example.test>");
  }

  @Autowired EmailSignInService service;

  @Test
  void nonDevProfileSendsOneMessageThroughResend() {
    resend.resetAll();
    resend.stubFor(
        post(urlEqualTo("/emails"))
            .willReturn(aResponse().withStatus(200).withBody("{\"id\":\"1\"}")));
    String email = "prod-" + UUID.randomUUID() + "@example.test";

    service.start(email, "203.0.113.9");

    resend.verify(
        1,
        postRequestedFor(urlEqualTo("/emails"))
            .withHeader("Authorization", equalTo("Bearer re_test_key"))
            .withRequestBody(containing(email))
            .withRequestBody(containing("/auth/verify?token=")));
  }

  @Test
  void resendFailureSurfacesAsDeliveryError() {
    resend.resetAll();
    resend.stubFor(post(urlEqualTo("/emails")).willReturn(aResponse().withStatus(500)));

    assertThatThrownBy(
            () -> service.start("fail-" + UUID.randomUUID() + "@example.test", "203.0.113.9"))
        .isInstanceOf(EmailDeliveryException.class);
    assertThat(resend.getAllServeEvents()).hasSize(1);
  }
}
