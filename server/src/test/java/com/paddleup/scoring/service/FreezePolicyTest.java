package com.paddleup.scoring.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.paddleup.rooms.dto.ScoringInputsDto;
import com.paddleup.scoring.model.RuleSetRow;
import com.paddleup.scoring.repository.MatchStatsRepository;
import com.paddleup.scoring.repository.RuleSetRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * AC-14: the freeze instant is the earliest start of the room's dataset matches at or after the
 * room was created.
 */
class FreezePolicyTest {

  static final Instant START = Instant.parse("2027-04-01T14:00:00Z");

  static final Instant CREATED = START.minusSeconds(86_400);

  final MatchStatsRepository matches = mock(MatchStatsRepository.class);
  final RuleSetRepository sets = mock(RuleSetRepository.class);
  final FreezePolicy policy = new FreezePolicy(matches, sets);
  final UUID room = UUID.randomUUID();
  final UUID dataset = UUID.randomUUID();
  ScoringInputsDto inputs;

  private void datasetIs(UUID id) {
    inputs = new ScoringInputsDto("p", "1", List.of(), List.of(), null, null, id, CREATED);
  }

  @Test
  void itIsTheEarliestStartOfTheDatasetMatches() {
    datasetIs(dataset);
    when(matches.earliestStart(dataset, CREATED)).thenReturn(Optional.of(START));

    assertThat(policy.freezesAt(inputs)).contains(START);
  }

  @Test
  void aRoomWithNoPoolOrDatasetHasNoFreezeInstant() {
    datasetIs(null);

    assertThat(policy.freezesAt(inputs)).isEmpty();
  }

  @Test
  void aDatasetWithNoMatchesHasNoFreezeInstant() {
    datasetIs(dataset);
    when(matches.earliestStart(dataset, CREATED)).thenReturn(Optional.empty());

    assertThat(policy.freezesAt(inputs)).isEmpty();
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

  @Test
  void aMatchThatStartedBeforeTheRoomExistedDoesNotCount() {
    datasetIs(dataset);
    when(matches.earliestStart(dataset)).thenReturn(Optional.of(CREATED.minusSeconds(60)));
    when(matches.earliestStart(dataset, CREATED)).thenReturn(Optional.of(START));

    assertThat(policy.freezesAt(inputs)).contains(START);
  }

  @Test
  void recordIfDueRecordsTheScheduledInstantOnceTheClockReachesIt() {
    datasetIs(dataset);
    when(matches.earliestStart(dataset, CREATED)).thenReturn(Optional.of(START));
    var open = new RuleSetRow(room, null, 0, null, START, null);

    assertThat(policy.recordIfDue(room, open, inputs, START.minusSeconds(1))).isFalse();
    verify(sets, never()).markFrozen(any(), any());

    assertThat(policy.recordIfDue(room, open, inputs, START.plusSeconds(5))).isTrue();
    verify(sets).markFrozen(room, START);
  }

  @Test
  void aRecordedRowIsFrozenWithoutLookingAtTheSchedule() {
    datasetIs(dataset);
    var recorded = new RuleSetRow(room, null, 0, START, START, null);

    assertThat(policy.recordIfDue(room, recorded, inputs, START.minusSeconds(3600))).isTrue();
    verify(sets, never()).markFrozen(any(), any());
  }

  @Test
  void theRecordedInstantIsReportedOverTheSchedule() {
    var later = Optional.of(START.plusSeconds(86400));

    assertThat(FreezePolicy.reported(START, later)).contains(START);
    assertThat(FreezePolicy.reported(null, later)).isEqualTo(later);
  }
}
