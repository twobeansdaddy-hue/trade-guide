package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderImportCounts;
import com.tradeguide.domain.broker.BrokerOrderLifecycle;
import com.tradeguide.domain.broker.BrokerOrderRecord;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderSkipReason;
import com.tradeguide.domain.broker.BrokerOrderStagedOrder;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 증권사가 보고한 주문을 반영 후보와 제외 사유로 나눈다. DB도 HTTP도 모르는 순수 판정이라
 * 규칙 하나하나를 표처럼 테스트할 수 있다.
 *
 * <p>판정 원칙은 두 가지다.
 *
 * <ol>
 *   <li><b>버리지 않는다.</b> 반영할 수 없는 주문도 사유와 함께 항목으로 남긴다.
 *       사용자가 "왜 이 거래가 안 들어왔지"를 화면에서 직접 확인할 수 있어야 한다.</li>
 *   <li><b>애매하면 자동으로 고르지 않는다.</b> 중복 의심은 기본 반영도 기본 무시도 아니고
 *       사용자 판단으로 넘긴다. 오탐의 대가는 확인 한 번이고, 미탐의 대가는 원장 오염이다.</li>
 * </ol>
 */
@Component
public class BrokerOrderStagingClassifier {

    /**
     * 증권사가 보고하는 시각은 KST 오프셋을 포함한다. 수동 기록과 같은 날인지 비교할 때도
     * 같은 기준을 써야 한다. UTC로 비교하면 KST 오전 8시 거래가 전날로 넘어간다.
     */
    static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Seoul");

    private static final BigDecimal HALF = new BigDecimal("0.5");

    /**
     * 조회한 주문을 분류한다.
     *
     * @param records   어댑터가 돌려준 주문. 미지원 시장·통화는 이미 걸러진 뒤다
     * @param context   중복·수동 기록 대조에 필요한 이미 아는 사실들
     * @return 항목별 판정과 사유별 건수
     */
    public Result classify(List<BrokerOrderRecord> records, StagingContext context) {
        if (records == null || context == null) {
            throw new IllegalArgumentException("주문 분류 입력이 올바르지 않습니다.");
        }

        List<BrokerOrderStagedOrder> stagedOrders = new ArrayList<>();
        Counter counter = new Counter();

        for (BrokerOrderRecord record : records) {
            String fingerprint = BrokerOrderFingerprint.of(record);
            Decision decision = decide(record, fingerprint, context);
            counter.count(decision);

            boolean amountMismatch = hasUnexplainedAmountGap(record);
            boolean feeUnknown = record.hasFill() && record.commission() == null;
            boolean buyTax = record.side() == BrokerOrderSide.BUY
                    && record.tax() != null
                    && record.tax().signum() != 0;

            counter.countSignals(amountMismatch, feeUnknown, buyTax);

            stagedOrders.add(new BrokerOrderStagedOrder(
                    record,
                    context.displayNameOf(record.market(), record.ticker()),
                    decision.status(),
                    decision.reason(),
                    fingerprint,
                    amountMismatch,
                    feeUnknown,
                    buyTax
            ));
        }

        return new Result(stagedOrders, counter);
    }

