package com.tradeguide.domain.broker;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 승인 판정 집계의 규칙을 고정한다. 이 판정이 틀리면 화면은 매매 원장을 바꾸는 버튼을
 * 잘못된 근거로 열거나 닫는다.
 */
class BrokerOrderImportApprovalAssessmentTest {

    @Test
    void derivesBaselineExcludedAndWritableCountsFromTheCountsItAlreadyHas() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MATCHED,
                counts(10, 6, 2, 1, 1),
                4,
                1,
                0,
                0,
                LocalDateTime.of(2026, 9, 3, 0, 0),
                true,
                false);

        assertThat(assessment.stagedCount()).isEqualTo(6);
        assertThat(assessment.eligibleCount()).isEqualTo(4);
        assertThat(assessment.baselineExcludedCount()).isEqualTo(2);
        assertThat(assessment.alreadyLinkedCount()).isEqualTo(1);
        assertThat(assessment.writableCount()).isEqualTo(3);
        assertThat(assessment.approvable()).isTrue();
        assertThat(assessment.blocker()).isNull();
    }

    /**
     * 제외와 의심을 한 덩어리로 세지 않는다. 제외는 정상 동작이고 의심은 사람이 봐야 하는
     * 문제다. 합쳐 놓으면 사용자는 매번 전량을 다시 확인해야 한다.
     */
    @Test
    void separatesNormalExclusionsFromItemsThatNeedAHumanDecision() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                counts(10, 6, 2, 1, 1),
                6,
                0,
                0,
                0,
                null,
                true,
                false);

        // 체결 없음 2 + 이미 반영 1
        assertThat(assessment.excludedCount()).isEqualTo(3);
        // 수기 매매 중복 의심 1
        assertThat(assessment.suspectedCount()).isEqualTo(1);
        assertThat(assessment.unresolvedSuspectedCount()).isEqualTo(1);
    }

    /**
     * 반영 허용으로 재판정한 의심 항목은 반영 후보에 든다. 원래 분류 건수({@code stagedCount})는
     * 바꾸지 않고 재판정 건수를 따로 더해, 기존 필드의 의미를 흔들지 않는다.
     */
    @Test
    void countsSuspectedItemsAllowedByAnOverrideAsCandidatesWithoutChangingTheStagedCount() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MATCHED,
                counts(10, 5, 2, 1, 1, 1),
                7,
                0,
                2,
                0,
                null,
                true,
                false);

        assertThat(assessment.stagedCount()).isEqualTo(5);
        assertThat(assessment.overrideAllowedCount()).isEqualTo(2);
        assertThat(assessment.eligibleCount()).isEqualTo(7);
        assertThat(assessment.baselineExcludedCount()).isZero();
        assertThat(assessment.writableCount()).isEqualTo(7);
        assertThat(assessment.suspectedCount()).isEqualTo(2);
        assertThat(assessment.unresolvedSuspectedCount()).isZero();
        assertThat(assessment.approvable()).isTrue();
    }

    /**
     * 재판정하지 않은 의심 항목은 승인을 막지 않는다. 반영 후보가 아니라서 반영되지 않을 뿐이며,
     * 남은 건수로 화면이 "아직 판단하지 않은 항목"을 안내할 수 있어야 한다.
     */
    @Test
    void reportsUnresolvedSuspectedItemsWithoutBlockingTheRemainingCandidates() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MATCHED,
                counts(10, 5, 2, 1, 1, 1),
                5,
                0,
                0,
                1,
                null,
                true,
                false);

        assertThat(assessment.overrideKeptExcludedCount()).isEqualTo(1);
        assertThat(assessment.unresolvedSuspectedCount()).isEqualTo(1);
        assertThat(assessment.writableCount()).isEqualTo(5);
        assertThat(assessment.approvable()).isTrue();
    }

    /** 반영 후보가 전부 재판정에서 왔어도 승인할 수 있다. */
    @Test
    void opensApprovalWhenEveryCandidateComesFromAnAllowOverride() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                counts(3, 0, 1, 0, 1, 1),
                2,
                0,
                2,
                0,
                null,
                true,
                false);

        assertThat(assessment.stagedCount()).isZero();
        assertThat(assessment.approvable()).isTrue();
        assertThat(assessment.blocker()).isNull();
    }

    /** 의심 항목을 전부 제외 유지로 판단했으면 반영 후보가 없다는 기존 사유로 막는다. */
    @Test
    void reportsNoCandidatesWhenEverySuspectedItemIsKeptExcluded() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MATCHED,
                counts(2, 0, 0, 0, 1, 1),
                0,
                0,
                0,
                2,
                null,
                true,
                false);

        assertThat(assessment.blocker()).isEqualTo(BrokerOrderImportApprovalBlocker.NO_STAGED_ITEMS);
        assertThat(assessment.unresolvedSuspectedCount()).isZero();
    }

    /** 재판정은 의심 항목에만 붙는다. 그보다 많다면 남은 의심 건수가 음수가 된다. */
    @Test
    void rejectsMoreOverridesThanSuspectedItems() {
        assertThatThrownBy(() -> BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MATCHED,
                counts(10, 6, 2, 1, 1),
                7,
                0,
                1,
                1,
                null,
                true,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("재판정 건수");
    }

    /**
     * 대조 기준이 아직 없는 것은 불일치가 아니다. 여기서 막으면 스냅샷을 한 번도 받지 않은
     * 계좌는 영원히 승인할 수 없게 된다. 서버 승인 경로도 이 상태는 통과시킨다.
     */
    @Test
    void allowsApprovalWhenThereIsNoSnapshotToReconcileAgainst() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                counts(10, 6, 2, 1, 1),
                6,
                0,
                0,
                0,
                null,
                true,
                false);

        assertThat(assessment.approvable()).isTrue();
    }

    @Test
    void refusesApprovalWhenTheReplayDidNotHold() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.REPLAY_FAILED,
                counts(10, 6, 2, 1, 1),
                6,
                0,
                0,
                0,
                null,
                true,
                false);

        assertThat(assessment.approvable()).isFalse();
        assertThat(assessment.blocker())
                .isEqualTo(BrokerOrderImportApprovalBlocker.RECONCILIATION_REPLAY_FAILED);
    }

    @Test
    void refusesApprovalForARunThatNeverFinishedStaging() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.FAILED,
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                BrokerOrderImportCounts.empty(),
                0,
                0,
                0,
                0,
                null,
                true,
                false);

        assertThat(assessment.approvable()).isFalse();
        assertThat(assessment.blocker()).isEqualTo(BrokerOrderImportApprovalBlocker.RUN_NOT_STAGED);
    }

    @Test
    void reportsAnEmptyRunAsHavingNoCandidatesRatherThanABaselineProblem() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MATCHED,
                counts(3, 0, 2, 1, 0),
                0,
                0,
                0,
                0,
                null,
                true,
                false);

        assertThat(assessment.blocker()).isEqualTo(BrokerOrderImportApprovalBlocker.NO_STAGED_ITEMS);
    }

    /**
     * 대조가 불일치면 부분 커버 여부와 무관하게 먼저 대조 불일치로 거부한다. 새 불완전 이력
     * 검사가 대조가 이미 잡아내는 사례에 이중으로 끼어들지 않는지 확인하는 핵심 테스트다.
     */
    @Test
    void reconciliationMismatchTakesPrecedenceOverTheIncompleteCoverageCheck() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MISMATCHED,
                counts(10, 6, 2, 1, 1),
                4,
                1,
                0,
                0,
                null,
                false,
                false);

        assertThat(assessment.blocker())
                .isEqualTo(BrokerOrderImportApprovalBlocker.RECONCILIATION_MISMATCHED);
    }

    /**
     * 부분 커버이고 대조 기준이 없어(NOT_AVAILABLE) 이력 누락을 대조로 배제할 수 없는데
     * 아직 확인하지 않았다면 새 블로커로 막는다.
     */
    @Test
    void blocksApprovalOfAnIncompleteCoverageRunThatHasNoReconciliationBaselineAndIsNotAcknowledged() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                counts(10, 6, 2, 1, 1),
                4,
                1,
                0,
                0,
                null,
                false,
                false);

        assertThat(assessment.approvable()).isFalse();
        assertThat(assessment.blocker())
                .isEqualTo(BrokerOrderImportApprovalBlocker.INCOMPLETE_COVERAGE_NOT_ACKNOWLEDGED);
        assertThat(assessment.coverageAcknowledgementRequired()).isTrue();
    }

    /**
     * 부분 커버라도 대조가 MATCHED면 이력 누락 가능성을 이미 배제한 것이므로 새 검사를
     * 건너뛰고 승인 가능해야 한다. 대조 안전망과 새 정책의 역할을 겹치지 않게 나누는 핵심 근거다.
     */
    @Test
    void skipsTheIncompleteCoverageCheckWhenReconciliationIsMatched() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MATCHED,
                counts(10, 6, 2, 1, 1),
                4,
                1,
                0,
                0,
                null,
                false,
                false);

        assertThat(assessment.approvable()).isTrue();
        assertThat(assessment.blocker()).isNull();
        assertThat(assessment.fullyCovered()).isFalse();
        assertThat(assessment.coverageAcknowledgementRequired()).isFalse();
    }

    /** 요청 구간을 끝까지 커버했으면 대조 상태와 무관하게 새 검사는 항상 통과한다. */
    @Test
    void skipsTheIncompleteCoverageCheckWhenTheRunIsFullyCovered() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                counts(10, 6, 2, 1, 1),
                4,
                1,
                0,
                0,
                null,
                true,
                false);

        assertThat(assessment.approvable()).isTrue();
        assertThat(assessment.blocker()).isNull();
    }

    /** 이미 확인된 실행은 다시 묻지 않는다. */
    @Test
    void skipsTheIncompleteCoverageCheckWhenAlreadyAcknowledged() {
        BrokerOrderImportApprovalAssessment assessment = BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.NOT_AVAILABLE,
                counts(10, 6, 2, 1, 1),
                4,
                1,
                0,
                0,
                null,
                false,
                true);

        assertThat(assessment.approvable()).isTrue();
        assertThat(assessment.blocker()).isNull();
    }

    /** 후보보다 반영 대상이 많으면 화면이 실제보다 많은 건수를 반영 대상으로 안내한다. */
    @Test
    void rejectsAnEligibleCountLargerThanTheCandidateCount() {
        assertThatThrownBy(() -> BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MATCHED,
                counts(10, 6, 2, 1, 1),
                7,
                0,
                0,
                0,
                null,
                true,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("반영 후보");
    }

    @Test
    void rejectsAnAlreadyLinkedCountLargerThanTheEligibleCount() {
        assertThatThrownBy(() -> BrokerOrderImportApprovalAssessment.of(
                BrokerOrderImportRunStatus.STAGED,
                BrokerOrderReconciliationStatus.MATCHED,
                counts(10, 6, 2, 1, 1),
                4,
                5,
                0,
                0,
                null,
                true,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 연계된");
    }

    /** 승인 가능한데 사유가 붙어 있거나, 불가한데 사유가 없으면 화면이 설명할 말을 잃는다. */
    @Test
    void rejectsAnApprovabilityThatDisagreesWithItsReason() {
        assertThatThrownBy(() -> new BrokerOrderImportApprovalAssessment(
                6, 4, 2, 0, 4, 3, 1, 0, 0, 1, 0, null, true, false, true,
                BrokerOrderImportApprovalBlocker.NO_STAGED_ITEMS))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new BrokerOrderImportApprovalAssessment(
                6, 4, 2, 0, 4, 3, 1, 0, 0, 1, 0, null, true, false, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BrokerOrderImportCounts counts(
            int fetchedCount,
            int stagedCount,
            int notFilledCount,
            int alreadyImportedCount,
            int manualOverlapSuspectedCount
    ) {
        return counts(fetchedCount, stagedCount, notFilledCount, alreadyImportedCount, manualOverlapSuspectedCount, 0);
    }

    /**
     * 배타 분류 합이 조회 건수와 같아야 한다는 {@link BrokerOrderImportCounts}의 불변식을 지키는
     * 건수를 만든다. 그 규칙이 깨진 건수로는 판정 자체가 성립하지 않는다.
     */
    private BrokerOrderImportCounts counts(
            int fetchedCount,
            int stagedCount,
            int notFilledCount,
            int alreadyImportedCount,
            int manualOverlapSuspectedCount,
            int duplicateSuspectedCount
    ) {
        return new BrokerOrderImportCounts(
                fetchedCount, stagedCount, notFilledCount, 0, 0, 0, 0, 0, 0, 0,
                alreadyImportedCount, manualOverlapSuspectedCount, duplicateSuspectedCount,
                0, 0, 0, 0, 0, 0);
    }
}
