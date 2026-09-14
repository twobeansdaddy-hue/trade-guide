package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerOpeningBalanceBatchResult;
import com.tradeguide.domain.broker.BrokerOpeningBalanceSkip;
import com.tradeguide.domain.broker.BrokerOpeningBalanceSkipReason;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.exception.BrokerHoldingImportConflictException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.asset.AssetListingService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 일괄 개시 잔고 반영의 판정과 실행을 검증한다.
 *
 * <p>이 기능은 <b>실제 증권사 주문을 내지 않는다</b>. 검증 대상은 저장된 스냅샷을 근거로
 * 매매 원장에 개시 잔고 행을 만드는 것과, 만들 수 없는 종목을 사유와 함께 제외하는 것뿐이다.
 * 그래서 이 테스트에는 증권사 호출 어댑터도, 자격 증명 복호화도, 외부 종목 검색도 없다.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioBrokerOpeningBalanceBatchWriterTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-06T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime SYNCED_AT = LocalDateTime.of(2026, 9, 4, 9, 30);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;

    @Mock
    private AssetListingService assetListingService;

    @Mock
    private TradeTransactionRepository tradeTransactionRepository;

    @Mock
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

    private PortfolioBrokerOpeningBalanceBatchWriter writer;

    private Portfolio portfolio;
    private BrokerConnection connection;

    @BeforeEach
    void setUp() {
        writer = new PortfolioBrokerOpeningBalanceBatchWriter(
                portfolioRepository,
                portfolioBrokerHoldingSnapshotService,
                new BrokerProviderRegistry(List.of(), List.of(), List.of()),
                assetListingService,
                tradeTransactionRepository,
                portfolioBrokerHoldingImportRepository,
                FIXED_CLOCK
        );

        Member member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);

        connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));
        connection.reconcileVerifiedAccounts(List.of(
                new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1)
        ));
        connection.markConnected("*****1234");
        ReflectionTestUtils.setField(connection, "id", 1L);

        lenient().when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
    }

    @Test
    void approvesEveryOnlyInBrokerHoldingOfTheLatestSnapshotInOneGo() {
        PortfolioBrokerHoldingSnapshot snapshot = snapshot(
                new BrokerHolding(Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3X",
                        new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.US, "PFE", "Pfizer Inc.",
                        new BigDecimal("50"), new BigDecimal("28.50"))
        );
        stubSnapshot(snapshot);
        stubComparison(
                onlyInBroker(snapshot, 0),
                onlyInBroker(snapshot, 1)
        );
        stubEmptyHistoryAndLedger();
        stubWrites();

        BrokerOpeningBalanceBatchResult result = writer.approveAll(10L, 20L, 77L, 10L);

        assertThat(result.snapshotId()).isEqualTo(77L);
        assertThat(result.snapshotSyncedAt()).isEqualTo(SYNCED_AT);
        assertThat(result.skipped()).isEmpty();
        assertThat(result.approved())
                .extracting(PortfolioBrokerHoldingImport::getTicker)
                .containsExactly("SOXL", "PFE");
        assertThat(result.approved())
                .allSatisfy(record -> {
                    assertThat(record.getStatus()).isEqualTo(PortfolioBrokerHoldingImportStatus.ACTIVE);
                    assertThat(record.getSnapshotSyncedAt()).isEqualTo(SYNCED_AT);
                    assertThat(record.getApprovedByMemberId()).isEqualTo(10L);
                    // 일괄 반영은 한 시각으로 승인된다. 그래서 이력 정렬에 동점 기준이 필요하다.
                    assertThat(record.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 9, 6, 10, 0));
                });

        ArgumentCaptor<TradeTransaction> captor = ArgumentCaptor.forClass(TradeTransaction.class);
        verify(tradeTransactionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(transaction -> {
            assertThat(transaction.getTradeType()).isEqualTo(TradeType.BUY);
            assertThat(transaction.getSource()).isEqualTo(TradeTransactionSource.BROKER_OPENING_BALANCE);
            assertThat(transaction.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(transaction.getTradedAt()).isEqualTo(Instant.parse("2026-09-06T10:00:00Z"));
        });

        // 사전 등록 없이 스냅샷이 보고한 표시명 그대로 자산 카탈로그를 확보한다.
        verify(assetListingService).ensureActiveListingFromBrokerSnapshot(
                Market.US, "SOXL", "Direxion Daily Semiconductor Bull 3X");
        verify(assetListingService).ensureActiveListingFromBrokerSnapshot(
                Market.US, "PFE", "Pfizer Inc.");
    }

    /**
     * 사용자가 검토한 스냅샷이 더는 최신이 아니면 막는다. 그 사이 스냅샷이 갱신됐다면
     * 사용자가 화면에서 보지 않은 종목까지 한 번에 들어갈 수 있고, 그것은 명시적 승인이 아니다.
     */
    @Test
    void refusesToApproveAgainstASnapshotTheUserDidNotReview() {
        PortfolioBrokerHoldingSnapshot snapshot = snapshot(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")));
        stubSnapshot(snapshot);

        assertThatThrownBy(() -> writer.approveAll(10L, 20L, 76L, 10L))
                .isInstanceOf(BrokerHoldingImportConflictException.class)
                .hasMessageContaining("최신이 아닙니다");

        verifyNoInteractions(tradeTransactionRepository, assetListingService);
        verify(portfolioBrokerHoldingImportRepository, never())
                .saveAndFlush(any(PortfolioBrokerHoldingImport.class));
    }

    @Test
    void skipsHoldingsThatAreNotOnlyInBroker() {
        PortfolioBrokerHoldingSnapshot snapshot = snapshot(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.US, "AAPL", new BigDecimal("10"), new BigDecimal("150.00"))
        );
        stubSnapshot(snapshot);
        stubComparison(
                onlyInBroker(snapshot, 0),
                comparisonItem(snapshot, 1, BrokerHoldingComparison.QUANTITY_MISMATCH)
        );
        stubEmptyHistoryAndLedger();
        stubWrites();

        BrokerOpeningBalanceBatchResult result = writer.approveAll(10L, 20L, 77L, 10L);

        assertThat(result.approved())
                .extracting(PortfolioBrokerHoldingImport::getTicker)
                .containsExactly("SOXL");
        assertThat(result.skipped())
                .extracting(BrokerOpeningBalanceSkip::ticker, BrokerOpeningBalanceSkip::reason)
                .containsExactly(tuple("AAPL", BrokerOpeningBalanceSkipReason.NOT_ONLY_IN_BROKER));
    }

    /**
     * D1 회귀 테스트. 시장 제외 판정의 기준은 제공자가 <b>조회할 수 있는</b> 시장이 아니라
     * Trade Guide 원장이 <b>표현할 수 있는</b> 시장이다. 토스증권은 KR을 조회할 수 있다고
     * 선언하지만(스냅샷·비교에는 그대로 남는다) 원장에는 US만 쓸 수 있다. 두 집합을 같은
     * 값으로 읽던 시절에는 KR 종목이 통화 개념 없는 원장에 그대로 들어갔다.
     *
     * <p>일괄 경로에서 이것은 실패가 아니라 사유 있는 제외다. KR 한 종목 때문에 같은
     * 스냅샷의 US 종목까지 반영되지 못하면 안 된다.
     */
    @Test
    void skipsKoreanHoldingsAsUnsupportedMarketWhileStillApprovingUsHoldings() {
        BrokerProvider provider = connection.getProvider();
        assertThat(provider.getSupportedMarkets()).contains(Market.KR);
        assertThat(provider.getLedgerWritableMarkets()).containsExactly(Market.US);

        PortfolioBrokerHoldingSnapshot snapshot = snapshot(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")),
                new BrokerHolding(Market.KR, "005930", "삼성전자",
                        new BigDecimal("10"), new BigDecimal("70000")));
        stubSnapshot(snapshot);
        stubComparison(onlyInBroker(snapshot, 0), onlyInBroker(snapshot, 1));
        stubEmptyHistoryAndLedger();
        stubWrites();

        BrokerOpeningBalanceBatchResult result = writer.approveAll(10L, 20L, 77L, 10L);

        assertThat(result.approved()).hasSize(1);
        assertThat(result.approved().getFirst().getTicker()).isEqualTo("SOXL");
        assertThat(result.skipped()).singleElement().satisfies(skip -> {
            assertThat(skip.ticker()).isEqualTo("005930");
            assertThat(skip.market()).isEqualTo(Market.KR);
            assertThat(skip.reason()).isEqualTo(BrokerOpeningBalanceSkipReason.UNSUPPORTED_MARKET);
        });

        // 반영할 수 없는 시장은 자산 카탈로그 등록도 시도하지 않는다.
        verify(assetListingService).ensureActiveListingFromBrokerSnapshot(Market.US, "SOXL", "SOXL");
        verify(assetListingService, never())
                .ensureActiveListingFromBrokerSnapshot(eq(Market.KR), any(), any());

        ArgumentCaptor<TradeTransaction> captor = ArgumentCaptor.forClass(TradeTransaction.class);
        verify(tradeTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getMarket()).isEqualTo(Market.US);
    }

    /** KR만 담긴 스냅샷은 원장에 아무것도 쓰지 않고 사유만 돌려준다. */
    @Test
    void writesNothingWhenEveryHoldingBelongsToANonLedgerWritableMarket() {
        PortfolioBrokerHoldingSnapshot snapshot = snapshot(
                new BrokerHolding(Market.KR, "005930", new BigDecimal("10"), new BigDecimal("70000")));
        stubSnapshot(snapshot);
        stubComparison(onlyInBroker(snapshot, 0));
        stubEmptyHistoryAndLedger();

        BrokerOpeningBalanceBatchResult result = writer.approveAll(10L, 20L, 77L, 10L);

        assertThat(result.approved()).isEmpty();
        assertThat(result.skipped()).singleElement()
                .satisfies(skip -> assertThat(skip.reason())
                        .isEqualTo(BrokerOpeningBalanceSkipReason.UNSUPPORTED_MARKET));
        verify(tradeTransactionRepository, never()).save(any(TradeTransaction.class));
        verifyNoInteractions(assetListingService);
    }

    @Test
    void skipsHoldingsThatAlreadyHaveAnActiveOpeningBalanceApproval() {
        PortfolioBrokerHoldingSnapshot snapshot = snapshot(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")));
        stubSnapshot(snapshot);
        stubComparison(onlyInBroker(snapshot, 0));
        stubHistory(existingImport(Market.US, "SOXL", PortfolioBrokerHoldingImportStatus.ACTIVE));
        stubLedger();

        BrokerOpeningBalanceBatchResult result = writer.approveAll(10L, 20L, 77L, 10L);

        assertThat(result.approved()).isEmpty();
        assertThat(result.skipped()).singleElement()
                .satisfies(skip -> assertThat(skip.reason())
                        .isEqualTo(BrokerOpeningBalanceSkipReason.ALREADY_APPROVED));
        verify(tradeTransactionRepository, never()).save(any(TradeTransaction.class));
        verifyNoInteractions(assetListingService);
    }

    /** 사용자가 스스로 취소한 개시 잔고를 일괄 반영이 조용히 되살리지 않는다. */
    @Test
    void skipsHoldingsWhoseOpeningBalanceTheUserAlreadyRevoked() {
        PortfolioBrokerHoldingSnapshot snapshot = snapshot(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")));
        stubSnapshot(snapshot);
        stubComparison(onlyInBroker(snapshot, 0));
        stubHistory(existingImport(Market.US, "SOXL", PortfolioBrokerHoldingImportStatus.REVOKED));
        stubLedger();

        BrokerOpeningBalanceBatchResult result = writer.approveAll(10L, 20L, 77L, 10L);

        assertThat(result.approved()).isEmpty();
        assertThat(result.skipped()).singleElement()
                .satisfies(skip -> assertThat(skip.reason())
                        .isEqualTo(BrokerOpeningBalanceSkipReason.PREVIOUSLY_REVOKED));
        verify(tradeTransactionRepository, never()).save(any(TradeTransaction.class));
        verifyNoInteractions(assetListingService);
    }

    /**
     * 전량 매도로 보유 수량이 0이면 비교에서는 {@code ONLY_IN_BROKER}로 보인다. 그렇다고
     * 개시 잔고를 얹으면 이미 있는 매수·매도 이력과 충돌한다.
     */
    @Test
    void skipsHoldingsThatAlreadyHaveLedgerHistory() {
        PortfolioBrokerHoldingSnapshot snapshot = snapshot(
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")));
        stubSnapshot(snapshot);
        stubComparison(onlyInBroker(snapshot, 0));
        stubHistory();
        stubLedger(new TradeTransaction(
                portfolio, Market.US, "SOXL", TradeType.SELL,
                new BigDecimal("10"), new BigDecimal("21.00"), BigDecimal.ZERO,
                Instant.parse("2026-08-01T00:00:00Z")
        ));

        BrokerOpeningBalanceBatchResult result = writer.approveAll(10L, 20L, 77L, 10L);

        assertThat(result.approved()).isEmpty();
        assertThat(result.skipped()).singleElement()
                .satisfies(skip -> assertThat(skip.reason())
                        .isEqualTo(BrokerOpeningBalanceSkipReason.LEDGER_CONFLICT));
        verify(tradeTransactionRepository, never()).save(any(TradeTransaction.class));
    }

    /**
     * 종목 하나가 상장 폐지됐다는 이유로 나머지 전부를 못 넣게 하면 안 된다. 반영할 수 없다는
     * 판정은 실패가 아니라 사유가 붙은 제외다.
     */
    @Test
    void skipsInactiveListingsWithoutBlockingTheRestOfTheBatch() {
        PortfolioBrokerHoldingSnapshot snapshot = snapshot(
                new BrokerHolding(Market.US, "DEAD", new BigDecimal("5"), new BigDecimal("1.00")),
                new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00"))
        );
        stubSnapshot(snapshot);
        stubComparison(onlyInBroker(snapshot, 0), onlyInBroker(snapshot, 1));
        stubEmptyHistoryAndLedger();
        stubWrites();
        when(assetListingService.ensureActiveListingFromBrokerSnapshot(Market.US, "DEAD", "DEAD"))
                .thenThrow(new IllegalArgumentException("비활성 상장 종목은 거래를 등록할 수 없습니다: US / DEAD"));

        BrokerOpeningBalanceBatchResult result = writer.approveAll(10L, 20L, 77L, 10L);

        assertThat(result.approved())
                .extracting(PortfolioBrokerHoldingImport::getTicker)
                .containsExactly("SOXL");
        assertThat(result.skipped()).singleElement()
                .satisfies(skip -> {
                    assertThat(skip.ticker()).isEqualTo("DEAD");
                    assertThat(skip.reason()).isEqualTo(BrokerOpeningBalanceSkipReason.INACTIVE_LISTING);
                });
    }

    @Test
    void reportsAnEmptyResultWhenTheSnapshotHasNothingToApprove() {
        PortfolioBrokerHoldingSnapshot snapshot = snapshot();
        stubSnapshot(snapshot);
        stubComparison();

        BrokerOpeningBalanceBatchResult result = writer.approveAll(10L, 20L, 77L, 10L);

        assertThat(result.approved()).isEmpty();
        assertThat(result.skipped()).isEmpty();
        assertThat(result.hasApproved()).isFalse();
        verify(tradeTransactionRepository, never()).save(any(TradeTransaction.class));
        verifyNoInteractions(assetListingService);
    }

    @Test
    void rejectsApprovalForAPortfolioTheMemberDoesNotOwn() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.approveAll(99L, 20L, 77L, 99L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(portfolioBrokerHoldingSnapshotService, tradeTransactionRepository);
    }

    private void stubSnapshot(PortfolioBrokerHoldingSnapshot snapshot) {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshot(anyLong(), anyLong()))
                .thenReturn(snapshot);
    }

    private void stubComparison(BrokerHoldingPreviewItem... items) {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(new BrokerHoldingPreview(
                        BrokerProvider.TOSS_SECURITIES,
                        1L,
                        "*****1234",
                        SYNCED_AT,
                        List.of(items),
                        0
                ));
    }

    private void stubEmptyHistoryAndLedger() {
        stubHistory();
        stubLedger();
    }

    private void stubHistory(PortfolioBrokerHoldingImport... records) {
        when(portfolioBrokerHoldingImportRepository
                .findAllByPortfolio_IdAndTickerIn(anyLong(), anyCollection()))
                .thenReturn(List.of(records));
    }

    private void stubLedger(TradeTransaction... transactions) {
        when(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(20L))
                .thenReturn(List.of(transactions));
    }

    private void stubWrites() {
        List<Long> nextTransactionId = new ArrayList<>(List.of(900L));
        when(tradeTransactionRepository.save(any(TradeTransaction.class)))
                .thenAnswer(invocation -> {
                    TradeTransaction transaction = invocation.getArgument(0);
                    long id = nextTransactionId.getFirst();
                    nextTransactionId.set(0, id + 1);
                    ReflectionTestUtils.setField(transaction, "id", id);
                    return transaction;
                });
        when(portfolioBrokerHoldingImportRepository.saveAndFlush(any(PortfolioBrokerHoldingImport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PortfolioBrokerHoldingSnapshot snapshot(BrokerHolding... holdings) {
        PortfolioBrokerHoldingSnapshot snapshot = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                connection.getAccounts().getFirst(),
                SYNCED_AT,
                0,
                List.of(holdings)
        );
        ReflectionTestUtils.setField(snapshot, "id", 77L);

        List<PortfolioBrokerHoldingSnapshotItem> items = snapshot.getItems();
        for (int index = 0; index < items.size(); index++) {
            ReflectionTestUtils.setField(items.get(index), "id", 55L + index);
        }
        return snapshot;
    }

    private BrokerHoldingPreviewItem onlyInBroker(PortfolioBrokerHoldingSnapshot snapshot, int index) {
        return comparisonItem(snapshot, index, BrokerHoldingComparison.ONLY_IN_BROKER);
    }

    private BrokerHoldingPreviewItem comparisonItem(
            PortfolioBrokerHoldingSnapshot snapshot,
            int index,
            BrokerHoldingComparison comparison
    ) {
        PortfolioBrokerHoldingSnapshotItem item = snapshot.getItems().get(index);
        return new BrokerHoldingPreviewItem(
                item.getMarket(),
                item.getTicker(),
                item.getDisplayName(),
                item.getQuantity(),
                item.getAveragePurchasePrice(),
                comparison == BrokerHoldingComparison.ONLY_IN_BROKER ? null : new BigDecimal("1"),
                comparison,
                item.getId()
        );
    }

    private PortfolioBrokerHoldingImport existingImport(
            Market market,
            String ticker,
            PortfolioBrokerHoldingImportStatus status
    ) {
        PortfolioBrokerHoldingSnapshot previousSnapshot = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                connection.getAccounts().getFirst(),
                SYNCED_AT.minusDays(3),
                0,
                List.of(new BrokerHolding(market, ticker, new BigDecimal("30"), new BigDecimal("20.00")))
        );
        PortfolioBrokerHoldingImport importRecord = new PortfolioBrokerHoldingImport(
                portfolio,
                previousSnapshot.getItems().getFirst(),
                previousSnapshot.getSyncedAt(),
                800L,
                10L,
                SYNCED_AT.minusDays(3)
        );
        if (status == PortfolioBrokerHoldingImportStatus.REVOKED) {
            importRecord.revoke();
        }
        return importRecord;
    }
}