    /**
     * 판정 순서가 곧 우선순위다. 한 주문이 여러 사유에 걸릴 수 있으므로 순서를 고정해야
     * 사유별 건수가 서로 배타적으로 유지된다.
     *
     * <p>이미 반영된 주문을 가장 먼저 걸러낸다. 이미 원장에 있는 주문을 두고 "수동 기록과
     * 겹치는 것 같다"고 묻는 것은 사용자에게 답할 수 없는 질문을 던지는 것이다.
     */
    private Decision decide(BrokerOrderRecord record, String fingerprint, StagingContext context) {
        if (context.isAlreadyImported(record.externalOrderId())) {
            return new Decision(BrokerOrderStagingStatus.ALREADY_IMPORTED, BrokerOrderSkipReason.ALREADY_IMPORTED);
        }
        if (record.lifecycle() == BrokerOrderLifecycle.CONTROL_RECORD) {
            return new Decision(
                    BrokerOrderStagingStatus.SKIPPED_CONTROL_RECORD, BrokerOrderSkipReason.CONTROL_RECORD);
        }
        if (record.lifecycle() == BrokerOrderLifecycle.UNKNOWN) {
            return new Decision(BrokerOrderStagingStatus.SKIPPED_UNSUPPORTED, BrokerOrderSkipReason.UNKNOWN_STATUS);
        }
        if (record.lifecycle() == BrokerOrderLifecycle.PARTIALLY_FILLED_OPEN) {
            return new Decision(
                    BrokerOrderStagingStatus.PENDING_SETTLEMENT, BrokerOrderSkipReason.PARTIAL_FILL_PENDING);
        }
        if (!record.hasFill()) {
            return new Decision(BrokerOrderStagingStatus.SKIPPED_NOT_FILLED, BrokerOrderSkipReason.NOT_FILLED);
        }
        if (record.filledAt() == null) {
            // 결제일은 체결 시각이 아니다. 시각을 합성하면 원장 정렬이 조용히 틀어진다.
            return new Decision(
                    BrokerOrderStagingStatus.SKIPPED_UNSUPPORTED, BrokerOrderSkipReason.MISSING_EXECUTION_TIME);
        }
        if (record.averageFilledPrice() == null) {
            return new Decision(
                    BrokerOrderStagingStatus.SKIPPED_UNSUPPORTED, BrokerOrderSkipReason.MISSING_AVERAGE_PRICE);
        }

        String knownOrderId = context.orderIdWithSameFingerprint(fingerprint);
        if (knownOrderId != null && !knownOrderId.equals(record.externalOrderId())) {
            // 내용이 같은데 식별자만 다르다. 제공자가 재조회마다 다른 식별자를 줄 가능성이 있다.
            return new Decision(
                    BrokerOrderStagingStatus.DUPLICATE_SUSPECTED, BrokerOrderSkipReason.DUPLICATE_FINGERPRINT);
        }
        if (context.hasOverlappingManualTrade(record)) {
            return new Decision(
                    BrokerOrderStagingStatus.MANUAL_OVERLAP_SUSPECTED, BrokerOrderSkipReason.MANUAL_OVERLAP);
        }

        return new Decision(BrokerOrderStagingStatus.STAGED, null);
    }

    /**
     * 평균가 × 수량과 제공자가 보고한 체결 금액의 괴리가 <b>평균가 반올림으로 설명되는지</b> 본다.
     *
     * <p>제공자 명세는 평균 체결가의 소수 자릿수 상한을 정해 두지 않았다. 그래서 두 값이 정확히
     * 같기를 기대할 수 없다. 임의의 허용 오차 상수를 두는 대신, 보고된 평균가가 마지막 자리에서
     * 반올림됐을 때 생길 수 있는 최대 오차({@code 수량 × 0.5 × 10^-자릿수})를 한계로 쓴다.
     * 이 한계를 넘는 괴리는 반올림으로 설명되지 않으므로 사람이 봐야 한다.
     *
     * <p>넘더라도 값을 고치지 않는다. 원장에는 제공자가 보고한 평균가를 그대로 넣어야
     * 증권사 화면과 값이 맞는다.
     */
    private boolean hasUnexplainedAmountGap(BrokerOrderRecord record) {
        BigDecimal averagePrice = record.averageFilledPrice();
        BigDecimal filledAmount = record.filledAmount();
        if (averagePrice == null || filledAmount == null || !record.hasFill()) {
            return false;
        }

        BigDecimal quantity = record.filledQuantity();
        BigDecimal gap = averagePrice.multiply(quantity).subtract(filledAmount).abs();
        BigDecimal tolerance = quantity.abs()
                .multiply(HALF)
                .multiply(BigDecimal.ONE.movePointLeft(Math.max(averagePrice.scale(), 0)));

        return gap.compareTo(tolerance) > 0;
    }

