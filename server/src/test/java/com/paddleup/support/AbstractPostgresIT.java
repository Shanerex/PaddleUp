package com.paddleup.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Shares one Postgres container across integration tests; tests must use unique data. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
// Every test shares one client address, so the per-address limits are lifted here; the throttle
// tests lower them again.
@TestPropertySource(
    properties = {
      "paddleup.auth.throttle.start-per-ip=100000",
      "paddleup.auth.throttle.failed-per-ip=100000",
      "paddleup.rooms.join-throttle.failed-per-ip=100000"
    })
public abstract class AbstractPostgresIT {

  // Every distinct Spring context stays cached with its own connection pool, so the default of 100
  // connections runs out once enough test configurations exist.
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17").withCommand("postgres", "-c", "max_connections=400");

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
