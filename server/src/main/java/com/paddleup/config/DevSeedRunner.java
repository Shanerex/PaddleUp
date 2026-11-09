package com.paddleup.config;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.accounts.service.DevAccountSeeder;
import com.paddleup.packs.dto.PackSummaryDto;
import com.paddleup.packs.service.PackService;
import com.paddleup.rooms.dto.CreateRoomRequest;
import com.paddleup.rooms.dto.FranchiseRequest;
import com.paddleup.rooms.dto.InviteDto;
import com.paddleup.rooms.dto.JoinRequest;
import com.paddleup.rooms.dto.RoomDto;
import com.paddleup.rooms.dto.RoomSummaryDto;
import com.paddleup.rooms.service.FranchiseService;
import com.paddleup.rooms.service.InviteService;
import com.paddleup.rooms.service.JoinService;
import com.paddleup.rooms.service.RoomService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dev-only test data: four accounts and one room in {@code setup} with four franchises, ready for
 * the Commissioner to schedule an auction. Seeds once on dev boot; a later boot finds the room and
 * does nothing. The room uses the first loaded Sport Pack (by id), so no pack is named here. Turn
 * off with {@code paddleup.dev-seed.enabled=false}.
 *
 * <p>Sign in as any seeded email: in dev the sign-in code is printed to the server log. Only the
 * Commissioner ({@value #COMMISSIONER_EMAIL}) can schedule.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "paddleup.dev-seed", name = "enabled", havingValue = "true")
class DevSeedRunner {

  private static final Logger log = LoggerFactory.getLogger(DevSeedRunner.class);

  static final String ROOM_NAME = "Seed League";
  static final String COMMISSIONER_EMAIL = "cara@paddleup.test";
  private static final String JOIN_CLIENT_KEY = "dev-seed";

  /** A seeded account and the franchise it founds (the Commissioner's) or joins with. */
  private record Seat(
      String email, String displayName, String franchiseName, String colour, String monogram) {}

  private static final List<Seat> SEATS =
      List.of(
          new Seat(COMMISSIONER_EMAIL, "Cara", "Crimson Kings", "crimson", "CK"),
          new Seat("alex@paddleup.test", "Alex", "Tangerine Titans", "tangerine", "TT"),
          new Seat("bea@paddleup.test", "Bea", "Bronze Bulls", "bronze", "BB"),
          new Seat("chen@paddleup.test", "Chen", "Teal Tigers", "teal", "TG"));

  private final DevAccountSeeder accounts;
  private final RoomService rooms;
  private final FranchiseService franchises;
  private final InviteService invites;
  private final JoinService joins;
  private final PackService packs;

  DevSeedRunner(
      DevAccountSeeder accounts,
      RoomService rooms,
      FranchiseService franchises,
      InviteService invites,
      JoinService joins,
      PackService packs) {
    this.accounts = accounts;
    this.rooms = rooms;
    this.franchises = franchises;
    this.invites = invites;
    this.joins = joins;
    this.packs = packs;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void seed() {
    List<UserId> users =
        SEATS.stream().map(s -> accounts.ensure(s.email(), s.displayName())).toList();
    UserId commissioner = users.get(0);

    boolean seeded =
        rooms.listMine(commissioner).stream().map(RoomSummaryDto::name).anyMatch(ROOM_NAME::equals);
    if (seeded) {
      log.info("[dev seed] '{}' already exists; skipping", ROOM_NAME);
      return;
    }

    String packId = packs.list().stream().map(PackSummaryDto::id).findFirst().orElse(null);
    if (packId == null) {
      log.warn("[dev seed] no Sport Pack loaded; skipping");
      return;
    }
    RoomDto room =
        rooms.create(
            commissioner, new CreateRoomRequest(packId, "standard", ROOM_NAME, "Dev test cup"));
    Seat founder = SEATS.get(0);
    franchises.createOwn(
        commissioner,
        room.id(),
        new FranchiseRequest(founder.franchiseName(), founder.colour(), founder.monogram()));

    InviteDto invite = invites.issue(commissioner, room.id());
    for (int i = 1; i < SEATS.size(); i++) {
      Seat seat = SEATS.get(i);
      joins.redeem(
          users.get(i),
          new JoinRequest(
              invite.code(),
              new FranchiseRequest(seat.franchiseName(), seat.colour(), seat.monogram())),
          JOIN_CLIENT_KEY);
    }
    log.info(
        "[dev seed] created '{}' ({}) with {} franchises; Commissioner {} can schedule an auction",
        ROOM_NAME,
        room.id(),
        SEATS.size(),
        COMMISSIONER_EMAIL);
  }
}
