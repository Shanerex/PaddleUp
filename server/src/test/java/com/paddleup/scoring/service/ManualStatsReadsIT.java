package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.scoring.ScoringIT;
import com.paddleup.scoring.dto.ClearFieldsRequest;
import com.paddleup.scoring.dto.FieldRefDto;
import com.paddleup.scoring.dto.FieldValueDto;
import com.paddleup.scoring.dto.ProviderStatLineDto;
import com.paddleup.scoring.dto.SaveSheetRequest;
import com.paddleup.scoring.dto.SheetFieldDto;
import com.paddleup.scoring.dto.StatMatchDto;
import com.paddleup.scoring.exception.MatchRefusedException;
import com.paddleup.support.QueryCounter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** AC-12, AC-17, AC-20: the match list and its chips, the whole-pool player list, the sheet. */
@Import(QueryCounter.class)
class ManualStatsReadsIT extends ScoringIT {

  @Autowired ManualStatsService manual;

  @BeforeEach
  void started() {
    clock.freeze(START.plus(java.time.Duration.ofDays(2)));
  }

  private void save(String match, UUID poolPlayer, String stat, String value) {
    manual.save(
        roomId,
        user(commissioner),
        match,
        poolPlayer,
        new SaveSheetRequest(List.of(new FieldValueDto(stat, null, new BigDecimal(value)))));
  }

  private void providerFor(String match, UUID seasonPlayer) {
    matches.putProviderStats(
        datasetId,
        match,
        List.of(
            new ProviderStatLineDto(
                seasonPlayer,
                Map.of("runs", new BigDecimal("46"), "in-playing-xi", BigDecimal.ONE),
                null)));
  }

  private Map<String, StatMatchDto> byId() {
    var all = manual.matches(roomId, user(commissioner));
    return all.stream().collect(java.util.stream.Collectors.toMap(StatMatchDto::matchId, m -> m));
  }

  @Test
  void matchesCarryTheirNumberLabelAndSourceChip() {
    Instant base = START;
    matches.upsertMatch(datasetId, "m4", base.plusSeconds(1800), null);
    matches.upsertMatch(datasetId, "m2", base.plusSeconds(3600), "Eliminator");
    matches.upsertMatch(datasetId, "m5", base.plusSeconds(7200), null);
    matches.upsertMatch(datasetId, "m3", base.plusSeconds(86400L * 5), "Final");
    var p0 = port.players(roomId).get(0);
    var p1 = port.players(roomId).get(1);
    providerFor("m1", p0.sourcePlayerId());
    providerFor("m2", p0.sourcePlayerId());
    save("m2", p0.poolPlayerId(), "fours", "6");
    save("m2", p1.poolPlayerId(), "fours", "2");
    save("m4", p1.poolPlayerId(), "runs", "12");

    var all = manual.matches(roomId, user(commissioner));

    assertThat(all).extracting(StatMatchDto::matchId).containsExactly("m1", "m4", "m2", "m5", "m3");
    assertThat(all).extracting(StatMatchDto::n).containsExactly(1, 2, 3, 4, 5);
    var map = byId();
    assertThat(map.get("m1").chip()).isEqualTo("PROVIDER");
    assertThat(map.get("m1").manualPlayers()).isZero();
    assertThat(map.get("m2").chip()).isEqualTo("PROVIDER_MANUAL");
    assertThat(map.get("m2").manualPlayers()).isEqualTo(2);
    assertThat(map.get("m2").label()).isEqualTo("Eliminator");
    assertThat(map.get("m4").chip()).isEqualTo("MANUAL");
    assertThat(map.get("m5").chip()).isEqualTo("NO_DATA");
    assertThat(map.get("m3").chip()).isEqualTo("UPCOMING");
    assertThat(map.get("m3").label()).isEqualTo("Final");
  }

  @Test
  void clearingTheLastOverrideTurnsAManualOnlyMatchBackToNoData() {
    var p0 = port.players(roomId).get(0);
    save("m1", p0.poolPlayerId(), "runs", "12");
    assertThat(byId().get("m1").chip()).isEqualTo("MANUAL");

    manual.clear(
        roomId,
        user(commissioner),
        "m1",
        p0.poolPlayerId(),
        new ClearFieldsRequest(List.of(new FieldRefDto("runs", null))));

    assertThat(byId().get("m1").chip()).isEqualTo("NO_DATA");
  }

