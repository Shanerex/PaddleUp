package com.paddleup.accounts.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.paddleup.accounts.model.AccountPrincipal;
import com.paddleup.accounts.model.User;
import com.paddleup.accounts.repository.UserRepository;
import com.paddleup.accounts.service.SignInSessionService;
import com.paddleup.support.AbstractPostgresIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class SecurityIT extends AbstractPostgresIT {

  @Autowired MockMvc mvc;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;
  @Autowired SignInSessionService signIn;

  private User newUser(String name) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID id = UUID.randomUUID();
    return users.insert(new User(id, id + "@example.test", name, "preset-01", null, now, now));
  }

  private int sessionRows() {
    return jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class);
  }

  @Test
  void noSessionGivesJson401AndCreatesNoSession() throws Exception {
    int before = sessionRows();

    mvc.perform(get("/api/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("unauthorised"));

    assertThat(sessionRows()).isEqualTo(before);
  }

  @Test
  void healthAndApiDocsNeedNoSession() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  @Test
  void spoofedUserIdHeadersAndParamsStillResolveToTheSessionUser() throws Exception {
    User me = newUser("Me Myself");
    User other = newUser("Someone Else");
    var auth =
        new UsernamePasswordAuthenticationToken(new AccountPrincipal(me.id()), null, List.of());

    mvc.perform(
            get("/api/me")
                .with(authentication(auth))
                .header("X-User-Id", other.id().toString())
                .header("X-Forwarded-User", other.id().toString())
                .param("userId", other.id().toString())
                .param("user_id", other.id().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(me.id().toString()))
        .andExpect(jsonPath("$.displayName").value("Me Myself"));
  }

  @Test
  void signInStoresOnlyTheUserIdAndRotatesAnExistingSession() {
    User me = newUser("Rotating");
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    String oldId = request.getSession(true).getId();

    signIn.signIn(me.id(), request, response);

    var session = request.getSession(false);
    assertThat(session.getId()).isNotEqualTo(oldId);
    var context =
        (org.springframework.security.core.context.SecurityContext)
            session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    assertThat(context.getAuthentication().getPrincipal()).isEqualTo(new AccountPrincipal(me.id()));
    assertThat(context.getAuthentication().isAuthenticated()).isTrue();
  }
}
