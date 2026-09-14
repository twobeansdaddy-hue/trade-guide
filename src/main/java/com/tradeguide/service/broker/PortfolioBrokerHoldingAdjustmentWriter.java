package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.exception.BrokerHoldingAdjustmentConflictException;
import com.tradeguide.exception.BrokerHoldingAdjustmentUnprocessableException;
import com.tradeguide.exception.BrokerHoldingSnapshotItemNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingAdjustmentRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 증권사 잔고 조정 승인 한 건을 실제로 생성하는 트랜잭션 경계다.
 *
 * <p>대상은 이미 Trade Guide 원장에 있는 종목 중 스냅샷 비교가
 * {@link BrokerHoldingComparison#QUANTITY_MISMATCH}이고 증권사 수량이 원장 수량보다
 * 많은 경우뿐이다. 신규 종목({@code ONLY_IN_BROKER})은 개시 잔고 승인
 * ({@link PortfolioBrokerHoldingImportWriter})의 대상이며 이 클래스는 다루지 않는다.
 *
 * <p>이 클래스는 어떤 경우에도 실제 증권사 주문을 내지 않는다. 조정 매수 한 건만
 * 명시적 사용자 승인으로 생성하며, 수량은 증권사·원장 수량의 차이(delta), 단가는
 * 반영 후 내부 가중평균이 증권사 스냅샷 평균과 일치하도록 역산한 값이다.
 *
 * <p>{@link PortfolioBrokerHoldingAdjustmentService}와 별도 빈으로 분리한 이유는
 * {@link PortfolioBrokerHoldingImportWriter}와 같다. 동시 요청으로 인한 유니크 제약
 * 위반을 안전하게 복구하려면 이 트랜잭션 전체가 롤백된 뒤 호출자가 새 트랜잭션에서
 * 기존 승인 결과를 다시 조회해야 하고, 같은 빈 안에서 자기 자신을 호출하면 프록시를
 * 거치지 않아 트랜잭션 경계가 성립하지 않기 때문이다.
 */
@Component
public class PortfolioBrokerHoldingAdjustmentWriter {

    /**
     * {@code TradeTransaction.executedPrice}, {@code average_purchase_price} 컬럼과 같은
     * 소수점 자리수다. 조정 단가를 이 정밀도로 반올림해 저장한다.
     */
    private static final int PRICE_SCALE = 4;

    private final PortfolioRepository portfolioRepository;
    private final PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;
    private final HoldingService holdingService;
    private final TradeTransactionRepository tradeTransactionRepository;
    private final PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository;
    private final Clock clock;

    public PortfolioBrokerHoldingAdjustmentWriter(
            PortfolioRepository portfolioRepository,
            PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService,
            HoldingService holdingService,
            TradeTransactionRepository tradeTransactionRepository,
            PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository,
            Clock clock
    ) {
        this.portfolioRepository = portfolioRepository;
        this.portfolioBrokerHoldingSnapshotService = portfolioBrokerHoldingSnapshotService;
        this.holdingService = holdingService;
        this.tradeTransactionRepository = tradeTransactionRepository;
        this.portfolioBrokerHoldingAdjustmentRepository = portfolioBrokerHoldingAdjustmentRepository;
        this.clock = clock;
    }

    @Transactional
    public PortfolioBrokerHoldingAdjustment createAdjustment(
            Long memberId,
            Long portfolioId,
            Long snapshotItemId,
            Long approvingMemberId
    ) {
        Portfolio portfolio = portfolioRepository.findByMember_IdAndId(memberId, portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException("포트폴리오를 찾을 수 없습니다."));

        PortfolioBrokerHoldingSnapshot latestSnapshot =
                portfolioBrokerHoldingSnapshotService.getLatestSnapshot(memberId, portfolioId);

        PortfolioBrokerHoldingSnapshotItem item = latestSnapshot.getItems().stream()
                .filter(candidate -> candidate.getId().equals(snapshotItemId))
                .findFirst()
                .orElseThrow(() -> new BrokerHoldingSnapshotItemNotFoundException(
                        "최신 스냅샷에서 해당 증권사 보유 종목을 찾을 수 없습니다."
                ));

        BrokerHoldingPreview comparison =
                portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(memberId, portfolioId);
        BrokerHoldingPreviewItem previewItem = comparison.items().stream()
                .filter(candidate -> snapshotItemId.equals(candidate.snapshotItemId()))
                .findFirst()
                .orElseThrow(() -> new BrokerHoldingSnapshotItemNotFoundException(
                        "최신 스냅샷에서 해당 증권사 보유 종목을 찾을 수 없습니다."
                ));

        if (previewItem.comparison() != BrokerHoldingComparison.QUANTITY_MISMATCH) {
            throw new BrokerHoldingAdjustmentConflictException(
                    "수량이 불일치하는 종목만 잔고 조정으로 반영할 수 있습니다: " + previewItem.comparison()
            );
        }

        BigDecimal brokerQuantity = item.getQuantity();
        BigDecimal brokerAveragePurchasePrice = item.getAveragePurchasePrice();

        Holding ledgerHolding = holdingService.getHoldings(memberId, portfolioId).stream()
                .filter(holding -> holding.getMarket() == item.getMarket()
                        && holding.getTicker().equalsIgnoreCase(item.getTicker()))
                .findFirst()
                .orElse(null);

        BigDecimal ledgerQuantityBefore = ledgerHolding == null ? BigDecimal.ZERO : ledgerHolding.getQuantity();
        BigDecimal ledgerAveragePurchasePriceBefore =
                ledgerHolding == null ? BigDecimal.ZERO : ledgerHolding.getAveragePurchasePrice();

        // 이 API는 증권사 수량이 원장 수량보다 많은 경우만 지원한다. 원장이 더 많거나
        // 같으면(부족분 반영, 초과분 매도 반영 등) 매수만으로 해소할 수 없으므로 422로 거부한다.
        if (brokerQuantity.compareTo(ledgerQuantityBefore) <= 0) {
            throw new BrokerHoldingAdjustmentUnprocessableException(
                    "증권사 수량이 Trade Guide 보유 수량보다 많은 경우만 잔고 조정을 반영할 수 있습니다."
            );
        }

        BigDecimal deltaQuantity = brokerQuantity.subtract(ledgerQuantityBefore);

        // 반영 후 내부 가중평균이 증권사 스냅샷 평균과 일치하도록 단가를 역산한다.
        // brokerAvg * brokerQty = ledgerAvg * ledgerQty + unitPrice * delta
        BigDecimal brokerTotalCost = brokerQuantity.multiply(brokerAveragePurchasePrice);
        BigDecimal ledgerTotalCost = ledgerQuantityBefore.multiply(ledgerAveragePurchasePriceBefore);
        BigDecimal unitPrice = brokerTotalCost.subtract(ledgerTotalCost)
                .divide(deltaQuantity, PRICE_SCALE, RoundingMode.HALF_UP);

        if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BrokerHoldingAdjustmentUnprocessableException(
                    "산출된 조정 단가가 0 이하여서 잔고 조정을 반영할 수 없습니다."
            );
        }

        Instant approvedAtInstant = Instant.now(clock);
        LocalDateTime approvedAt = LocalDateTime.ofInstant(approvedAtInstant, clock.getZone());

        TradeTransaction transaction = tradeTransactionRepository.save(new TradeTransaction(
                portfolio,
                item.getMarket(),
                item.getTicker(),
                TradeType.BUY,
                deltaQuantity,
                unitPrice,
                BigDecimal.ZERO,
                approvedAtInstant,
                TradeTransactionSource.BROKER_HOLDING_ADJUSTMENT
        ));

        PortfolioBrokerHoldingAdjustment adjustment = new PortfolioBrokerHoldingAdjustment(
                portfolio,
                item,
                deltaQuantity,
                unitPrice,
                brokerQuantity,
                brokerAveragePurchasePrice,
                ledgerQuantityBefore,
                ledgerAveragePurchasePriceBefore,
                latestSnapshot.getSyncedAt(),
                transaction.getId(),
                approvingMemberId,
                approvedAt
        );

        return portfolioBrokerHoldingAdjustmentRepository.saveAndFlush(adjustment);
    }
}
