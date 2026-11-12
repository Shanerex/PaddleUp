package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.rooms.service.ScoringRoomPort;
import com.paddleup.scoring.repository.MatchStatsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** AC-14: the freeze instant is the earliest start of the room's dataset matches. */
class FreezePolicyTest {

  static final Instant START = Instant.parse("2027-04-01T14:00:00Z");

  final ScoringRoomPort port = mock(ScoringRoomPort.class);
  final MatchStatsRepository matches = mock(MatchStatsRepository.class);
  final FreezePolicy policy = new FreezePolicy(port, matches);
  final UUID room = UUID.randomUUID();
  final UUID dataset = UUID.randomUUID();

  private void datasetIs(UUID id) {
    when(port.ruleInputs(room))
        .thenReturn(new ScoringInputsDto("p", "1", List.of(), List.of(), null, null, id));
  }

  @Test
  void itIsTheEarliestStartOfTheDatasetMatches() {
    datasetIs(dataset);
    when(matches.earliestStart(dataset)).thenReturn(Optional.of(START));

    assertThat(policy.freezesAt(room)).contains(START);
  }

  @Test
  void aRoomWithNoPoolOrDatasetHasNoFreezeInstant() {
    datasetIs(null);

    assertThat(policy.freezesAt(room)).isEmpty();
  }

  @Test
  void aDatasetWithNoMatchesHasNoFreezeInstant() {
    datasetIs(dataset);
    when(matches.earliestStart(dataset)).thenReturn(Optional.empty());

    assertThat(policy.freezesAt(room)).isEmpty();
  }

  @Test
  void frozenFromTheStartInstantNotOneSecondBefore() {
    var at = Optional.of(START);

    assertThat(FreezePolicy.frozen(null, at, START.minusSeconds(1))).isFalse();
    assertThat(FreezePolicy.frozen(null, at, START)).isTrue();
    assertThat(FreezePolicy.frozen(null, at, START.plusSeconds(1))).isTrue();
  }

  @Test
  void aRoomWithNoFreezeInstantIsNeverFrozenUnlessItWasRecordedFrozen() {
    assertThat(FreezePolicy.frozen(null, Optional.empty(), START)).isFalse();
    assertThat(FreezePolicy.frozen(START, Optional.empty(), START)).isTrue();
  }

  @Test
  void aRecordedFreezeHoldsEvenIfTheMatchIsMovedLater() {
    assertThat(FreezePolicy.frozen(START, Optional.of(START.plusSeconds(86400)), START)).isTrue();
  }
}
