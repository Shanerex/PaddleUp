package com.paddleup.accounts.service;

import com.paddleup.accounts.model.AccountPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;

/** Turns a successful sign-in into a server-side session holding only the user id. */
@Service
public class SignInSessionService {

  private final HttpSessionSecurityContextRepository repository =
      new HttpSessionSecurityContextRepository();

  public void signIn(UUID userId, HttpServletRequest request, HttpServletResponse response) {
    var authentication =
        new UsernamePasswordAuthenticationToken(new AccountPrincipal(userId), null, List.of());
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    if (request.getSession(false) != null) {
      request.changeSessionId();
    }
    repository.saveContext(context, request, response);
    SecurityContextHolder.setContext(context);
  }
}
