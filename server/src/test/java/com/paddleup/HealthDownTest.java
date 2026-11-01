package com.paddleup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Own container and context: stopping the database here must not affect other tests. */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.hikari.connection-timeout=1000")
@Testcontainers
class HealthDownTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

  @Autowired TestRestTemplate rest;

  @Test
  void healthIsNotUpWhenDatabaseStopped() {
    assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    postgres.stop();

    var response = rest.getForEntity("/actuator/health", String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).doesNotContain("\"status\":\"UP\"");
  }
}
