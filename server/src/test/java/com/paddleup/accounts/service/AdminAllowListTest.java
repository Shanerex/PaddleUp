package com.paddleup.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.paddleup.accounts.config.AdminProperties;
import com.paddleup.accounts.dto.UserId;
import com.paddleup.accounts.model.User;
import com.paddleup.accounts.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-2: only an account whose email is on the allow-list is an admin. */
class AdminAllowListTest {

  private final UserRepository users = mock(UserRepository.class);

  private UserId user(String email) {
    UUID id = UUID.randomUUID();
    when(users.findById(id))
        .thenReturn(
            Optional.of(
                new User(id, email, "Ann", "preset-01", null, Instant.now(), Instant.now())));
    return new UserId(id);
  }

  @Test
  void matchesTheEmailIgnoringCaseAndSurroundingSpaces() {
    var list = new AdminAllowList(new AdminProperties(List.of(" Boss@Example.TEST ")), users);

    assertThat(list.isAdmin(user("boss@example.test"))).isTrue();
    assertThat(list.isAdmin(user("BOSS@EXAMPLE.TEST"))).isTrue();
    assertThat(list.isAdmin(user("other@example.test"))).isFalse();
  }

  @Test
  void anEmptyListAdmitsNobody() {
    var list = new AdminAllowList(new AdminProperties(List.of()), users);

    assertThat(list.isAdmin(user("boss@example.test"))).isFalse();
  }

  @Test
  void aBlankEntryAdmitsNobodyAndAnUnknownUserIsNotAnAdmin() {
    var list = new AdminAllowList(new AdminProperties(List.of("", "  ")), users);

    assertThat(list.isAdmin(user(""))).isFalse();
    assertThat(list.isAdmin(new UserId(UUID.randomUUID()))).isFalse();
    assertThat(list.isAdmin(null)).isFalse();
  }
}
