package com.paddleup.packs.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.packs.dto.SeasonPlayerDto;
import com.paddleup.packs.exception.NoValidRowsException;
import com.paddleup.packs.util.DatasetFileReader.Format;
import com.paddleup.support.AbstractPostgresIT;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** AC-3, AC-4, AC-5: partial import into immutable versions. */
class SeasonDatasetServiceIT extends AbstractPostgresIT {

  private static final String PACK = "cricket-t20";
  private static final String HEADER = "name,country,role,tags,base_price,set,provider_id\n";

  @Autowired SeasonDatasetService datasets;
  @Autowired JdbcTemplate jdbc;

  private UserId admin() {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO users (id, email, display_name, avatar, created_at, updated_at)"
            + " VALUES (?, ?, 'Ann', 'preset-01', now(), now())",
        id,
        "ds-" + id + "@example.test");
    return new UserId(id);
  }

  private int latestVersion() {
    return datasets.latestSummary(PACK).map(d -> d.version()).orElse(0);
  }

  private static byte[] csv(String rows) {
    return (HEADER + rows).getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void aMixedFileStoresOnlyTheValidRowsAsTheNextVersionAndNamesEachRejectedField() {
    int before = latestVersion();
    byte[] file =
        csv(
            """
            Ann Lee,Chile,BAT,capped;overseas,20,Marquee,p-1
            Bad Role,Peru,SPIN,capped,20,Marquee,
            Bad Tag,Peru,BOWL,mystery,20,Marquee,
            Bad Price,Peru,AR,,-5,Marquee,
            Cy Day,Chile,WK,uncapped,100,Capped Batters,
            """);

    var report = datasets.importFile(admin(), PACK, Format.CSV, file);

    assertThat(report.version()).isEqualTo(before + 1);
    assertThat(report.totalRows()).isEqualTo(5);
    assertThat(report.imported()).isEqualTo(2);
    assertThat(report.skipped()).isEqualTo(3);
    assertThat(report.rows())
        .extracting(r -> r.row() + ":" + r.field())
        .containsExactly("2:role", "3:tags", "4:base_price");
    assertThat(report.moreProblems()).isZero();
    var stored = datasets.latest(PACK).orElseThrow();
    assertThat(stored.version()).isEqualTo(before + 1);
    assertThat(stored.rejectedCount()).isEqualTo(3);
    assertThat(stored.players())
        .extracting(SeasonPlayerDto::name)
        .containsExactly("Ann Lee", "Cy Day");
    assertThat(stored.players()).extracting(SeasonPlayerDto::position).containsExactly(0, 1);
    assertThat(stored.players().get(0).tags()).containsExactly("capped", "overseas");
    assertThat(stored.players().get(0).providerId()).isEqualTo("p-1");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM season_players WHERE dataset_id = ? AND name LIKE 'Bad%'",
                Integer.class, stored.id()))
        .isZero();
  }

  @Test
  void aFileWithNoValidRowsThrowsWithTheReportAndLeavesTheLatestVersionAlone() {
    datasets.importFile(admin(), PACK, Format.CSV, csv("Ann,Chile,BAT,,20,Marquee,\n"));
    var latest = datasets.latest(PACK).orElseThrow();

    assertThatThrownBy(
            () ->
                datasets.importFile(
                    admin(),
                    PACK,
                    Format.CSV,
                    csv("A,Chile,NOPE,,20,S,\nB,Chile,BAT,nope,20,S,\nC,Chile,BAT,,7,S,\n")))
        .isInstanceOfSatisfying(
            NoValidRowsException.class,
            e -> {
              assertThat(e.report().version()).isNull();
              assertThat(e.report().imported()).isZero();
              assertThat(e.report().skipped()).isEqualTo(3);
              assertThat(e.report().rows()).hasSize(3);
            });

    assertThat(datasets.latest(PACK).orElseThrow().id()).isEqualTo(latest.id());
    assertThat(latestVersion()).isEqualTo(latest.version());
  }

  @Test
  void aSecondImportMakesTheNextVersionAndTheFirstKeepsItsContents() {
    var first = datasets.importFile(admin(), PACK, Format.CSV, csv("One,Chile,BAT,,20,A,\n"));
    var firstStored = datasets.latest(PACK).orElseThrow();

    var second =
        datasets.importFile(
            admin(), PACK, Format.CSV, csv("Two,Chile,BAT,,20,B,\nThree,Chile,BOWL,,20,B,\n"));

    assertThat(second.version()).isEqualTo(first.version() + 1);
    assertThat(datasets.list(PACK).get(0).version()).isEqualTo(second.version());
    var again = datasets.player(firstStored.id(), firstStored.players().get(0).id()).orElseThrow();
    assertThat(again.name()).isEqualTo("One");
    assertThat(datasets.latest(PACK).orElseThrow().playerCount()).isEqualTo(2);
  }

  @Test
  void theSetOrderFollowsFirstAppearanceInTheFile() {
    datasets.importFile(
        admin(),
        PACK,
        Format.CSV,
        csv(
            """
            a,Chile,BAT,,20,Zeta,
            b,Chile,BAT,,20,Alpha,
            c,Chile,BAT,,20,Zeta,
            d,Chile,BAT,,20,Mid,
            """));

    var stored = datasets.latest(PACK).orElseThrow();

    assertThat(stored.setOrder()).containsExactly("Zeta", "Alpha", "Mid");
  }

  @Test
  void aSetOnlyRejectedRowsUseIsNotInTheOrder() {
    datasets.importFile(
        admin(), PACK, Format.CSV, csv("a,Chile,BAT,,20,Kept,\nb,Chile,NOPE,,20,Ghost,\n"));

    assertThat(datasets.latest(PACK).orElseThrow().setOrder()).containsExactly("Kept");
  }

  @Test
  void theReportListsAtMostOneHundredProblemsAndCountsTheRest() {
    var rows = new StringBuilder("Good,Chile,BAT,,20,S,\n");
    for (int i = 0; i < 130; i++) {
      rows.append("Bad").append(i).append(",Chile,NOPE,,20,S,\n");
    }

    var report = datasets.importFile(admin(), PACK, Format.CSV, csv(rows.toString()));

    assertThat(report.rows()).hasSize(100);
    assertThat(report.moreProblems()).isEqualTo(30);
    assertThat(report.skipped()).isEqualTo(130);
  }
}
