package com.paddleup.scoring.util;

import com.paddleup.packs.dto.RuleDto;
import com.paddleup.packs.dto.RuleSetDto;
import com.paddleup.scoring.dto.RuleDiffDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What changed between two rule sets, by rule id. Stateless. */
public final class RuleDiffs {

  private RuleDiffs() {}

  /**
   * Ids only in {@code after} are added, only in {@code before} removed, in both but unequal
   * changed.
   */
  public static RuleDiffDto between(RuleSetDto before, RuleSetDto after) {
    Map<String, RuleDto> old = byId(before);
    Map<String, RuleDto> now = byId(after);
    List<String> added = new ArrayList<>();
    List<String> removed = new ArrayList<>();
    List<String> changed = new ArrayList<>();
    now.forEach(
        (id, rule) -> {
          if (!old.containsKey(id)) {
            added.add(id);
          } else if (!old.get(id).equals(rule)) {
            changed.add(id);
          }
        });
    old.keySet().stream().filter(id -> !now.containsKey(id)).forEach(removed::add);
    return new RuleDiffDto(added, removed, changed);
  }

  private static Map<String, RuleDto> byId(RuleSetDto set) {
    Map<String, RuleDto> byId = new LinkedHashMap<>();
    for (RuleDto rule : set.rules()) {
      byId.putIfAbsent(rule.id(), rule);
    }
    return byId;
  }
}
