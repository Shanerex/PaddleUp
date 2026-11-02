package com.paddleup.accounts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.accounts.model.User;
import com.paddleup.support.AbstractPostgresIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class UserRepositoryIT extends AbstractPostgresIT {

  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  private User newUser() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
    UUID id = UUID.randomUUID();
    return new User(id, id + "@example.test", "Pat", "preset-01", null, now, now);
  }

  @Test
  void createAndFind() {
    User created = users.insert(newUser());

    assertThat(users.findById(created.id())).contains(created);
    assertThat(users.findByEmail(created.email())).contains(created);
    assertThat(users.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void updatesProfileFields() {
    User created = users.insert(newUser());
    Instant later = created.updatedAt().plusSeconds(5);

    users.updateDisplayName(created.id(), "New Name", later);
    users.updateAvatar(created.id(), "preset-02", later);
    users.updateGooglePictureUrl(created.id(), "https://example.test/p.png", later);

    User read = users.findById(created.id()).orElseThrow();
    assertThat(read.displayName()).isEqualTo("New Name");
    assertThat(read.avatar()).isEqualTo("preset-02");
    assertThat(read.googlePictureUrl()).isEqualTo("https://example.test/p.png");
    assertThat(read.updatedAt()).isEqualTo(later);
  }

  @Test
  void oneUserCanBeAssociatedWithTwoRooms() {
    jdbc.execute(
        "CREATE TABLE IF NOT EXISTS test_user_rooms ("
            + "user_id uuid NOT NULL REFERENCES users (id), room_id uuid NOT NULL,"
            + " PRIMARY KEY (user_id, room_id))");
    User user = users.insert(newUser());
    UUID roomA = UUID.randomUUID();
    UUID roomB = UUID.randomUUID();

    jdbc.update("INSERT INTO test_user_rooms (user_id, room_id) VALUES (?, ?)", user.id(), roomA);
    jdbc.update("INSERT INTO test_user_rooms (user_id, room_id) VALUES (?, ?)", user.id(), roomB);

    List<UUID> rooms =
        jdbc.queryForList(
            "SELECT room_id FROM test_user_rooms WHERE user_id = ?", UUID.class, user.id());
    assertThat(rooms).containsExactlyInAnyOrder(roomA, roomB);
  }
}
