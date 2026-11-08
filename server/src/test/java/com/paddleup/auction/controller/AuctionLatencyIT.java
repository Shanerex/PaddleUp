package com.paddleup.auction.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.paddleup.auction.AbstractAuctionIT;
import com.paddleup.auction.StompProbe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** AC-15: with 10 subscribers, bid-to-broadcast is under 500 ms at p95 over 100 bids. */
@Tag("local")
class AuctionLatencyIT extends AbstractAuctionIT {

  @LocalServerPort int port;

  @Test
  void bidToBroadcastIsUnder500msAtP95OverOneHundredBidsToTenSubscribers() throws Exception {
    Room room = room(2, 6);
    jdbc.update(
        "UPDATE rooms SET config = jsonb_set(config, '{purse}', '1000000') WHERE id = ?",
        UUID.fromString(room.id()));
    goLive(room);

    List<StompProbe> probes = new ArrayList<>();
    try {
      for (int i = 0; i < 10; i++) {
        Person who = i % 2 == 0 ? room.manager(0) : room.manager(1);
        StompProbe probe = StompProbe.connect(json, port, who.cookie(), null, null);
        assertThat(probe.connected()).isTrue();
        probe.subscribe("/topic/auctions/" + room.id());
        probes.add(probe);
      }
      Thread.sleep(500);

      long amount = 20;
      int turn = 0;
      List<Long> samples = new ArrayList<>();
      for (int n = 0; n < 105; n++) {
        Person bidder = room.manager(turn++ % 2);
        long sentAt = System.nanoTime();
        var response = bid(room, bidder, 1, amount);
        assertThat(outcome(response)).isEqualTo("accepted");
        long seq = body(response).get("seq").asLong();
        for (StompProbe probe : probes) {
          long deadline = System.nanoTime() + 5_000_000_000L;
          while (!probe.arrivedNanos.containsKey(seq) && System.nanoTime() < deadline) {
            Thread.sleep(1);
          }
          Long arrived = probe.arrivedNanos.get(seq);
          assertThat(arrived).as("seq " + seq + " reached every subscriber").isNotNull();
          if (n >= 5) {
            samples.add((arrived - sentAt) / 1_000_000);
          }
        }
        amount =
            amount < 100
                ? amount + 5
                : amount < 200 ? amount + 10 : amount < 500 ? amount + 20 : amount + 25;
      }

      Collections.sort(samples);
      long p95 = samples.get((int) Math.ceil(samples.size() * 0.95) - 1);
      System.out.println(
          "bid-to-broadcast p95="
              + p95
              + "ms max="
              + samples.get(samples.size() - 1)
              + "ms n="
              + samples.size());
      assertThat(samples).hasSize(1000);
      assertThat(p95).isLessThan(500);
    } finally {
      probes.forEach(StompProbe::close);
    }
  }
}
