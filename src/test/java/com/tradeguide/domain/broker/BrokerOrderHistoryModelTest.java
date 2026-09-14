package com.tradeguide.domain.broker;

import com.tradeguide.domain.trade.Market;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주문 이력 조회의 제공자 중립 모델이 지키는 규칙을 고정한다.
 * 이 모델은 읽기 전용이며 매매 원장을 만들지 않는다.
 */
class BrokerOrderHistoryModelTest {

    @Test
    void rejectsQueryWithReversedOrderedPeriod() {
        assertThatThrownBy(() -> BrokerOrderHistoryQuery.closedFirstPage(
                LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 1), 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 이력 조회 시작일은 종료일보다 늦을 수 없습니다.");
    }

    @Test
    void rejectsPageSizeOutsideTheProviderRange() {
        assertThatThrownBy(() -> BrokerOrderHistoryQuery.closedFirstPage(null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 이력 페이지 크기는 1 이상 100 이하여야 합니다.");
        assertThatThrownBy(() -> BrokerOrderHistoryQuery.closedFirstPage(null, null, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 이력 페이지 크기는 1 이상 100 이하여야 합니다.");
    }

    /** 진행 중 주문은 전량 반환되므로 커서로 순회할 수 없다. 순회를 시도하는 조건 자체를 만들지 못하게 한다. */
    @Test
    void rejectsCursorOnTheOpenGroup() {
        assertThatThrownBy(() -> new BrokerOrderHistoryQuery(
                BrokerOrderStatusGroup.OPEN, null, null, "cursor", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("진행 중 주문 조회는 커서를 사용하지 않습니다.");
    }

    @Test
    void carriesTheSamePeriodAndPageSizeToTheNextCursor() {
        BrokerOrderHistoryQuery first = BrokerOrderHistoryQuery.closedFirstPage(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8), 50);

        BrokerOrderHistoryQuery next = first.withCursor("cursor-2");

        assertThat(next.statusGroup()).isEqualTo(BrokerOrderStatusGroup.CLOSED);
        assertThat(next.orderedFrom()).isEqualTo(first.orderedFrom());
        assertThat(next.orderedTo()).isEqualTo(first.orderedTo());
        assertThat(next.pageSize()).isEqualTo(50);
        assertThat(next.cursor()).isEqualTo("cursor-2");
    }

    /** 조용한 누락 금지: 레코드 수와 제외 건수의 합이 조회 건수와 다르면 페이지를 만들 수 없다. */
    @Test
    void rejectsPageWhoseCountsDoNotAddUpToTheFetchedCount() {
        assertThatThrownBy(() -> BrokerOrderHistoryPage.lastPage(
                List.of(filledOrder()), new BrokerOrderExclusionCounts(3, 1, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("주문 이력 조회 건수와 제외 건수 합계가 일치하지 않습니다.");
    }

    @Test
    void neverReportsANextPageWithoutACursor() {
        BrokerOrderHistoryPage page = new BrokerOrderHistoryPage(
                List.of(filledOrder()), " ", true, BrokerOrderExclusionCounts.none(1));

        assertThat(page.nextCursor()).isNull();
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void countsUnknownEnumValuesOutsideTheExclusionTotal() {
        BrokerOrderExclusionCounts counts = new BrokerOrderExclusionCounts(5, 1, 2, 4);

        assertThat(counts.excludedCount()).isEqualTo(3);
    }

    @Test
    void normalizesTickerAndCurrencyWithoutRewritingReportedAmounts() {
        BrokerOrderRecord record = new BrokerOrderRecord(
                " a1 ", Market.US, " aapl ", BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "usd",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("221.8650"),
                new BigDecimal("2218.65"), null, null,
                Instant.parse("2026-09-02T14:35:00Z"), Instant.parse("2026-09-02T15:12:31Z"),
                LocalDate.of(2026, 9, 5));

        assertThat(record.externalOrderId()).isEqualTo("a1");
        assertThat(record.ticker()).isEqualTo("AAPL");
        assertThat(record.currencyCode()).isEqualTo("USD");
        assertThat(record.averageFilledPrice()).isEqualTo(new BigDecimal("221.8650"));
    }

    @Test
    void rejectsRecordWithoutTheValuesEveryOrderMustHave() {
        assertThatThrownBy(() -> new BrokerOrderRecord(
                null, Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.NOT_FILLED,
                "PENDING", "LIMIT", "DAY", "USD", BigDecimal.TEN, BigDecimal.ZERO,
                null, null, null, null, Instant.parse("2026-09-02T14:35:00Z"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("증권사 주문 식별자는 필수입니다.");

        assertThatThrownBy(() -> new BrokerOrderRecord(
                "a1", Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.NOT_FILLED,
                "PENDING", "LIMIT", "DAY", "USD", BigDecimal.TEN, new BigDecimal("-1"),
                null, null, null, null, Instant.parse("2026-09-02T14:35:00Z"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("증권사 주문의 수량은 0 이상이어야 합니다.");
    }

    /** 보류 중인 부분 체결은 체결분이 있어도 원장 후보가 아니다. 더 체결될 수 있기 때문이다. */
    @Test
    void doesNotTreatPartiallyFilledOpenOrderAsLedgerCandidate() {
        BrokerOrderRecord record = new BrokerOrderRecord(
                "a1", Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN,
                "PARTIAL_FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("4"), new BigDecimal("221.86"),
                new BigDecimal("887.44"), null, null,
                Instant.parse("2026-09-02T14:35:00Z"), Instant.parse("2026-09-02T15:12:31Z"), null);

        assertThat(record.hasFill()).isTrue();
        assertThat(record.isLedgerEligible()).isFalse();
    }

    private BrokerOrderRecord filledOrder() {
        return new BrokerOrderRecord(
                "a1", Market.US, "AAPL", BrokerOrderSide.BUY, BrokerOrderLifecycle.TERMINAL_WITH_FILL,
                "FILLED", "LIMIT", "DAY", "USD",
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("221.86"),
                new BigDecimal("2218.60"), new BigDecimal("1.11"), null,
                Instant.parse("2026-09-02T14:35:00Z"), Instant.parse("2026-09-02T15:12:31Z"),
                LocalDate.of(2026, 9, 5));
    }
}
