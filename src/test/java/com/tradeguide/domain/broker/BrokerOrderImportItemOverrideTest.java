package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 재판정 감사 기록의 규칙을 고정한다. 결과 상태가 기존 스테이징 상태의 의미를 벗어나거나, 빈 사유가
 * 남거나, 의심이 아닌 항목이 재판정되면 "무엇을 근거로 원장에 넣었는지"를 설명할 수 없게 된다.
 */
class BrokerOrderImportItemOverrideTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 8, 9, 30);

    private final Member member = mock(Member.class);

    @Test
    void recordsAnAllowDecisionAsStagedWhileKeepingTheOriginalSuspectedStatus() {
        BrokerOrderImportItem item = item(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);

        BrokerOrderImportItemOverride override = new BrokerOrderImportItemOverride(
                item, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "  수기 기록과 별개 체결  ", member, CREATED_AT);

        assertThat(override.getOriginalStagingStatus()).isEqualTo(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
        assertThat(override.getOriginalSkipReasonCode()).isEqualTo(BrokerOrderSkipReason.MANUAL_OVERLAP);
        assertThat(override.getResultingStagingStatus()).isEqualTo(BrokerOrderStagingStatus.STAGED);
        assertThat(override.getExternalOrderId()).isEqualTo("order-2");
        assertThat(override.getReason()).isEqualTo("수기 기록과 별개 체결");
        assertThat(override.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(BrokerOrderImportItemOverride.effectiveStatusOf(item, override))
                .isEqualTo(BrokerOrderStagingStatus.STAGED);
    }

    /** 제외 유지는 새 상태를 만들지 않는다. 원래 의심 상태 그대로가 결과다. */
    @Test
    void recordsAKeepDecisionAsTheOriginalSuspectedStatus() {
        BrokerOrderImportItem item = item(BrokerOrderStagingStatus.DUPLICATE_SUSPECTED);

        BrokerOrderImportItemOverride override = new BrokerOrderImportItemOverride(
                item, BrokerOrderOverrideDecision.KEEP_EXCLUDED, "같은 주문의 식별자 변경으로 판단", member, CREATED_AT);

        assertThat(override.getResultingStagingStatus()).isEqualTo(BrokerOrderStagingStatus.DUPLICATE_SUSPECTED);
        assertThat(BrokerOrderImportItemOverride.effectiveStatusOf(item, override))
                .isEqualTo(BrokerOrderStagingStatus.DUPLICATE_SUSPECTED);
    }

    @Test
    void fallsBackToTheClassifierStatusWhenThereIsNoOverride() {
        BrokerOrderImportItem item = item(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);

        assertThat(BrokerOrderImportItemOverride.effectiveStatusOf(item, null))
                .isEqualTo(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void rejectsABlankReason(String reason) {
        BrokerOrderImportItem item = item(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);

        assertThatThrownBy(() -> new BrokerOrderImportItemOverride(
                item, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, reason, member, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("재판정 사유는 필수입니다.");
    }

    @Test
    void acceptsAReasonAtTheMaximumLengthAndRejectsOneCharacterMore() {
        BrokerOrderImportItem item = item(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
        String longest = "가".repeat(BrokerOrderImportItemOverride.REASON_MAX_LENGTH);

        assertThat(new BrokerOrderImportItemOverride(
                item, BrokerOrderOverrideDecision.KEEP_EXCLUDED, longest, member, CREATED_AT).getReason())
                .hasSize(BrokerOrderImportItemOverride.REASON_MAX_LENGTH);
        assertThatThrownBy(() -> new BrokerOrderImportItemOverride(
                item, BrokerOrderOverrideDecision.KEEP_EXCLUDED, longest + "가", member, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("500자");
    }

    /** 체결 없음·값 누락·이미 반영 같은 제외는 사람 판단의 문제가 아니므로 재판정으로 열지 않는다. */
    @ParameterizedTest
    @EnumSource(value = BrokerOrderStagingStatus.class,
            names = {"MANUAL_OVERLAP_SUSPECTED", "DUPLICATE_SUSPECTED"},
            mode = EnumSource.Mode.EXCLUDE)
    void rejectsItemsThatAreNotSuspected(BrokerOrderStagingStatus status) {
        BrokerOrderImportItem item = item(status);

        assertThat(BrokerOrderImportItemOverride.isOverridable(status)).isFalse();
        assertThatThrownBy(() -> new BrokerOrderImportItemOverride(
                item, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "사유", member, CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 제외 유지 재판정은 원장 반영의 근거가 될 수 없다. 링크가 그것을 가리키면 감사가 모순된다. */
    @Test
    void refusesToLinkALedgerRowToAKeepExcludedOverride() {
        BrokerOrderImportItem item = item(BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED);
        BrokerOrderImportItemOverride keep = new BrokerOrderImportItemOverride(
                item, BrokerOrderOverrideDecision.KEEP_EXCLUDED, "제외 유지", member, CREATED_AT);

        assertThatThrownBy(() -> new BrokerOrderLedgerLink(
                mock(BrokerAccount.class), mock(BrokerOrderImportRun.class), item, 900L, member, CREATED_AT, keep))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BrokerOrderImportItem item(BrokerOrderStagingStatus status) {
        BrokerOrderImportItem item = mock(BrokerOrderImportItem.class);
        when(item.getStagingStatus()).thenReturn(status);
        when(item.getExternalOrderId()).thenReturn("order-2");
        when(item.getSkipReasonCode()).thenReturn(
                status == BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED ? BrokerOrderSkipReason.MANUAL_OVERLAP : null);
        return item;
    }
}
