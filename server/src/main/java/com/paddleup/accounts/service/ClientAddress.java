package com.paddleup.accounts.service;

import com.paddleup.accounts.config.AuthProperties;
import com.paddleup.accounts.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import org.springframework.stereotype.Component;

/**
 * The one way to learn which client address a request came from, behind the configured number of
 * trusted proxies. A client cannot pick its own address by sending {@code X-Forwarded-For}.
 */
@Component
public class ClientAddress {

  private final int trustedProxyHops;

  ClientAddress(AuthProperties props) {
    this.trustedProxyHops = props.throttle().trustedProxyHops();
  }

  /** The client address of the request. */
  public String resolve(HttpServletRequest request) {
    String forwarded = String.join(",", Collections.list(request.getHeaders("X-Forwarded-For")));
    return ClientIp.resolve(request.getRemoteAddr(), forwarded, trustedProxyHops);
  }

  /**
   * The address to count rate limits against: an IPv6 address collapses to its /64 so one
   * subscriber cannot rotate through its range.
   */
  public String limitKey(HttpServletRequest request) {
    return AuthThrottle.ipKey(resolve(request));
  }
}
