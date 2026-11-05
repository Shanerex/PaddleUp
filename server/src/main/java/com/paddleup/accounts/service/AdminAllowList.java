package com.paddleup.accounts.service;

import com.paddleup.accounts.config.AdminProperties;
import com.paddleup.accounts.dto.UserId;
import com.paddleup.accounts.repository.UserRepository;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Who is an app admin: an account whose email is on the server-config allow-list. The only way
 * other modules ask the question; an empty list admits nobody.
 */
@Service
public class AdminAllowList {

  private final Set<String> emails;
  private final UserRepository users;

  public AdminAllowList(AdminProperties properties, UserRepository users) {
    this.emails =
        properties.emails().stream()
            .filter(e -> e != null)
            .map(AdminAllowList::normalise)
            .filter(e -> !e.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    this.users = users;
  }

  public boolean isAdmin(UserId user) {
    if (user == null || emails.isEmpty()) {
      return false;
    }
    return users
        .findById(user.value())
        .map(u -> emails.contains(normalise(u.email())))
        .orElse(false);
  }

  private static String normalise(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
