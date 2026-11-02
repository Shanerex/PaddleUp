package com.paddleup.accounts.config;

import com.paddleup.accounts.service.GoogleSignInService;
import com.paddleup.accounts.service.SignInSessionService;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * The single authentication gate. The caller is whoever the session says; request headers and
 * parameters never set the principal.
 */
@Configuration
class SecurityConfig {

  private static final String UNAUTHORISED_JSON = "{\"error\":\"unauthorised\"}";

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      GoogleSignInService googleSignIn,
      SignInSessionService sessions,
      AuthProperties props)
      throws Exception {
    var google = new GoogleLoginHandlers(googleSignIn, sessions, props);
    return http.csrf(AbstractHttpConfigurer::disable) // SameSite=Lax cookie and JSON-only writes
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/auth/email/**",
                        "/api/auth/sign-out",
                        "/api/oauth2/**",
                        "/api/login/oauth2/**",
                        "/error", // ERROR dispatch: keeps the original status for anonymous callers
                        "/actuator/health",
                        "/v3/api-docs",
                        "/v3/api-docs/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2Login(
            oauth ->
                oauth
                    .authorizationEndpoint(a -> a.baseUri("/api/oauth2/authorization"))
                    .redirectionEndpoint(r -> r.baseUri("/api/login/oauth2/code/*"))
                    .userInfoEndpoint(u -> u.oidcUserService(google.oidcUserService()))
                    .authorizedClientRepository(GoogleLoginHandlers.discardAuthorizedClients())
                    .successHandler(google.success())
                    .failureHandler(google.failure()))
        .exceptionHandling(e -> e.authenticationEntryPoint(jsonUnauthorised()))
        .logout(
            logout ->
                logout
                    .logoutRequestMatcher(new AntPathRequestMatcher("/api/auth/sign-out", "POST"))
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies(SessionConfig.COOKIE_NAME)
                    .logoutSuccessHandler(
                        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
        .build();
  }

  private static AuthenticationEntryPoint jsonUnauthorised() {
    return (request, response, ex) -> {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      response.setCharacterEncoding(StandardCharsets.UTF_8.name());
      response.getWriter().write(UNAUTHORISED_JSON);
    };
  }
}
