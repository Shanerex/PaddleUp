package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.PaddleUpApplication;
import com.paddleup.scoring.ScoringIT;
import com.paddleup.scoring.exception.RuleSetConflictException;
import com.paddleup.support.MutableClock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * AC-10, AC-17: a second application context on the same database is the restarted server, or the
 * server restarted on a changed pack.
 */
class SecondContextIT extends ScoringIT {

  @Autowired Environment env;
  @Autowired RuleSetService rules;

  /** A restarted server sharing this test's database, with its own clock and packs directory. */
  private ConfigurableApplicationContext restart(MutableClock clock, String packsDir) {
    return new SpringApplicationBuilder(PaddleUpApplication.class)
        .initializers(
            (org.springframework.context.ApplicationContextInitializer<
                    org.springframework.context.support.GenericApplicationContext>)
                context ->
                    context.registerBean(
                        "restartedClock",
                        MutableClock.class,
                        () -> clock,
                        bd -> bd.setPrimary(true)))
        .run(
            "--server.port=0",
            "--spring.datasource.url=" + env.getProperty("spring.datasource.url"),
            "--spring.datasource.username=" + env.getProperty("spring.datasource.username"),
            "--spring.datasource.password=" + env.getProperty("spring.datasource.password"),
            "--paddleup.auction.tick=0",
            "--paddleup.auction.recovery=false",
            "--paddleup.packs.dir=" + packsDir,
            "--spring.main.allow-bean-definition-overriding=true",
            "--logging.level.root=WARN");
  }

  @Test
  void afterARestartTheRuleSetAndTheFrozenStateAreIdenticalAndEditsAreStillRefused() {
    var edited =
        rules.edit(
            roomId, user(commissioner), 0, runsWorth(port.ruleInputs(roomId).defaultRules(), "2"));
    clock.freeze(START);
    var refused =
        org.junit.jupiter.api.Assertions.assertThrows(
            RuleSetConflictException.class,
            () -> rules.edit(roomId, user(commissioner), 1, edited.rules()));
    assertThat(refused.code()).isEqualTo("frozen");
    var before = rules.read(roomId, user(manager));
    assertThat(before.frozen()).isTrue();

    // The restarted server's clock is an hour before the start: only the stored freeze remains.
    var later = new MutableClock();
    later.freeze(START.minusSeconds(3600));
    try (var ctx = restart(later, "../packs")) {
      var restarted = ctx.getBean(RuleSetService.class);

      var after = restarted.read(roomId, user(manager));

      assertThat(after).isEqualTo(before);
      assertThat(after.frozen()).isTrue();
      assertThat(after.status()).isEqualTo("custom");
      assertThat(after.version()).isEqualTo(1);
      var again =
          org.junit.jupiter.api.Assertions.assertThrows(
              RuleSetConflictException.class,
              () -> restarted.edit(roomId, user(commissioner), 1, edited.rules()));
      assertThat(again.code()).isEqualTo("frozen");
    }
    assertThat(version(roomId)).isEqualTo(1);
  }

  @Test
  void afterAPackChangeTheRoomsRulesAndRecomputedPointsAreUnchanged(@TempDir Path packs)
      throws IOException {
    provider(datasetId, "m1", player.sourcePlayerId(), "30");
    var rulesBefore = port.ruleInputs(roomId);
    var pointsBefore = scoring.points(roomId, "m1");
    assertThat(pointsBefore.get(0).total()).isEqualByComparingTo("34");
    String changed =
        Files.readString(Path.of("../packs/cricket-t20.json"))
            .replace("\"version\": \"1.1.0\"", "\"version\": \"2.0.0\"")
            .replace(
                "\"id\": \"runs-points\", \"label\": \"Runs\", \"stat\": \"runs\", \"points\": 1",
                "\"id\": \"runs-points\", \"label\": \"Runs\", \"stat\": \"runs\", \"points\": 9")
            .replace(
                "{\"type\": \"appearance-bonus\", \"id\": \"appearance-bonus\", \"label\": \"Playing XI\", \"points\": 4}",
                "{\"type\": \"appearance-bonus\", \"id\": \"appearance-bonus\", \"label\": \"Playing XI\", \"points\": 40}");
    assertThat(changed).contains("\"points\": 9").contains("\"points\": 40");
    Files.writeString(packs.resolve("cricket-t20.json"), changed);

    try (var ctx = restart(new MutableClock(), packs.toString())) {
      var livePack = ctx.getBean(com.paddleup.packs.service.PackService.class).get("cricket-t20");
      assertThat(livePack.version()).isEqualTo("2.0.0");
      var restartedPort = ctx.getBean(com.paddleup.rooms.service.ScoringRoomPort.class);

      assertThat(restartedPort.ruleInputs(roomId)).isEqualTo(rulesBefore);
      ctx.getBean(ScoringService.class).recomputeRoom(roomId);
      var pointsAfter = ctx.getBean(ScoringService.class).points(roomId, "m1");

      assertThat(pointsAfter).hasSize(1);
      assertThat(pointsAfter.get(0).total()).isEqualByComparingTo("34");
      assertThat(pointsAfter.get(0).lines()).isEqualTo(pointsBefore.get(0).lines());
      assertThat(restartedPort.ruleInputs(roomId).packVersion()).isEqualTo("1.1.0");
    }
  }
}
