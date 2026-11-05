package com.paddleup.packs.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.paddleup.packs.exception.DatasetFileException;
import com.paddleup.packs.exception.DatasetFileException.Reason;
import com.paddleup.packs.util.DatasetFileReader.Format;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** AC-1: a CSV and its equivalent JSON read to the same rows. */
class DatasetFileReaderTest {

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static final String CSV =
      """
      name,country,role,tags,base_price,set,provider_id
      Ann Lee,Chile,BAT,capped;overseas,20,Marquee,p-1
      "Bo, Jr",Peru,BOWL,,50,Marquee,
      Cy Day,Chile,AR,uncapped,100,Capped Batters,
      """;

  private static final String JSON =
      """
      {"players":[
        {"name":"Ann Lee","country":"Chile","role":"BAT","tags":["capped","overseas"],
         "base_price":20,"set":"Marquee","provider_id":"p-1"},
        {"name":"Bo, Jr","country":"Peru","role":"BOWL","tags":[],"base_price":"50","set":"Marquee"},
        {"name":"Cy Day","country":"Chile","role":"AR","tags":["uncapped"],"base_price":100,
         "set":"Capped Batters","provider_id":""}
      ]}
      """;

  @Test
  void aCsvAndItsEquivalentJsonGiveEqualRowsWithRowNumbers() {
    var fromCsv = DatasetFileReader.read(Format.CSV, bytes(CSV));
    var fromJson = DatasetFileReader.read(Format.JSON, bytes(JSON));

    assertThat(fromCsv).isEqualTo(fromJson);
    assertThat(fromCsv).extracting(DatasetFileReader.RawRow::rowNumber).containsExactly(1, 2, 3);
    assertThat(fromCsv.get(0).input().tags()).containsExactly("capped", "overseas");
    assertThat(fromCsv.get(0).input().providerId()).isEqualTo("p-1");
    assertThat(fromCsv.get(1).input().name()).isEqualTo("Bo, Jr");
    assertThat(fromCsv.get(1).input().providerId()).isNull();
  }

  @Test
  void aBomAndExtraColumnsAndBlankLinesAreTolerated() {
    String csv = "﻿name,country,role,tags,base_price,set,extra\n\nAnn,Chile,BAT,,20,Marquee,zzz\n";

    var rows = DatasetFileReader.read(Format.CSV, bytes(csv));

    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).input().providerId()).isNull();
  }

  @Test
  void aFileOverOneMegabyteIsRefused() {
    byte[] big = new byte[DatasetFileReader.MAX_BYTES + 1];

    assertThatThrownBy(() -> DatasetFileReader.read(Format.CSV, big))
        .isInstanceOfSatisfying(
            DatasetFileException.class, e -> assertThat(e.reason()).isEqualTo(Reason.TOO_LARGE));
  }

  @Test
  void moreThanTwoThousandRowsIsRefusedForBothFormats() {
    String rows =
        IntStream.rangeClosed(1, 2001)
            .mapToObj(i -> "P" + i + ",C,BAT,,20,S")
            .collect(Collectors.joining("\n"));
    String csv = "name,country,role,tags,base_price,set\n" + rows + "\n";
    String json =
        "{\"players\":["
            + IntStream.rangeClosed(1, 2001)
                .mapToObj(i -> "{\"name\":\"P" + i + "\"}")
                .collect(Collectors.joining(","))
            + "]}";

    for (var call :
        java.util.List.<Runnable>of(
            () -> DatasetFileReader.read(Format.CSV, bytes(csv)),
            () -> DatasetFileReader.read(Format.JSON, bytes(json)))) {
      assertThatThrownBy(call::run)
          .isInstanceOfSatisfying(
              DatasetFileException.class,
              e -> assertThat(e.reason()).isEqualTo(Reason.TOO_MANY_ROWS));
    }
  }

  @Test
  void twoThousandRowsIsAccepted() {
    String rows =
        IntStream.rangeClosed(1, 2000)
            .mapToObj(i -> "P" + i + ",C,BAT,,20,S")
            .collect(Collectors.joining("\n"));

    var read =
        DatasetFileReader.read(Format.CSV, bytes("name,country,role,tags,base_price,set\n" + rows));

    assertThat(read).hasSize(2000);
  }

  @Test
  void malformedFilesGiveATypedError() {
    String[][] bad = {
      {"csv", "name,country\nAnn,Chile\n"},
      {"csv", "name,country,role,tags,base_price,set\n\"unterminated,Chile,BAT,,20,S\n"},
      {"json", "{\"players\": [ {"},
      {"json", "[]"},
      {"json", "{\"people\":[]}"},
      {"json", "{\"players\":{}}"},
      {"json", "{\"players\":[[[[[[[[[[[[[[[1]]]]]]]]]]]]]]]}"}
    };
    for (String[] file : bad) {
      Format format = file[0].equals("csv") ? Format.CSV : Format.JSON;
      assertThatThrownBy(() -> DatasetFileReader.read(format, bytes(file[1])))
          .as(file[1])
          .isInstanceOfSatisfying(
              DatasetFileException.class, e -> assertThat(e.reason()).isEqualTo(Reason.MALFORMED));
    }
  }

  @Test
  void anEmptyFileOrAHeaderOnlyFileOrBadUtf8IsRefused() {
    assertThatThrownBy(() -> DatasetFileReader.read(Format.CSV, new byte[0]))
        .isInstanceOfSatisfying(
            DatasetFileException.class, e -> assertThat(e.reason()).isEqualTo(Reason.EMPTY));
    assertThatThrownBy(
            () ->
                DatasetFileReader.read(
                    Format.CSV, bytes("name,country,role,tags,base_price,set\n")))
        .isInstanceOfSatisfying(
            DatasetFileException.class, e -> assertThat(e.reason()).isEqualTo(Reason.EMPTY));
    assertThatThrownBy(() -> DatasetFileReader.read(Format.JSON, bytes("{\"players\":[]}")))
        .isInstanceOfSatisfying(
            DatasetFileException.class, e -> assertThat(e.reason()).isEqualTo(Reason.EMPTY));
    assertThatThrownBy(
            () -> DatasetFileReader.read(Format.CSV, new byte[] {(byte) 0xC3, (byte) 0x28}))
        .isInstanceOfSatisfying(
            DatasetFileException.class, e -> assertThat(e.reason()).isEqualTo(Reason.MALFORMED));
  }

  @Test
  void theFormatComesFromTheFileNameOrContentType() {
    assertThat(Format.detect("Players.CSV", null)).isEqualTo(Format.CSV);
    assertThat(Format.detect("p.json", "text/plain")).isEqualTo(Format.JSON);
    assertThat(Format.detect("p", "text/csv; charset=utf-8")).isEqualTo(Format.CSV);
    assertThat(Format.detect("p.txt", "text/plain")).isNull();
  }
}
