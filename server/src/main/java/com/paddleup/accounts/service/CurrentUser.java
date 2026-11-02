package com.paddleup.accounts.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.accounts.exception.NotSignedInException;
import com.paddleup.accounts.model.AccountPrincipal;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The only way other modules learn who the caller is. Reads the session's security context; request
 * headers and parameters are never consulted.
 */
@Component
public class CurrentUser {

  public Optional<UserId> find() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.isAuthenticated()
        && auth.getPrincipal() instanceof AccountPrincipal p) {
      return Optional.of(new UserId(p.userId()));
    }
    return Optional.empty();
  }

  public UserId require() {
    return find().orElseThrow(NotSignedInException::new);
  }
}
