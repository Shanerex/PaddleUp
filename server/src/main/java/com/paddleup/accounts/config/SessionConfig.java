package com.paddleup.accounts.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;

/**
 * The session cookie: HttpOnly, SameSite=Lax, Secure outside dev. It lives as long as the server
 * session (spring.session.timeout), so sign-in survives a browser restart.
 */
@Configuration
class SessionConfig {

  static final String COOKIE_NAME = "pu_session";

  @Bean
  CookieSerializer cookieSerializer(
      AuthProperties props, @Value("${spring.session.timeout}") Duration sessionTimeout) {
    DefaultCookieSerializer serializer = new DefaultCookieSerializer();
    serializer.setCookieName(COOKIE_NAME);
    serializer.setCookiePath("/");
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    serializer.setUseSecureCookie(props.cookieSecure());
    serializer.setCookieMaxAge(Math.toIntExact(sessionTimeout.toSeconds()));
    return serializer;
  }

  /** Keeps the cookie's Max-Age sliding with the server session; see the filter. */
  @Bean
  FilterRegistrationBean<SessionCookieRenewalFilter> sessionCookieRenewalFilter(
      CookieSerializer cookieSerializer) {
    var registration =
        new FilterRegistrationBean<>(new SessionCookieRenewalFilter(cookieSerializer));
    registration.setOrder(SessionRepositoryFilter.DEFAULT_ORDER + 1);
    return registration;
  }
}
