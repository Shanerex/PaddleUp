package com.paddleup.packs.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.paddleup.packs.dto.PlayerInput;
import com.paddleup.packs.exception.DatasetFileException;
import com.paddleup.packs.exception.DatasetFileException.Reason;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a season dataset file into raw rows without judging them. A CSV has the header {@code
 * name,country,role,tags,base_price,set,provider_id} (tags separated by {@code ;}); a JSON file is
 * {@code {"players":[...]}} with the same keys and tags as an array. Both give the same rows. It
 * has its own Jackson readers, so the app-wide request limit does not apply, with limits of its
 * own.
 */
public final class DatasetFileReader {

  public static final int MAX_BYTES = 1024 * 1024;
  public static final int MAX_ROWS = 2000;

  private static final int JSON_DEPTH_MAX = 8;
  private static final int JSON_STRING_MAX = 10_000;
  private static final List<String> REQUIRED_COLUMNS =
      List.of("name", "country", "role", "tags", "base_price", "set");

  /** The file format, from the file name or content type. */
  public enum Format {
    CSV,
    JSON;

    public String value() {
      return name().toLowerCase(Locale.ROOT);
    }

    /** The format a file name or content type points to, or null when neither does. */
    public static Format detect(String fileName, String contentType) {
      String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
      String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
      if (name.endsWith(".csv")) {
        return CSV;
      }
      if (name.endsWith(".json")) {
        return JSON;
      }
      if (type.startsWith("text/csv")) {
        return CSV;
      }
      if (type.startsWith("application/json")) {
        return JSON;
      }
      return null;
    }
  }

  /** One data row as read: its 1-based position among the players and what it held. */
  public record RawRow(int rowNumber, PlayerInput input) {}

  private DatasetFileReader() {}

  public static List<RawRow> read(Format format, byte[] bytes) {
    if (format == null) {
      throw new DatasetFileException(Reason.UNSUPPORTED_FORMAT, "Upload a .csv or .json file");
    }
    if (bytes == null || bytes.length == 0) {
      throw new DatasetFileException(Reason.EMPTY, "The file is empty");
    }
    if (bytes.length > MAX_BYTES) {
      throw new DatasetFileException(
          Reason.TOO_LARGE, "The file is larger than " + MAX_BYTES / 1024 / 1024 + " MB");
    }
    List<RawRow> rows =
        switch (format) {
          case CSV -> csv(text(bytes));
          case JSON -> json(text(bytes));
        };
    if (rows.isEmpty()) {
      throw new DatasetFileException(Reason.EMPTY, "The file has no player rows");
    }
    return rows;
  }

  private static String text(byte[] bytes) {
    try {
      String text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
      return text.startsWith("﻿") ? text.substring(1) : text;
    } catch (CharacterCodingException e) {
      throw new DatasetFileException(Reason.MALFORMED, "The file is not valid UTF-8 text");
    }
  }

  private static List<RawRow> csv(String text) {
    CsvMapper mapper = new CsvMapper();
    mapper.enable(CsvParser.Feature.SKIP_EMPTY_LINES);
    mapper.enable(CsvParser.Feature.IGNORE_TRAILING_UNMAPPABLE);
    var rows = new ArrayList<RawRow>();
    try (MappingIterator<Map<String, String>> it =
        mapper.readerFor(Map.class).with(CsvSchema.emptySchema().withHeader()).readValues(text)) {
      boolean checked = false;
      while (it.hasNext()) {
        Map<String, String> record = it.next();
        if (!checked) {
          checked = true;
          for (String column : REQUIRED_COLUMNS) {
            if (!record.containsKey(column)) {
              throw new DatasetFileException(
                  Reason.MALFORMED, "The header is missing the column '" + column + "'");
            }
          }
        }
        if (rows.size() >= MAX_ROWS) {
          throw tooManyRows();
        }
        rows.add(new RawRow(rows.size() + 1, fromCsv(record)));
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof DatasetFileException dfe) {
        throw dfe;
      }
      throw new DatasetFileException(Reason.MALFORMED, "The CSV could not be read");
    }
    return rows;
  }

  private static PlayerInput fromCsv(Map<String, String> record) {
    return new PlayerInput(
        record.get("name"),
        record.get("country"),
        record.get("role"),
        splitTags(record.get("tags")),
        record.get("base_price"),
        record.get("set"),
        blankToNull(record.get("provider_id")));
  }

  private static List<RawRow> json(String text) {
    ObjectMapper mapper = new ObjectMapper();
    mapper
        .getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder()
                .maxNestingDepth(JSON_DEPTH_MAX)
                .maxDocumentLength(MAX_BYTES)
                .maxStringLength(JSON_STRING_MAX)
                .build());
    mapper.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    JsonNode root;
    try {
      root = mapper.readTree(text);
    } catch (IOException | RuntimeException e) {
      throw new DatasetFileException(Reason.MALFORMED, "The JSON could not be read");
    }
    if (root == null || !root.isObject() || !root.path("players").isArray()) {
      throw new DatasetFileException(
          Reason.MALFORMED, "Expected a JSON object with a \"players\" array");
    }
    JsonNode players = root.get("players");
    if (players.size() > MAX_ROWS) {
      throw tooManyRows();
    }
    var rows = new ArrayList<RawRow>();
    for (JsonNode player : players) {
      rows.add(new RawRow(rows.size() + 1, fromJson(player)));
    }
    return rows;
  }

  private static PlayerInput fromJson(JsonNode player) {
    return new PlayerInput(
        scalar(player.get("name")),
        scalar(player.get("country")),
        scalar(player.get("role")),
        tags(player.get("tags")),
        scalar(player.get("base_price")),
        scalar(player.get("set")),
        blankToNull(scalar(player.get("provider_id"))));
  }

  private static String scalar(JsonNode node) {
    if (node == null || node.isNull() || node.isContainerNode()) {
      return null;
    }
    return node.asText();
  }

  private static List<String> tags(JsonNode node) {
    if (node == null || node.isNull()) {
      return List.of();
    }
    if (node.isArray()) {
      var tags = new ArrayList<String>();
      for (JsonNode tag : node) {
        String text = scalar(tag);
        tags.add(text == null ? "" : text);
      }
      return tags;
    }
    return splitTags(scalar(node));
  }

  private static List<String> splitTags(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return Arrays.stream(raw.split(";", -1)).map(String::trim).filter(t -> !t.isEmpty()).toList();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static DatasetFileException tooManyRows() {
    return new DatasetFileException(
        Reason.TOO_MANY_ROWS, "The file has more than " + MAX_ROWS + " player rows");
  }
}