  @Test
  void matchPlayersListsTheWholePoolWithValuedPlayersFirst() {
    var pool = port.players(roomId);
    providerFor("m1", pool.get(3).sourcePlayerId());
    save("m1", pool.get(5).poolPlayerId(), "runs", "12");
    save("m1", pool.get(3).poolPlayerId(), "fours", "2");

    var view = manual.matchPlayers(roomId, user(commissioner), "m1");

    assertThat(view.poolSize()).isEqualTo(pool.size());
    assertThat(view.withValues()).isEqualTo(2);
    assertThat(view.players()).hasSize(pool.size());
    assertThat(view.players().get(0).poolPlayerId()).isEqualTo(pool.get(3).poolPlayerId());
    assertThat(view.players().get(0).chip()).isEqualTo("PROVIDER_MANUAL");
    assertThat(view.players().get(0).total()).isNotNull();
    assertThat(view.players().get(1).poolPlayerId()).isEqualTo(pool.get(5).poolPlayerId());
    assertThat(view.players().get(1).chip()).isEqualTo("MANUAL");
    assertThat(view.players().subList(2, pool.size()))
        .allSatisfy(
            p -> {
              assertThat(p.chip()).isNull();
              assertThat(p.total()).isNull();
              assertThat(p.owner()).isNull();
            });
    assertThat(view.players().get(0).name()).isEqualTo(pool.get(3).name());
    assertThat(view.match().chip()).isEqualTo("PROVIDER_MANUAL");
  }

  @Test
  void anUpcomingMatchCannotBeOpenedAndAnUnknownOneIs404() {
    matches.upsertMatch(datasetId, "m9", START.plus(java.time.Duration.ofDays(30)), null);

    assertThatThrownBy(() -> manual.matchPlayers(roomId, user(commissioner), "m9"))
        .isInstanceOfSatisfying(
            MatchRefusedException.class, e -> assertThat(e.code()).isEqualTo("match_not_started"));
    assertThatThrownBy(() -> manual.matchPlayers(roomId, user(commissioner), "zz"))
        .isInstanceOfSatisfying(
            MatchRefusedException.class, e -> assertThat(e.code()).isEqualTo("unknown_match"));
  }

  @Test
  void theSheetShowsTheProviderAndManualValueOfEveryField() {
    var p0 = port.players(roomId).get(0);
    providerFor("m1", p0.sourcePlayerId());
    save("m1", p0.poolPlayerId(), "runs", "52");

    var sheet = manual.sheet(roomId, user(commissioner), "m1", p0.poolPlayerId());

    assertThat(sheet.fields()).hasSizeGreaterThan(10);
    SheetFieldDto runs =
        sheet.fields().stream().filter(f -> f.stat().equals("runs")).findFirst().orElseThrow();
    assertThat(runs.provider()).isEqualByComparingTo("46");
    assertThat(runs.manual()).isEqualByComparingTo("52");
    SheetFieldDto fours =
        sheet.fields().stream().filter(f -> f.stat().equals("fours")).findFirst().orElseThrow();
    assertThat(fours.provider()).isNull();
    assertThat(fours.manual()).isNull();
    assertThat(sheet.points()).isNotNull();
    assertThat(sheet.providerOnly()).isNotNull().isNotEqualByComparingTo(sheet.points());
    assertThat(sheet.name()).isEqualTo(p0.name());
    assertThat(sheet.match().matchId()).isEqualTo("m1");
  }

  @Test
  void theListCostsTheSameNumberOfQueriesFor300PlayersAsForAFew() {
    var p0 = port.players(roomId).get(0);
    providerFor("m1", p0.sourcePlayerId());
    save("m1", p0.poolPlayerId(), "fours", "6");
    manual.matchPlayers(roomId, user(commissioner), "m1");

    int small = statementsOf(() -> manual.matchPlayers(roomId, user(commissioner), "m1"));
    jdbc.update(
        "INSERT INTO room_pool_players (id, room_id, source_player_id, origin, name, country,"
            + " role_key, tag_keys, base_price, set_name, provider_player_id, position)"
            + " SELECT gen_random_uuid(), ?, NULL, 'added', 'Extra ' || n, 'Chile', 'BAT', '{}',"
            + " 20, 'Marquee', NULL, 1000 + n FROM generate_series(1, 300) n",
        roomId);
    var big = manual.matchPlayers(roomId, user(commissioner), "m1");
    int large = statementsOf(() -> manual.matchPlayers(roomId, user(commissioner), "m1"));

    assertThat(big.poolSize()).isGreaterThan(300);
    assertThat(large).isEqualTo(small);
    assertThat(small).isBetween(1, 19);
  }

  private int statementsOf(Runnable action) {
    int before = QueryCounter.STATEMENTS.get();
    action.run();
    return QueryCounter.STATEMENTS.get() - before;
  }
}