    /** 항목별 판정과 사유별 건수. */
    public record Result(List<BrokerOrderStagedOrder> stagedOrders, Counter counter) {

        /**
         * 어댑터가 이미 걸러낸 미지원 시장·통화 건수를 합쳐 실행 전체의 건수를 만든다.
         * 어댑터 제외분까지 합쳐야 "제공자가 몇 건을 줬는지"와 사유별 합계가 맞아떨어진다.
         */
        public BrokerOrderImportCounts toCounts(
                int unsupportedMarketCount,
                int unsupportedCurrencyCount,
                int unknownEnumCount,
                int duplicateFetchCount,
                int openOrderCount
        ) {
            return counter.toCounts(
                    unsupportedMarketCount, unsupportedCurrencyCount,
                    unknownEnumCount, duplicateFetchCount, openOrderCount);
        }
    }

    private record Decision(BrokerOrderStagingStatus status, BrokerOrderSkipReason reason) {
    }

    /** 분류를 세는 가변 누산기다. 분류 규칙과 세는 일을 한 메서드에 섞지 않으려고 분리했다. */
    public static final class Counter {

        private int staged;
        private int notFilled;
        private int pendingSettlement;
        private int controlRecord;
        private int missingExecutionTime;
        private int missingAveragePrice;
        private int unknownStatus;
        private int alreadyImported;
        private int manualOverlapSuspected;
        private int duplicateSuspected;
        private int amountMismatch;
        private int feeUnknown;
        private int buyTax;

        private void count(Decision decision) {
            switch (decision.status()) {
                case STAGED -> staged++;
                case SKIPPED_NOT_FILLED -> notFilled++;
                case PENDING_SETTLEMENT -> pendingSettlement++;
                case SKIPPED_CONTROL_RECORD -> controlRecord++;
                case ALREADY_IMPORTED -> alreadyImported++;
                case MANUAL_OVERLAP_SUSPECTED -> manualOverlapSuspected++;
                case DUPLICATE_SUSPECTED -> duplicateSuspected++;
                // 이 상태 하나에 사유가 셋 붙는다. 상태만 세면 "왜 못 넣었는지"가 다시 사라진다.
                case SKIPPED_UNSUPPORTED -> countUnsupported(decision.reason());
            }
        }

        private void countUnsupported(BrokerOrderSkipReason reason) {
            switch (reason) {
                case MISSING_EXECUTION_TIME -> missingExecutionTime++;
                case MISSING_AVERAGE_PRICE -> missingAveragePrice++;
                case UNKNOWN_STATUS -> unknownStatus++;
                default -> throw new IllegalStateException("미지원 제외 사유가 아닙니다: " + reason);
            }
        }

        private void countSignals(boolean amountGap, boolean unknownFee, boolean taxOnBuy) {
            if (amountGap) {
                amountMismatch++;
            }
            if (unknownFee) {
                feeUnknown++;
            }
            if (taxOnBuy) {
                buyTax++;
            }
        }

        private BrokerOrderImportCounts toCounts(
                int unsupportedMarketCount,
                int unsupportedCurrencyCount,
                int unknownEnumCount,
                int duplicateFetchCount,
                int openOrderCount
        ) {
            int fetchedCount = staged + notFilled + pendingSettlement + controlRecord
                    + missingExecutionTime + missingAveragePrice + unknownStatus
                    + alreadyImported + manualOverlapSuspected + duplicateSuspected
                    + unsupportedMarketCount + unsupportedCurrencyCount;

            return new BrokerOrderImportCounts(
                    fetchedCount,
                    staged,
                    notFilled,
                    pendingSettlement,
                    controlRecord,
                    missingExecutionTime,
                    missingAveragePrice,
                    unknownStatus,
                    unsupportedMarketCount,
                    unsupportedCurrencyCount,
                    alreadyImported,
                    manualOverlapSuspected,
                    duplicateSuspected,
                    unknownEnumCount,
                    duplicateFetchCount,
                    amountMismatch,
                    feeUnknown,
                    buyTax,
                    openOrderCount
            );
        }
    }

