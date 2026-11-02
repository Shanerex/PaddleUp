package com.paddleup.accounts.service;

import com.paddleup.accounts.config.ResendProperties;
import com.paddleup.accounts.exception.EmailDeliveryException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Sends through the Resend HTTP API. Active in every profile except dev. */
@Component
@Profile("!dev")
class ResendEmailSender implements EmailSender {

  private final ResendProperties props;
  private final RestClient client;

  ResendEmailSender(ResendProperties props) {
    this.props = props;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.client = RestClient.builder().baseUrl(props.baseUrl()).requestFactory(factory).build();
  }

  @Override
  public void send(OutgoingEmail email) {
    try {
      client
          .post()
          .uri("/emails")
          .header("Authorization", "Bearer " + props.apiKey())
          .body(
              Map.of(
                  "from", props.from(),
                  "to", List.of(email.to()),
                  "subject", email.subject(),
                  "text", email.text()))
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientException e) {
      throw new EmailDeliveryException("Could not send email", e);
    }
  }
}
