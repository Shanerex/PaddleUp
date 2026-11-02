package com.paddleup.accounts.config;

import com.paddleup.accounts.exception.UnverifiedEmailException;
import com.paddleup.accounts.model.User;
import com.paddleup.accounts.service.GoogleProfile;
import com.paddleup.accounts.service.GoogleSignInService;
import com.paddleup.accounts.service.SignInSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Turns Google's authorization-code result into a PaddleUp session, or a redirect with an error.
 */
class GoogleLoginHandlers {

  private static final Logger log = LoggerFactory.getLogger(GoogleLoginHandlers.class);

  private final GoogleSignInService google;
  private final SignInSessionService sessions;
  private final AuthProperties props;

  GoogleLoginHandlers(
      GoogleSignInService google, SignInSessionService sessions, AuthProperties props) {
    this.google = google;
    this.sessions = sessions;
    this.props = props;
  }

  /** Builds the user from ID-token claims only; no user-info call. */
  org.springframework.security.oauth2.client.userinfo.OAuth2UserService<
          org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest, OidcUser>
      oidcUserService() {
    return request -> new DefaultOidcUser(List.of(), request.getIdToken());
  }

  AuthenticationSuccessHandler success() {
    return (request, response, authentication) -> {
      try {
        OidcUser oidc = (OidcUser) authentication.getPrincipal();
        User user =
            google.signIn(
                new GoogleProfile(
                    oidc.getSubject(),
                    oidc.getEmail(),
                    Boolean.TRUE.equals(oidc.getEmailVerified()),
                    oidc.getFullName(),
                    oidc.getPicture()));
        // Replaces the OIDC authentication so the session holds the user id only.
        sessions.signIn(user.id(), request, response);
        response.sendRedirect(props.webBaseUrl() + "/profile");
      } catch (UnverifiedEmailException e) {
        reject(request, response, "unverified_email");
      } catch (RuntimeException e) {
        log.error("Google sign-in failed after authentication", e);
        reject(request, response, "google");
      }
    };
  }

  AuthenticationFailureHandler failure() {
    return (request, response, exception) -> {
      log.warn("Google sign-in rejected: {}", exception.getMessage());
      response.sendRedirect(props.webBaseUrl() + "/sign-in?error=google");
    };
  }

  /** Drops the half-built session so an OIDC-only authentication never survives a rejection. */
  private void reject(HttpServletRequest request, HttpServletResponse response, String error)
      throws IOException {
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    SecurityContextHolder.clearContext();
    response.sendRedirect(props.webBaseUrl() + "/sign-in?error=" + error);
  }

  /** Google access and refresh tokens are not needed after sign-in, so none are kept. */
  static OAuth2AuthorizedClientRepository discardAuthorizedClients() {
    return new OAuth2AuthorizedClientRepository() {
      @Override
      @SuppressWarnings("unchecked")
      public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
          String clientRegistrationId, Authentication principal, HttpServletRequest request) {
        return null;
      }

      @Override
      public void saveAuthorizedClient(
          OAuth2AuthorizedClient client,
          Authentication principal,
          HttpServletRequest request,
          HttpServletResponse response) {}

      @Override
      public void removeAuthorizedClient(
          String clientRegistrationId,
          Authentication principal,
          HttpServletRequest request,
          HttpServletResponse response) {}
    };
  }
}
