package com.paddleup.auction.service;

import com.paddleup.auction.dto.AuditEntryDto;
import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.EventKind;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps event kinds to the audit filters and says what each audit row may show. Only the fields
 * listed for a kind leave the server: a lifeline row carries its type and nothing else (the advice
 * stays private), and no actor id is ever included. Kinds that are plain clock steps (a lot opening
 * or resuming) are not audit entries.
 */
final class AuditViews {

  private AuditViews() {}

  /** The filter a request names; {@code ALL} lists every kind that has a category. */
  enum Filter {
    ALL,
    BIDS,
    PASSES,
    LIFELINES,
    RTM,
    ADMIN;

    String value() {
      return name().toLowerCase(java.util.Locale.ROOT);
    }

    static Optional<Filter> fromValue(String value) {
      return Arrays.stream(values()).filter(f -> f.value().equals(value)).findFirst();
    }
  }

  private static final Map<EventKind, Filter> CATEGORY = new LinkedHashMap<>();

  /** The fields of each kind's payload a member may see. */
  private static final Map<EventKind, List<String>> DETAIL = new LinkedHashMap<>();

  static {
    for (EventKind k : List.of(EventKind.BID_ACCEPTED, EventKind.LOT_SOLD, EventKind.LOT_UNSOLD)) {
      CATEGORY.put(k, Filter.BIDS);
    }
    CATEGORY.put(EventKind.PASSED, Filter.PASSES);
    CATEGORY.put(EventKind.LIFELINE_SPENT, Filter.LIFELINES);
    CATEGORY.put(EventKind.LIFELINE_REFUNDED, Filter.LIFELINES);
    for (EventKind k :
        List.of(
            EventKind.SCHEDULED,
            EventKind.RESCHEDULED,
            EventKind.START_REFUSED,
            EventKind.START_NOW,
            EventKind.AUCTION_STARTED,
            EventKind.PAUSED,
            EventKind.RESUMED,
            EventKind.LOT_SKIPPED,
            EventKind.LOT_REOPENED,
            EventKind.ADJOURNED,
            EventKind.RESUME_TIME_CHANGED,
            EventKind.RESUME_LOBBY_OPENED,
            EventKind.MAIN_DONE,
            EventKind.NOMINATING_OPENED,
            EventKind.ROUND_STARTED,
            EventKind.AUCTION_ENDED)) {
      CATEGORY.put(k, Filter.ADMIN);
    }

    DETAIL.put(EventKind.BID_ACCEPTED, List.of("amount"));
    DETAIL.put(EventKind.LOT_SOLD, List.of("amount"));
    DETAIL.put(EventKind.LIFELINE_SPENT, List.of("type"));
    DETAIL.put(EventKind.LIFELINE_REFUNDED, List.of("type"));
    DETAIL.put(EventKind.SCHEDULED, List.of("startsAt"));
    DETAIL.put(EventKind.RESCHEDULED, List.of("from", "to"));
    DETAIL.put(EventKind.START_REFUSED, List.of("reason"));
    DETAIL.put(EventKind.PAUSED, List.of("heldMs"));
    DETAIL.put(EventKind.LOT_REOPENED, List.of("reason", "newLot", "amount"));
    DETAIL.put(EventKind.ADJOURNED, List.of("resumeAt", "heldMs"));
    DETAIL.put(EventKind.RESUME_TIME_CHANGED, List.of("from", "to"));
    DETAIL.put(EventKind.RESUME_LOBBY_OPENED, List.of("liveAt"));
    DETAIL.put(EventKind.MAIN_DONE, List.of("pool"));
    DETAIL.put(EventKind.ROUND_STARTED, List.of("order"));
    DETAIL.put(EventKind.PICKS_SUBMITTED, List.of("lots"));
  }

  /** The event kinds a filter lists, as wire values; the rtm filter lists none until RTM exists. */
  static List<String> kinds(Filter filter) {
    return Arrays.stream(EventKind.values())
        .filter(k -> CATEGORY.containsKey(k) || k == EventKind.PICKS_SUBMITTED)
        .filter(k -> filter == Filter.ALL || CATEGORY.get(k) == filter)
        .map(EventKind::value)
        .toList();
  }

  private static String playerOf(AuctionEvent e, AuctionState state) {
    Integer lot = e.lot();
    return lot != null && lot >= 1 && lot <= state.lots().size()
        ? state.lots().get(lot - 1).name()
        : null;
  }

  static AuditEntryDto entry(AuctionEvent e, AuctionState state) {
    Filter category = CATEGORY.get(e.kind());
    boolean lifeline = category == Filter.LIFELINES;
    Map<String, Object> detail = new LinkedHashMap<>();
    for (String key : DETAIL.getOrDefault(e.kind(), List.of())) {
      if (e.payload().containsKey(key)) {
        detail.put(key, e.payload().get(key));
      }
    }
    return new AuditEntryDto(
        e.seq(),
        e.at(),
        category == null ? "other" : category.value(),
        e.kind().value(),
        lifeline ? null : e.franchiseId(),
        e.lot(),
        lifeline ? null : playerOf(e, state),
        detail);
  }
}