    /**
     * 분류에 필요한, 이미 아는 사실들이다. 조회 결과와 달리 이 값들은 우리 DB에서 온다.
     *
     * @param alreadyImportedOrderIds  이 계좌에서 이미 원장에 반영된 주문 식별자
     * @param orderIdByFingerprint     이미 아는 주문의 지문 → 주문 식별자.
     *                                 반영된 항목과 직전 실행 항목을 모두 포함한다
     * @param manualTrades             같은 포트폴리오의 수동 입력 매매
     * @param displayNames             자산 카탈로그에서 찾은 종목명. 없으면 종목 코드를 쓴다
     */
    public record StagingContext(
            Set<String> alreadyImportedOrderIds,
            Map<String, String> orderIdByFingerprint,
            List<ManualTrade> manualTrades,
            Map<AssetKey, String> displayNames
    ) {
        public StagingContext {
            alreadyImportedOrderIds = Set.copyOf(alreadyImportedOrderIds == null ? Set.of() : alreadyImportedOrderIds);
            orderIdByFingerprint = Map.copyOf(orderIdByFingerprint == null ? Map.of() : orderIdByFingerprint);
            manualTrades = List.copyOf(manualTrades == null ? List.of() : manualTrades);
            displayNames = Map.copyOf(displayNames == null ? Map.of() : displayNames);
        }

        public static StagingContext empty() {
            return new StagingContext(Set.of(), Map.of(), List.of(), Map.of());
        }

        boolean isAlreadyImported(String externalOrderId) {
            return alreadyImportedOrderIds.contains(externalOrderId);
        }

        String orderIdWithSameFingerprint(String fingerprint) {
            return orderIdByFingerprint.get(fingerprint);
        }

        String displayNameOf(Market market, String ticker) {
            return displayNames.getOrDefault(new AssetKey(market, ticker), ticker);
        }

        /**
         * 수동 기록과 같은 거래로 보이는지 판단한다.
         *
         * <p>수동 기록에는 증권사 주문 식별자가 없어 키로 이을 방법이 자체가 없다. 그래서
         * 자동 병합은 불가능하고, 할 수 있는 것은 "같아 보인다"고 알리는 것뿐이다.
         * 수량은 오차 없이 정확히 같아야 하고 날짜는 KST 달력일이 같아야 한다.
         * 조건을 느슨하게 잡으면 서로 다른 거래를 같다고 말하게 되고, 그건 알림이 아니라 오답이다.
         */
        boolean hasOverlappingManualTrade(BrokerOrderRecord record) {
            if (record.filledAt() == null) {
                return false;
            }

            TradeType tradeType = record.side() == BrokerOrderSide.BUY ? TradeType.BUY : TradeType.SELL;
            LocalDate filledDate = LocalDate.ofInstant(record.filledAt(), REPORTING_ZONE);

            return manualTrades.stream().anyMatch(trade ->
                    trade.market() == record.market()
                            && trade.ticker().equals(record.ticker())
                            && trade.tradeType() == tradeType
                            && trade.quantity().compareTo(record.filledQuantity()) == 0
                            && LocalDate.ofInstant(trade.tradedAt(), REPORTING_ZONE).equals(filledDate));
        }
    }

    /** 수동 입력 매매 한 건에서 대조에 필요한 값만 뽑은 것이다. */
    public record ManualTrade(
            Market market,
            String ticker,
            TradeType tradeType,
            BigDecimal quantity,
            Instant tradedAt
    ) {
    }

    /** 시장과 종목 코드 한 쌍. 종목명 조회 키다. */
    public record AssetKey(Market market, String ticker) {
    }
}
