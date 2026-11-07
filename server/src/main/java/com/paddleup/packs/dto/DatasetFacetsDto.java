package com.paddleup.packs.dto;

import java.util.HashSet;
import java.util.Set;

/**
 * What a dataset version uses, without its players: the distinct role keys, tag keys and base
 * prices. Enough to tell whether a room's pack, purse and increment ladder can take the version.
 */
public record DatasetFacetsDto(int version, Set<String> roles, Set<String> tags, Set<Long> prices) {

  public DatasetFacetsDto {
    roles = Set.copyOf(roles);
    tags = Set.copyOf(tags);
    prices = Set.copyOf(prices);
  }

  /** The facets of a dataset already loaded with its players. */
  public static DatasetFacetsDto of(SeasonDatasetDto dataset) {
    Set<String> roles = new HashSet<>();
    Set<String> tags = new HashSet<>();
    Set<Long> prices = new HashSet<>();
    for (SeasonPlayerDto p : dataset.players()) {
      roles.add(p.role());
      tags.addAll(p.tags());
      prices.add(p.basePrice());
    }
    return new DatasetFacetsDto(dataset.version(), roles, tags, prices);
  }
}
