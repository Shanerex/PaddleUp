package com.paddleup.accounts.controller;

import com.paddleup.accounts.config.AuthProperties;
import com.paddleup.accounts.dto.EmailStartRequest;
import com.paddleup.accounts.dto.EmailVerifyRequest;
import com.paddleup.accounts.dto.ProfileDto;
import com.paddleup.accounts.exception.InvalidRequestException;
import com.paddleup.accounts.model.User;
import com.paddleup.accounts.service.EmailSignInService;
import com.paddleup.accounts.service.ProfileService;
import com.paddleup.accounts.service.SignInSessionService;
import com.paddleup.accounts.util.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Email sign-in. Sign-out is handled by Spring Security at {@code POST /api/auth/sign-out}. */
@RestController
@RequestMapping("/api/auth/email")
class AuthController {

  private final EmailSignInService emailSignIn;
  private final SignInSessionService sessions;
  private final int trustedProxyHops;

  AuthController(
      EmailSignInService emailSignIn, SignInSessionService sessions, AuthProperties props) {
    this.emailSignIn = emailSignIn;
    this.sessions = sessions;
    this.trustedProxyHops = props.throttle().trustedProxyHops();
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = String.join(",", Collections.list(request.getHeaders("X-Forwarded-For")));
    return ClientIp.resolve(request.getRemoteAddr(), forwarded, trustedProxyHops);
  }

  /** Always 202 for a well-formed address, so it never reveals whether an account exists. */
  @PostMapping(value = "/start", consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.ACCEPTED)
  void start(@RequestBody EmailStartRequest request, HttpServletRequest httpRequest) {
    emailSignIn.start(request.email(), clientIp(httpRequest));
  }

  @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
  ProfileDto verify(
      @RequestBody EmailVerifyRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    User user;
    if (request.token() != null) {
      user = emailSignIn.redeemLink(request.token(), clientIp(httpRequest));
    } else if (request.email() != null && request.code() != null) {
      user = emailSignIn.redeemCode(request.email(), request.code(), clientIp(httpRequest));
    } else {
      throw new InvalidRequestException("Provide a token, or an email and a code");
    }
    // Only a successful redemption reaches here, so a failed attempt never opens a session.
    sessions.signIn(user.id(), httpRequest, httpResponse);
    return ProfileService.toDto(user);
  }
}
