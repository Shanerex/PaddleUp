package com.paddleup.rooms.service;

import com.paddleup.packs.dto.AppearanceRuleDto;
import com.paddleup.packs.exception.PackNotFoundException;
import com.paddleup.packs.service.PackService;
import com.paddleup.rooms.repository.RoomRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Rooms made before the appearance rule was snapshotted get the rule of their pack written into
 * their snapshot once, at startup. From then on scoring reads only the snapshot, so a later pack
 * change cannot move a room's points. A room whose pack is no longer loaded keeps no appearance
 * rule and scores no appearance bonus.
 */
@Component
public class AppearanceRuleBackfill {

  private static final Logger log = LoggerFactory.getLogger(AppearanceRuleBackfill.class);

  private final RoomRepository rooms;
  private final PackService packs;

  AppearanceRuleBackfill(RoomRepository rooms, PackService packs) {
    this.rooms = rooms;
    this.packs = packs;
  }

  /** Backfills every room still missing the rule; returns how many were written. */
  @EventListener(ApplicationReadyEvent.class)
  public int backfillMissing() {
    int written = 0;
    for (Map.Entry<UUID, String> room : rooms.withoutAppearanceRule()) {
      AppearanceRuleDto rule;
      try {
        rule = packs.get(room.getValue()).appearanceRule();
      } catch (PackNotFoundException e) {
        log.warn("Room {} has no appearance rule and its pack is not loaded", room.getKey());
        continue;
      }
      if (rule != null && rooms.setAppearanceRule(room.getKey(), rule)) {
        written++;
      }
    }
    if (written > 0) {
      log.info("Wrote the pack appearance rule into {} room snapshots", written);
    }
    return written;
  }
}
