package com.paddleup.auction.service;

import static com.paddleup.auction.service.AuctionFixtures.A;
import static com.paddleup.auction.service.AuctionFixtures.B;
import static com.paddleup.auction.service.AuctionFixtures.ROOM;
import static com.paddleup.auction.service.AuctionFixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paddleup.auction.dto.AuctionEventDto;
import com.paddleup.auction.model.AuctionEvent;
import com.paddleup.auction.model.AuctionState;
import com.paddleup.auction.model.Events;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

/** CR-3, S-2: a franchise's picks leave the server only to that franchise. */
class PicksBroadcastTest {

  private static final AuctionEvent PICKS =
      Events.picksSubmitted(3, T0, A, UUID.randomUUID(), UUID.randomUUID(), List.of(4, 5));

  private static AuctionState state() {
    return AuctionFixtures.lotOneOpen();
  }

  @Test
  void theEventViewKeepsTheLotsOnlyForTheSubmittingFranchise() {
    var own = AuctionViews.event(PICKS, state(), null, A);
    var other = AuctionViews.event(PICKS, state(), null, B);
    var nobody = AuctionViews.event(PICKS, state(), null, null);

    assertThat(own.payload()).containsEntry("lots", List.of(4, 5));
    assertThat(other.payload()).doesNotContainKey("lots");
    assertThat(nobody.payload()).doesNotContainKey("lots");
    assertThat(other.franchiseId()).isEqualTo(A);
    assertThat(other.kind()).isEqualTo("picks_submitted");
  }

  @Test
  void theBroadcastToTheWholeRoomNeverCarriesTheLots() {
    SimpMessageSendingOperations out = mock(SimpMessageSendingOperations.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<SimpMessageSendingOperations> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(out);
    var broadcaster = new AuctionBroadcaster(provider, Clock.fixed(T0, ZoneOffset.UTC));

    broadcaster.events(ROOM, List.of(PICKS), state());

    ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
    verify(out).convertAndSend(eq("/topic/auctions/" + ROOM), sent.capture());
    var dto = (AuctionEventDto) sent.getValue();
    assertThat(dto.kind()).isEqualTo("picks_submitted");
    assertThat(dto.franchiseId()).isEqualTo(A);
    assertThat(dto.payload()).doesNotContainKey("lots");
    assertThat(dto.toString()).doesNotContain("4, 5");
  }
}
