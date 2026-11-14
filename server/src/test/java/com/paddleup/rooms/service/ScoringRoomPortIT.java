package com.paddleup.rooms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.rooms.AbstractRoomsIT;
import com.paddleup.rooms.dto.RoomState;
import com.paddleup.rooms.exception.RoomForbiddenException;
import com.paddleup.rooms.exception.RoomNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** AC-10, AC-13: the one port scoring uses to read a room. */
class ScoringRoomPortIT extends AbstractRoomsIT {

  @Autowired ScoringRoomPort port;
  @Autowired AppearanceRuleBackfill backfill;

  Person commissioner;
  Person manager;
  String roomId;

  @BeforeEach
  void room() {
    importSeason("cricket-t20", SEASON_CSV);
    commissioner = person();
    roomId = createRoom(commissioner);
    manager = insertManager(roomId, "sky");
  }

  private UUID id() {
    return UUID.fromString(roomId);
  }

  private UserId user(Person who) {
    return new UserId(userId(who));
  }

  @Test
  void accessGivesMembersTheirStandingAndANonMemberIs404() {
    assertThat(port.access(id(), user(commissioner)).commissioner()).isTrue();
    var asManager = port.access(id(), user(manager));
    assertThat(asManager.commissioner()).isFalse();
    assertThat(asManager.state()).isEqualTo(RoomState.SETUP);

    assertThatThrownBy(() -> port.access(id(), user(person())))
        .isInstanceOf(RoomNotFoundException.class);
  }

  @Test
  void requireCommissionerIs404ForANonMemberAnd403ForAManager() {
    assertThat(port.requireCommissioner(id(), user(commissioner)).commissioner()).isTrue();

    assertThatThrownBy(() -> port.requireCommissioner(id(), user(manager)))
        .isInstanceOf(RoomForbiddenException.class);
    assertThatThrownBy(() -> port.requireCommissioner(id(), user(person())))
        .isInstanceOf(RoomNotFoundException.class);
    assertThatThrownBy(() -> port.requireCommissioner(UUID.randomUUID(), user(commissioner)))
        .isInstanceOf(RoomNotFoundException.class);
  }

  @Test
  void ruleInputsComeFromTheRoomSnapshotNotTheLivePack() {
    var inputs = port.ruleInputs(id());

    assertThat(inputs.packId()).isEqualTo("cricket-t20");
    assertThat(inputs.packVersion()).isEqualTo("1.1.0");
    assertThat(inputs.roles()).extracting("id").containsExactly("BAT", "BOWL", "AR", "WK");
    assertThat(inputs.stats()).isNotEmpty();
    assertThat(inputs.defaultRules().rules()).isNotEmpty();
    assertThat(inputs.appearanceRule().stat()).isEqualTo("in-playing-xi");
    assertThat(inputs.datasetId()).isNotNull();

    // The room's stored copy wins over the pack on disk.
    jdbc.update(
        "UPDATE rooms SET pack_version = '0.9.0', config = jsonb_set(config, '{scoringRules}',"
            + " '{\"rules\":[{\"type\":\"appearance-bonus\",\"id\":\"only\",\"points\":7}]}'),"
            + " pack_snapshot = jsonb_set(pack_snapshot, '{appearanceRule,stat}', '\"seen\"')"
            + " WHERE id = ?",
        id());
    var changed = port.ruleInputs(id());

    assertThat(changed.packVersion()).isEqualTo("0.9.0");
    assertThat(changed.defaultRules().rules()).extracting("id").containsExactly("only");
    assertThat(changed.appearanceRule().stat()).isEqualTo("seen");
  }

  @Test
  void aRoomWithoutAnAppearanceRuleIsBackfilledOnceAndThenReadsOnlyItsSnapshot() {
    jdbc.update(
        "UPDATE rooms SET pack_snapshot = pack_snapshot - 'appearanceRule' WHERE id = ?", id());
    assertThat(port.ruleInputs(id()).appearanceRule()).isNull();

    assertThat(backfill.backfillMissing()).isEqualTo(1);
    assertThat(port.ruleInputs(id()).appearanceRule().stat()).isEqualTo("in-playing-xi");

    // Once written, the snapshot is the room's own: a second run leaves an edited rule alone.
    jdbc.update(
        "UPDATE rooms SET"
            + " pack_snapshot = jsonb_set(pack_snapshot, '{appearanceRule,stat}', '\"seen\"')"
            + " WHERE id = ?",
        id());
    assertThat(backfill.backfillMissing()).isZero();
    assertThat(port.ruleInputs(id()).appearanceRule().stat()).isEqualTo("seen");
  }

  @Test
  void aRoomWithNoPoolHasNoDataset() {
    jdbc.update("DELETE FROM room_pool_players WHERE room_id = ?", id());
    jdbc.update("DELETE FROM room_pools WHERE room_id = ?", id());

    assertThat(port.ruleInputs(id()).datasetId()).isNull();
    assertThat(port.players(id())).isEmpty();
  }

  @Test
  void playersCarrySourceIdAndRoleFromThePoolAndAddedPlayersHaveNoSource() {
    jdbc.update(
        "INSERT INTO room_pool_players (id, room_id, source_player_id, origin, name, country,"
            + " role_key, tag_keys, base_price, set_name, provider_player_id, position)"
            + " VALUES (gen_random_uuid(), ?, NULL, 'added', 'Hand Added', 'Chile', 'WK', '{}',"
            + " 20, 'Marquee', NULL, 99)",
        id());

    var players = port.players(id());

    assertThat(players).hasSize(7);
    assertThat(players.stream().filter(p -> p.sourcePlayerId() != null)).hasSize(6);
    var added = players.stream().filter(p -> p.sourcePlayerId() == null).toList();
    assertThat(added).hasSize(1);
    assertThat(added.get(0).role()).isEqualTo("WK");
    assertThat(players).extracting("role").contains("BAT", "BOWL", "AR", "WK");
  }

  @Test
  void roomsOnDatasetListsEveryRoomCopiedFromIt() {
    UUID dataset = port.ruleInputs(id()).datasetId();
    String second = createRoom(commissioner);
    importSeason("cricket-t20", SEASON_CSV);
    String third = createRoom(commissioner);

    assertThat(port.roomsOnDataset(dataset))
        .containsExactlyInAnyOrder(id(), UUID.fromString(second))
        .doesNotContain(UUID.fromString(third));
  }
}
