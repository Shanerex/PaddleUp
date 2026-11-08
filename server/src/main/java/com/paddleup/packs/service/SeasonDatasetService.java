package com.paddleup.packs.service;

import com.paddleup.accounts.dto.UserId;
import com.paddleup.packs.dto.DatasetFacetsDto;
import com.paddleup.packs.dto.DatasetSummaryDto;
import com.paddleup.packs.dto.ImportReportDto;
import com.paddleup.packs.dto.PackDto;
import com.paddleup.packs.dto.RejectedRowDto;
import com.paddleup.packs.dto.SeasonDatasetDto;
import com.paddleup.packs.dto.SeasonPlayerDto;
import com.paddleup.packs.exception.NoValidRowsException;
import com.paddleup.packs.repository.SeasonDatasetRepository;
import com.paddleup.packs.util.DatasetFileReader;
import com.paddleup.packs.util.DatasetFileReader.Format;
import com.paddleup.packs.util.DatasetFileReader.RawRow;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Season datasets: immutable, versioned player lists per pack. An import keeps the valid rows as
 * the next version and reports the rest. Other modules read datasets only through this service.
 */
@Service
public class SeasonDatasetService {

  /** Most rejected-row problems listed in a report; the count of the rest is reported too. */
  public static final int REPORT_ROWS_MAX = 100;

  private final PackService packs;
  private final SeasonDatasetRepository datasets;
  private final Clock clock;

  public SeasonDatasetService(PackService packs, SeasonDatasetRepository datasets, Clock clock) {
    this.packs = packs;
    this.datasets = datasets;
    this.clock = clock;
  }

  /**
   * Imports a file for the pack. Valid rows become the next dataset version; rejected rows are only
   * reported. With no valid row nothing is stored and {@link NoValidRowsException} carries the
   * report. The caller has already checked that {@code admin} may import.
   */
  @Transactional(noRollbackFor = NoValidRowsException.class)
  public ImportReportDto importFile(UserId admin, String packId, Format format, byte[] bytes) {
    PackDto pack = packs.get(packId);
    List<RawRow> rows = DatasetFileReader.read(format, bytes);
    PlayerRowRules rules = PlayerRowRules.forPack(pack);

    var players = new ArrayList<SeasonPlayerDto>();
    var problems = new ArrayList<RejectedRowDto>();
    var sets = new LinkedHashSet<String>();
    int skipped = 0;
    for (RawRow row : rows) {
      var result = rules.check(row.input());
      if (result.valid()) {
        var p = result.player();
        sets.add(p.set());
        players.add(
            new SeasonPlayerDto(
                UUID.randomUUID(),
                players.size(),
                p.name(),
                p.country(),
                p.role(),
                p.tags(),
                p.basePrice(),
                p.set(),
                p.providerId()));
      } else {
        skipped++;
        result
            .errors()
            .forEach(
                e ->
                    problems.add(
                        new RejectedRowDto(row.rowNumber(), e.field(), e.value(), e.message())));
      }
    }
    List<RejectedRowDto> listed = problems.stream().limit(REPORT_ROWS_MAX).toList();
    int more = problems.size() - listed.size();
    if (players.isEmpty()) {
      throw new NoValidRowsException(
          new ImportReportDto(
              null, pack.id(), pack.version(), rows.size(), 0, skipped, listed, more));
    }
    int version = datasets.nextVersion(pack.id());
    datasets.insert(
        UUID.randomUUID(),
        pack.id(),
        version,
        pack.version(),
        format.value(),
        admin.value(),
        clock.instant(),
        skipped,
        List.copyOf(sets),
        players);
    return new ImportReportDto(
        version, pack.id(), pack.version(), rows.size(), players.size(), skipped, listed, more);
  }

  /** The newest version of the pack's dataset, with players; empty when none was imported. */
  @Transactional(readOnly = true)
  public Optional<SeasonDatasetDto> latest(String packId) {
    return datasets.latest(packId);
  }

  /** The newest version without its players. */
  @Transactional(readOnly = true)
  public Optional<DatasetSummaryDto> latestSummary(String packId) {
    return datasets.latestSummary(packId);
  }

  /** The newest version's distinct roles, tags and prices, without loading its players. */
  @Transactional(readOnly = true)
  public Optional<DatasetFacetsDto> latestFacets(String packId) {
    return datasets.latestFacets(packId);
  }

  /** Every version of the pack's dataset, newest first. */
  @Transactional(readOnly = true)
  public List<DatasetSummaryDto> list(String packId) {
    packs.get(packId);
    return datasets.list(packId);
  }

  /** One player of one dataset version, for comparing a room's copy with its source. */
  @Transactional(readOnly = true)
  public Optional<SeasonPlayerDto> player(UUID datasetId, UUID playerId) {
    return datasets.player(datasetId, playerId);
  }
}
