package com.paddleup.packs.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paddleup.packs.exception.PackViolation;
import com.paddleup.packs.model.PackFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Read-only access to the pack files in one directory. */
public class PackFileRepository {

  private static final Logger log = LoggerFactory.getLogger(PackFileRepository.class);

  /** The files that could be read and a violation for each one that could not. */
  public record Result(List<PackFile> files, List<PackViolation> violations) {}

  private final Path dir;
  private final ObjectMapper mapper;

  public PackFileRepository(Path dir, ObjectMapper mapper) {
    this.dir = dir.toAbsolutePath().normalize();
    this.mapper = mapper;
  }

  public Path dir() {
    return dir;
  }

  /**
   * Reads every regular {@code *.json} file directly in the directory, sorted by name. An absent or
   * empty directory gives no files and one warning.
   */
  public Result readAll() {
    var files = new ArrayList<PackFile>();
    var violations = new ArrayList<PackViolation>();
    for (Path path : list()) {
      try {
        files.add(new PackFile(path, mapper.readTree(path.toFile())));
      } catch (IOException e) {
        violations.add(
            new PackViolation(
                path.getFileName().toString(),
                "?",
                "unreadable-file",
                "not valid JSON: " + firstLine(e.getMessage())));
      }
    }
    if (files.isEmpty() && violations.isEmpty()) {
      log.warn("No sport packs found in {}; starting with zero packs", dir);
    }
    return new Result(files, violations);
  }

  private List<Path> list() {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> entries = Files.list(dir)) {
      return entries
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".json"))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new java.io.UncheckedIOException("Cannot list pack directory " + dir, e);
    }
  }

  private static String firstLine(String message) {
    if (message == null) {
      return "unreadable";
    }
    int end = message.indexOf('\n');
    return end < 0 ? message : message.substring(0, end);
  }
}
