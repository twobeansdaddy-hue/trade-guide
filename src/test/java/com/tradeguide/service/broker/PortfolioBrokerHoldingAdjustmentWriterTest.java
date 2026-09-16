package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.holding.Holding;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.exception.BrokerHoldingAdjustmentConflictException;
import com.tradeguide.exception.BrokerHoldingAdjustmentUnprocessableException;
import com.tradeguide.exception.BrokerHoldingSnapshotItemNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingAdjustmentRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.holding.HoldingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioBrokerHoldingAdjustmentWriterTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-11T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;

    @Mock
    private HoldingService holdingService;

    @Mock
    private TradeTransactionRepository tradeTransactionRepository;

    @Mock
    private PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository;

    private PortfolioBrokerHoldingAdjustmentWriter writer;

    private Portfolio portfolio;
    private PortfolioBrokerHoldingSnapshotItem mismatchItem;
    private PortfolioBrokerHoldingSnapshot latestSnapshot;

    @BeforeEach
    void setUp() {
        writer = new PortfolioBrokerHoldingAdjustmentWriter(
                portfolioRepository,
                portfolioBrokerHoldingSnapshotService,
                holdingService,
                tradeTransactionRepository,
                portfolioBrokerHoldingAdjustmentRepository,
                FIXED_CLOCK
        );

        Member member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);

        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));
        BrokerAccount account = new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1);
        connection.reconcileVerifiedAccounts(List.of(account));
        connection.markConnected("*****1234");
        ReflectionTestUtils.setField(connection, "id", 1L);

        // 증권사 15주, 평단가 120.00. Trade Guide 원장은 10주, 평단가 100.00으로 가정한다.
        latestSnapshot = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                connection.getAccounts().getFirst(),
                LocalDateTime.of(2026, 9, 11, 9, 30),
                0,
                List.of(new BrokerHolding(Market.US, "SOXL", new BigDecimal("15"), new BigDecimal("120.00")))
        );
        mismatchItem = latestSnapshot.getItems().getFirst();
        ReflectionTestUtils.setField(mismatchItem, "id", 55L);

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L)).thenReturn(latestSnapshot);
    }

    @Test
    void createsAdjustmentTransactionWhoseResultingAverageMatchesBrokerSnapshot() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.QUANTITY_MISMATCH, new BigDecimal("10")));
        when(holdingService.getHoldings(10L, 20L))
                .thenReturn(List.of(new Holding(Market.US, "SOXL", new BigDecimal("10"), new BigDecimal("100.00"))));
        when(tradeTransactionRepository.save(any(TradeTransaction.class)))
                .thenAnswer(invocation -> {
                    TradeTransaction transaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(transaction, "id", 900L);
                    return transaction;
                });
        when(portfolioBrokerHoldingAdjustmentRepository.saveAndFlush(any(PortfolioBrokerHoldingAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioBrokerHoldingAdjustment result = writer.createAdjustment(10L, 20L, 55L, 10L);

        // delta = 15 - 10 = 5, unitPrice = (15*120 - 10*100) / 5 = (1800-1000)/5 = 160.00
        assertThat(result.getDeltaQuantity()).isEqualByComparingTo("5");
        assertThat(result.getUnitPrice()).isEqualByComparingTo("160.00");
        assertThat(result.getBrokerQuantity()).isEqualByComparingTo("15");
        assertThat(result.getBrokerAveragePurchasePrice()).isEqualByComparingTo("120.00");
        assertThat(result.getLedgerQuantityBefore()).isEqualByComparingTo("10");
        assertThat(result.getLedgerAveragePurchasePriceBefore()).isEqualByComparingTo("100.00");
        assertThat(result.getTradeTransactionId()).isEqualTo(900L);
        assertThat(result.getApprovedByMemberId()).isEqualTo(10L);

        // 반영 후 원장 가중평균: (10*100 + 5*160) / 15 = 1800/15 = 120.00 = 증권사 스냅샷 평균
        BigDecimal postQuantity = result.getLedgerQuantityBefore().add(result.getDeltaQuantity());
        BigDecimal postTotalCost = result.getLedgerQuantityBefore().multiply(result.getLedgerAveragePurchasePriceBefore())
                .add(result.getDeltaQuantity().multiply(result.getUnitPrice()));
        BigDecimal postAverage = postTotalCost.divide(postQuantity, 4, java.math.RoundingMode.HALF_UP);
        assertThat(postAverage).isEqualByComparingTo(result.getBrokerAveragePurchasePrice());

        ArgumentCaptor<TradeTransaction> captor = ArgumentCaptor.forClass(TradeTransaction.class);
        org.mockito.Mockito.verify(tradeTransactionRepository).save(captor.capture());
        TradeTransaction saved = captor.getValue();
        assertThat(saved.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(saved.getSource()).isEqualTo(TradeTransactionSource.BROKER_HOLDING_ADJUSTMENT);
        assertThat(saved.getQuantity()).isEqualByComparingTo("5");
        assertThat(saved.getExecutedPrice()).isEqualByComparingTo("160.00");
        assertThat(saved.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void rejectsWhenSnapshotItemDoesNotBelongToLatestSnapshot() {
        assertThatThrownBy(() -> writer.createAdjustment(10L, 20L, 999L, 10L))
                .isInstanceOf(BrokerHoldingSnapshotItemNotFoundException.class);

        verifyNoInteractions(tradeTransactionRepository, portfolioBrokerHoldingAdjustmentRepository);
    }

    @Test
    void rejectsWhenComparisonStatusIsNotQuantityMismatch() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.MATCHED, new BigDecimal("15")));

        assertThatThrownBy(() -> writer.createAdjustment(10L, 20L, 55L, 10L))
                .isInstanceOf(BrokerHoldingAdjustmentConflictException.class);

        verifyNoInteractions(tradeTransactionRepository, portfolioBrokerHoldingAdjustmentRepository);
    }

    @Test
    void rejectsWhenComparisonStatusIsOnlyInBroker() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.ONLY_IN_BROKER, null));

        assertThatThrownBy(() -> writer.createAdjustment(10L, 20L, 55L, 10L))
                .isInstanceOf(BrokerHoldingAdjustmentConflictException.class);

        verifyNoInteractions(tradeTransactionRepository, portfolioBrokerHoldingAdjustmentRepository);
    }

    @Test
    void rejectsWhenBrokerQuantityEqualsLedgerQuantity() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.QUANTITY_MISMATCH, new BigDecimal("15")));
        when(holdingService.getHoldings(10L, 20L))
                .thenReturn(List.of(new Holding(Market.US, "SOXL", new BigDecimal("15"), new BigDecimal("100.00"))));

        assertThatThrownBy(() -> writer.createAdjustment(10L, 20L, 55L, 10L))
                .isInstanceOf(BrokerHoldingAdjustmentUnprocessableException.class);

        verifyNoInteractions(tradeTransactionRepository, portfolioBrokerHoldingAdjustmentRepository);
    }

    /**
     * 원장 수량이 증권사보다 많은 경우(예: 실제로는 매도됐지만 Trade Guide에는 기록되지
     * 않은 경우)는 조정 매도를 생성한다. 실제 체결가를 알 수 없으므로 단가는 원장의
     * 조정 전 평균 매입가와 동일하게 두어 실현손익을 0으로 만들고, 원장 평균 매입가는
     * 그대로 유지되어야 한다.
     */
    @Test
    void createsSellAdjustmentWhenLedgerQuantityExceedsBroker() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.QUANTITY_MISMATCH, new BigDecimal("30")));
        when(holdingService.getHoldings(10L, 20L))
                .thenReturn(List.of(new Holding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("100.00"))));
        when(tradeTransactionRepository.save(any(TradeTransaction.class)))
                .thenAnswer(invocation -> {
                    TradeTransaction transaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(transaction, "id", 901L);
                    return transaction;
                });
        when(portfolioBrokerHoldingAdjustmentRepository.saveAndFlush(any(PortfolioBrokerHoldingAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // 증권사 15주, 원장 30주 -> 초과분 15주를 조정 매도로 반영한다.
        PortfolioBrokerHoldingAdjustment result = writer.createAdjustment(10L, 20L, 55L, 10L);

        assertThat(result.getDeltaQuantity()).isEqualByComparingTo("15");
        assertThat(result.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(result.getBrokerQuantity()).isEqualByComparingTo("15");
        assertThat(result.getLedgerQuantityBefore()).isEqualByComparingTo("30");
        assertThat(result.getLedgerAveragePurchasePriceBefore()).isEqualByComparingTo("100.00");
        assertThat(result.getTradeTransactionId()).isEqualTo(901L);

        ArgumentCaptor<TradeTransaction> captor = ArgumentCaptor.forClass(TradeTransaction.class);
        org.mockito.Mockito.verify(tradeTransactionRepository).save(captor.capture());
        TradeTransaction saved = captor.getValue();
        assertThat(saved.getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(saved.getSource()).isEqualTo(TradeTransactionSource.BROKER_HOLDING_ADJUSTMENT);
        assertThat(saved.getQuantity()).isEqualByComparingTo("15");
        // 단가를 원장 평균 매입가와 동일하게 둬 실현손익이 0이 되도록 한다.
        assertThat(saved.getExecutedPrice()).isEqualByComparingTo(result.getLedgerAveragePurchasePriceBefore());
    }

    /**
     * 증권사 총매입원가가 원장 총매입원가보다 낮으면(증권사 평단가가 원장보다 훨씬 낮은 경우)
     * 역산한 단가가 0 이하로 나올 수 있다. 이런 계산 불가 상태는 422로 거부해야 한다.
     */
    @Test
    void rejectsWhenComputedUnitPriceIsZeroOrNegative() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.QUANTITY_MISMATCH, new BigDecimal("10")));
        // 증권사: 15주 * 10.00 = 150 총원가. 원장: 10주 * 100.00 = 1000 총원가.
        // unitPrice = (150 - 1000) / 5 = -170 (0 이하)
        PortfolioBrokerHoldingSnapshot lowAvgSnapshot = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                latestSnapshot.getBrokerConnection(),
                latestSnapshot.getBrokerAccount(),
                LocalDateTime.of(2026, 9, 11, 9, 30),
                0,
                List.of(new BrokerHolding(Market.US, "SOXL", new BigDecimal("15"), new BigDecimal("10.00")))
        );
        PortfolioBrokerHoldingSnapshotItem lowAvgItem = lowAvgSnapshot.getItems().getFirst();
        ReflectionTestUtils.setField(lowAvgItem, "id", 55L);
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L)).thenReturn(lowAvgSnapshot);
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(new BrokerHoldingPreview(
                        BrokerProvider.TOSS_SECURITIES,
                        1L,
                        "*****1234",
                        LocalDateTime.of(2026, 9, 11, 9, 30),
                        List.of(new BrokerHoldingPreviewItem(
                                Market.US, "SOXL", "SOXL",
                                new BigDecimal("15"), new BigDecimal("10.00"),
                                new BigDecimal("10"),
                                BrokerHoldingComparison.QUANTITY_MISMATCH,
                                55L
                        )),
                        0
                ));
        when(holdingService.getHoldings(10L, 20L))
                .thenReturn(List.of(new Holding(Market.US, "SOXL", new BigDecimal("10"), new BigDecimal("100.00"))));

        assertThatThrownBy(() -> writer.createAdjustment(10L, 20L, 55L, 10L))
                .isInstanceOf(BrokerHoldingAdjustmentUnprocessableException.class);

        verifyNoInteractions(tradeTransactionRepository, portfolioBrokerHoldingAdjustmentRepository);
    }

    private BrokerHoldingPreview comparisonWith(BrokerHoldingComparison comparisonStatus, BigDecimal tradeGuideQuantity) {
        return new BrokerHoldingPreview(
                BrokerProvider.TOSS_SECURITIES,
                1L,
                "*****1234",
                LocalDateTime.of(2026, 9, 11, 9, 30),
                List.of(new BrokerHoldingPreviewItem(
                        Market.US,
                        "SOXL",
                        "SOXL",
                        new BigDecimal("15"),
                        new BigDecimal("120.00"),
                        tradeGuideQuantity,
                        comparisonStatus,
                        55L
                )),
                0
        );
    }
}
