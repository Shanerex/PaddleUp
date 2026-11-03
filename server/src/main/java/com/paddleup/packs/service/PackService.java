package com.paddleup.packs.service;

import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.PackSummaryDto;
import com.paddleup.packs.dto.StatDto;
import com.paddleup.packs.dto.StatLineDto;
import com.paddleup.packs.dto.StatSheetDto;
import com.paddleup.packs.exception.PackNotFoundException;
import com.paddleup.packs.model.LoadedPack;
import com.paddleup.packs.model.PackRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** The only way other modules read sport packs. */
@Service
public class PackService {

  private final PackRegistry registry;

  public PackService(PackRegistry registry) {
    this.registry = registry;
  }

  /** Every loaded pack, ordered by id. */
  public List<PackSummaryDto> list() {
    return registry.packs().values().stream()
        .map(LoadedPack::pack)
        .sorted(Comparator.comparing(PackDto::id))
        .map(p -> new PackSummaryDto(p.id(), p.version(), p.manualEntryOnly()))
        .toList();
  }

  /** The pack with this id, or {@link PackNotFoundException}. */
  public PackDto get(String packId) {
    var loaded = registry.packs().get(packId);
    if (loaded == null) {
      throw new PackNotFoundException(packId);
    }
    return loaded.pack();
  }

  /** The data adapter the pack's key resolved to; empty for a manual-entry-only pack. */
  public Optional<DataAdapter> adapterFor(String packId) {
    get(packId);
    return registry.packs().get(packId).adapter();
  }

  /**
   * Checks a stat sheet against the pack's stat schema: every stat id must be in the schema and
   * every event type declared on its stat. Returns one message per problem; empty means valid.
   */
  public List<String> validateStatSheet(String packId, StatSheetDto sheet) {
    var stats = get(packId).statSchema().stream().collect(Collectors.toMap(StatDto::id, s -> s));
    var problems = new ArrayList<String>();
    for (StatLineDto line : sheet.lines()) {
      line.stats().keySet().stream()
          .sorted()
          .filter(stat -> !stats.containsKey(stat))
          .forEach(
              stat ->
                  problems.add(
                      "player '%s': stat '%s' is not in the stat schema"
                          .formatted(line.playerId(), stat)));
      line.events().entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              byStat -> {
                var declared = stats.get(byStat.getKey());
                if (declared == null) {
                  problems.add(
                      "player '%s': stat '%s' is not in the stat schema"
                          .formatted(line.playerId(), byStat.getKey()));
                  return;
                }
                byStat.getValue().keySet().stream()
                    .sorted()
                    .filter(type -> !declared.eventTypes().contains(type))
                    .forEach(
                        type ->
                            problems.add(
                                "player '%s': stat '%s' does not declare event type '%s'"
                                    .formatted(line.playerId(), byStat.getKey(), type)));
              });
    }
    return problems;
  }
}
