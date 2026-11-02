package com.paddleup.packs.service;

import com.paddleup.packs.exception.PackLoadException;
import com.paddleup.packs.exception.PackViolation;
import com.paddleup.packs.model.LoadedPack;
import com.paddleup.packs.model.PackRegistry;
import com.paddleup.packs.repository.PackFileRepository;
import com.paddleup.packs.util.PackParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the registry once at startup: read the files, parse them, then fail on any violation. */
public class PackLoader {

  private static final Logger log = LoggerFactory.getLogger(PackLoader.class);

  private final PackFileRepository repository;
  private final PackParser parser;
  private final PackValidator validator = new PackValidator();
  private final List<DataAdapter> adapters;

  public PackLoader(PackFileRepository repository, PackParser parser, List<DataAdapter> adapters) {
    this.repository = repository;
    this.parser = parser;
    this.adapters = List.copyOf(adapters);
  }

  /** Returns the registry, or throws {@link PackLoadException} listing every violation found. */
  public PackRegistry load() {
    var read = repository.readAll();
    var violations = new ArrayList<PackViolation>(read.violations());
    var byKey = adaptersByKey(violations);
    var packs = new LinkedHashMap<String, LoadedPack>();
    var seen = new java.util.HashMap<String, PackFileRef>();

    for (var file : read.files()) {
      var versionViolations = validator.checkSchemaVersion(file.tree(), file.name());
      if (!versionViolations.isEmpty()) {
        violations.addAll(versionViolations);
        continue;
      }
      var parsed = parser.parsePack(file.tree(), file.name());
      violations.addAll(parsed.violations());
      var pack = parsed.value();
      if (pack.id() != null) {
        var first = seen.putIfAbsent(pack.id(), new PackFileRef(file.name(), pack.version()));
        if (first != null) {
          violations.add(
              new PackViolation(
                  file.name(),
                  pack.id(),
                  "duplicate-pack-id",
                  "pack id '%s' is defined in both %s (version %s) and %s (version %s)"
                      .formatted(
                          pack.id(), first.file(), first.version(), file.name(), pack.version())));
          continue;
        }
      }
      if (parsed.violations().isEmpty()) {
        violations.addAll(validator.validate(pack, file.name()));
        Optional<DataAdapter> adapter = Optional.empty();
        if (pack.adapterKey().isPresent()) {
          adapter = Optional.ofNullable(byKey.get(pack.adapterKey().get()));
          if (adapter.isEmpty()) {
            violations.add(
                new PackViolation(
                    file.name(),
                    pack.id(),
                    "unknown-adapter-key",
                    "adapter key '%s' matches no registered data adapter; registered: %s"
                        .formatted(pack.adapterKey().get(), new TreeSet<>(byKey.keySet()))));
          }
        }
        packs.put(pack.id(), new LoadedPack(file.path(), pack, adapter));
      }
    }

    if (!violations.isEmpty()) {
      throw new PackLoadException(violations);
    }
    log.info("Loaded {} sport pack(s) from {}", packs.size(), repository.dir());
    return new PackRegistry(packs);
  }

  /** Indexes the adapter beans by key; two beans with one key are a violation. */
  private Map<String, DataAdapter> adaptersByKey(List<PackViolation> violations) {
    var byKey = new HashMap<String, DataAdapter>();
    for (DataAdapter adapter : adapters) {
      var previous = byKey.putIfAbsent(adapter.key(), adapter);
      if (previous != null) {
        violations.add(
            new PackViolation(
                "(adapters)",
                "-",
                "duplicate-adapter-key",
                "adapter key '%s' is implemented by both %s and %s"
                    .formatted(
                        adapter.key(),
                        previous.getClass().getName(),
                        adapter.getClass().getName())));
      }
    }
    return byKey;
  }

  private record PackFileRef(String file, String version) {}
}
