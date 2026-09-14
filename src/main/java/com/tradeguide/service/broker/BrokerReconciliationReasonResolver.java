package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerOrderImportItem;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerReconciliationReasonCode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 정합성 점검 줄 한 건의 차이 사유 후보를 계산한다. 증권사를 호출하지 않고, 이미 저장된
 * 주문 이력 실행·개시 잔고 승인 이력·제공자 카탈로그만 읽는다.
 *
 * <p>{@code UNEXPLAINED_DIFFERENCE}를 뺀 나머지 사유는 전부 판정 가능한 정황이다. 사유가
 * 여러 개 겹칠 수 있으므로 하나로 정하지 않고 집합으로 돌려준다. 최종 판단은 화면을 보는
 * 사용자의 몫이다.
 */
@Component
public class BrokerReconciliationReasonResolver {

    private final BrokerProviderRegistry brokerProviderRegistry;

    public BrokerReconciliationReasonResolver(BrokerProviderRegistry brokerProviderRegistry) {
        this.brokerProviderRegistry = brokerProviderRegistry;
    }

    /**
     * @param item                    스냅샷과 원장을 비교한 종목 한 줄이다.
     * @param provider                이 종목을 보고한 증권사 제공자다.
     * @param baselineInstant         활성 개시 잔고의 기준 시각이다. 없으면 {@code null}이며,
     *                                이때는 어떤 주문도 기준점 때문에 걸러지지 않는다.
     * @param latestStagedRunItems    이 계좌의 가장 최근 {@code STAGED} 주문 이력 실행에 담긴
     *                                항목 전체다. 실행이 없으면 빈 목록이다.
     * @param linkedExternalOrderIds  이미 매매 원장에 반영된 주문 식별자 집합이다.
     * @param hasCoverageGap          이 계좌에 성공한 주문 이력 실행이 없거나, 가장 최근 실행의
     *                                조회 구간이 이번 스냅샷 기준 시각보다 앞서 끝난다는 뜻이다.
     */
    public Set<BrokerReconciliationReasonCode> resolve(
            BrokerHoldingPreviewItem item,
            BrokerProvider provider,
            Instant baselineInstant,
            List<BrokerOrderImportItem> latestStagedRunItems,
            Set<String> linkedExternalOrderIds,
            boolean hasCoverageGap
    ) {
        if (item.comparison() == BrokerHoldingComparison.MATCHED) {
            return Set.of();
        }

        Set<BrokerReconciliationReasonCode> reasons = new LinkedHashSet<>();

        if (!brokerProviderRegistry.isLedgerWritableMarket(provider, item.market())) {
            reasons.add(BrokerReconciliationReasonCode.LEDGER_MARKET_UNSUPPORTED);
        }

        for (BrokerOrderImportItem orderItem : latestStagedRunItems) {
            if (!sameAsset(orderItem, item)) {
                continue;
            }

            if (orderItem.getStagingStatus() == BrokerOrderStagingStatus.PENDING_SETTLEMENT) {
                reasons.add(BrokerReconciliationReasonCode.UNSETTLED_OR_PARTIAL_FILL);
            }

            if (orderItem.getStagingStatus() == BrokerOrderStagingStatus.STAGED
                    && !linkedExternalOrderIds.contains(orderItem.getExternalOrderId())) {
                boolean afterBaseline = baselineInstant == null
                        || orderItem.getFilledAt().isAfter(baselineInstant);
                reasons.add(afterBaseline
                        ? BrokerReconciliationReasonCode.UNAPPROVED_RUN_EXISTS
                        : BrokerReconciliationReasonCode.BASELINE_EXCLUDED_HISTORY);
            }
        }

        if (hasCoverageGap) {
            reasons.add(BrokerReconciliationReasonCode.OUT_OF_PERIOD_HISTORY);
        }

        if (reasons.isEmpty()) {
            reasons.add(BrokerReconciliationReasonCode.UNEXPLAINED_DIFFERENCE);
        }

        return Set.copyOf(reasons);
    }

    private boolean sameAsset(BrokerOrderImportItem orderItem, BrokerHoldingPreviewItem previewItem) {
        return orderItem.getMarket() == previewItem.market()
                && orderItem.getTicker().equalsIgnoreCase(previewItem.ticker());
    }
}
