package com.paddleup.accounts.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.CookieSerializer.CookieValue;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-sends the session cookie on every request that carries a valid session, so its Max-Age slides
 * with the server session instead of counting from sign-in. Spring Session only writes the cookie
 * when the session id changes.
 *
 * <p>Ordered just after SessionRepositoryFilter, ahead of the rest of the chain. A sign-in that
 * rotates the id or a sign-out that clears the cookie writes its own Set-Cookie later, and the
 * browser applies the last one.
 */
class SessionCookieRenewalFilter extends OncePerRequestFilter {

  private final CookieSerializer cookieSerializer;

  SessionCookieRenewalFilter(CookieSerializer cookieSerializer) {
    this.cookieSerializer = cookieSerializer;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.isRequestedSessionIdValid()) {
      cookieSerializer.writeCookieValue(
          new CookieValue(request, response, request.getRequestedSessionId()));
    }
    chain.doFilter(request, response);
  }
}
