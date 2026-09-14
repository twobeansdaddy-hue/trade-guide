package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderReconciliationLineValue;
import com.tradeguide.domain.broker.BrokerOrderReconciliationStatus;
import com.tradeguide.domain.broker.BrokerOrderSide;
import com.tradeguide.domain.broker.BrokerOrderStagedOrder;
import com.tradeguide.domain.broker.BrokerOrderStagingStatus;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.service.holding.HoldingCalculator;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 스테이징 대상을 원장에 넣었다고 <b>가정</b>하고 보유 수량을 다시 계산해, 증권사가 보고한
 * 최신 보유 스냅샷과 맞춰 본다.
 *
 * <p>이 대조가 이 단계 전체의 안전망이다. 주문 이력 가져오기에는 조용히 틀리는 실패 방식이
 * 세 가지 있는데, 셋 다 이 한 번의 대조에서 수량 차이로 드러난다.
 *
 * <ul>
 *   <li>조회 가능한 과거 기간이 짧아 이력 앞부분이 잘렸다 → 재구성 수량이 <b>모자란다</b></li>
 *   <li>앱에서 낸 주문이 응답에 빠졌다 → 재구성 수량이 <b>모자란다</b></li>
 *   <li>취소·정정 관련 레코드가 체결분을 이중으로 보고했다 → 재구성 수량이 <b>넘친다</b></li>
 * </ul>
 *
 * <p>차이가 나와도 <b>메우지 않는다.</b> 부족분만큼 합성 매수를 만들면 수량은 맞겠지만
 * 취득 단가를 알 수 없어 평단이 조용히 틀리고, 그 평단이 그대로 전략 판단의 입력이 된다.
 * 차이는 차이대로 보여 주고 판단은 사용자에게 남긴다.
 *
 * <p>계산은 전부 메모리에서 한다. 여기서 만드는 매매 기록은 저장되지 않는 임시 값이며,
 * 포트폴리오를 참조하지 않아 어떤 경로로도 영속화될 수 없다.
 */
@Component
public class BrokerOrderReconciler {

    private final HoldingCalculator holdingCalculator;

    public BrokerOrderReconciler(HoldingCalculator holdingCalculator) {
        this.holdingCalculator = holdingCalculator;
    }

    /**
     * @param ledgerTransactions 포트폴리오의 현재 매매 원장 전체
     * @param stagedOrders       이번 실행이 분류한 항목. 반영 후보만 재생에 들어간다
     * @param snapshotQuantities 최신 증권사 보유 스냅샷의 종목별 수량. 스냅샷이 없으면 {@code null}
     */
    public Result reconcile(
            List<TradeTransaction> ledgerTransactions,
            List<BrokerOrderStagedOrder> stagedOrders,
            Map<AssetKey, BigDecimal> snapshotQuantities
    ) {
        if (ledgerTransactions == null || stagedOrders == null) {
            throw new IllegalArgumentException("대조 입력이 올바르지 않습니다.");
        }
        if (snapshotQuantities == null) {
            return new Result(BrokerOrderReconciliationStatus.NOT_AVAILABLE, List.of());
        }

        List<TradeTransaction> replay = new ArrayList<>(ledgerTransactions);
        stagedOrders.stream()
                .filter(order -> order.stagingStatus() == BrokerOrderStagingStatus.STAGED)
                .map(this::toReplayTransaction)
                .forEach(replay::add);

        Map<AssetKey, BigDecimal> reconstructed;
        try {
            reconstructed = toQuantities(holdingCalculator.calculate(replay));
        } catch (IllegalArgumentException exception) {
            // 보유 수량보다 많은 매도가 나왔다. 이력 앞부분이 잘렸을 때의 전형적인 모습이다.
            // 예외를 삼키면 "대조 결과 이상 없음"으로 보이므로 상태로 올려 보고한다.
            return new Result(BrokerOrderReconciliationStatus.REPLAY_FAILED, List.of());
        }

        List<BrokerOrderReconciliationLineValue> lines = toLines(reconstructed, snapshotQuantities);
        boolean matched = lines.stream().allMatch(BrokerOrderReconciliationLineValue::matches);

        return new Result(
                matched ? BrokerOrderReconciliationStatus.MATCHED : BrokerOrderReconciliationStatus.MISMATCHED,
                lines
        );
    }

    /**
     * 재생용 임시 매매 기록이다. 포트폴리오를 붙이지 않는 것은 실수가 아니라 안전장치다.
     * 관리 상태인 포트폴리오에 매달아 두면 영속성 컨텍스트를 통해 저장될 여지가 생기고,
     * 그 순간 "원장을 바꾸지 않는다"는 이 단계의 전제가 깨진다.
     * {@link HoldingCalculator}는 포트폴리오를 읽지 않으므로 계산에는 아무 영향이 없다.
     */
    private TradeTransaction toReplayTransaction(BrokerOrderStagedOrder order) {
        return new TradeTransaction(
                null,
                order.record().market(),
                order.record().ticker(),
                order.record().side() == BrokerOrderSide.BUY ? TradeType.BUY : TradeType.SELL,
                order.record().filledQuantity(),
                order.record().averageFilledPrice(),
                order.record().commission() == null ? BigDecimal.ZERO : order.record().commission(),
                order.record().filledAt()
        );
    }

    private Map<AssetKey, BigDecimal> toQuantities(List<Holding> holdings) {
        Map<AssetKey, BigDecimal> quantities = new LinkedHashMap<>();
        holdings.forEach(holding ->
                quantities.put(new AssetKey(holding.getMarket(), holding.getTicker()), holding.getQuantity()));
        return quantities;
    }

    /**
     * 양쪽 종목의 합집합으로 줄을 만든다. 한쪽에만 있는 종목이야말로 가장 중요한 차이인데,
     * 교집합만 비교하면 그 종목이 결과에서 통째로 사라진다.
     */
    private List<BrokerOrderReconciliationLineValue> toLines(
            Map<AssetKey, BigDecimal> reconstructed,
            Map<AssetKey, BigDecimal> snapshot
    ) {
        TreeSet<AssetKey> assets = new TreeSet<>(
                Comparator.comparing((AssetKey key) -> key.market().name()).thenComparing(AssetKey::ticker));
        assets.addAll(reconstructed.keySet());
        assets.addAll(snapshot.keySet());

        List<BrokerOrderReconciliationLineValue> lines = new ArrayList<>();
        for (AssetKey asset : assets) {
            lines.add(new BrokerOrderReconciliationLineValue(
                    asset.market(),
                    asset.ticker(),
                    reconstructed.getOrDefault(asset, BigDecimal.ZERO),
                    snapshot.getOrDefault(asset, BigDecimal.ZERO)
            ));
        }
        return lines;
    }

    public record Result(
            BrokerOrderReconciliationStatus status,
            List<BrokerOrderReconciliationLineValue> lines
    ) {
        public Result {
            lines = List.copyOf(lines == null ? List.of() : lines);
        }
    }

    /** 시장과 종목 코드 한 쌍. */
    public record AssetKey(Market market, String ticker) {
    }
}
