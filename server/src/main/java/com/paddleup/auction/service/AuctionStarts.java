package com.paddleup.auction.service;

import com.paddleup.auction.repository.AuctionRepository;
import com.paddleup.rooms.service.AuctionStartSource;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Feeds the rooms hub list with each scheduled auction's start. */
@Service
public class AuctionStarts implements AuctionStartSource {

  private final AuctionRepository headers;

  AuctionStarts(AuctionRepository headers) {
    this.headers = headers;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Instant> startsAt(Collection<UUID> roomIds) {
    return headers.startsAt(roomIds);
  }
}
