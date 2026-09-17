package com.tradeguide.service.broker;

import com.tradeguide.domain.asset.AssetListing;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.exception.BrokerHoldingImportUnprocessableException;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerHoldingImportConflictException;
import com.tradeguide.exception.BrokerHoldingSnapshotItemNotFoundException;
import com.tradeguide.repository.asset.AssetListingRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.asset.AssetListingService;
import com.tradeguide.service.asset.AssetSearchCache;
import com.tradeguide.service.asset.AssetSearchProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioBrokerHoldingImportWriterTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-06T10:00:00Z"), ZoneOffset.UTC);

    /**
     * 원장 반영 시장 관문은 제공자 카탈로그 선언만 읽으므로 어댑터 없이 실제 구현을 쓴다.
     * 목으로 대체하면 이 테스트가 카탈로그 선언 변화에 반응하지 못한다.
     */
    private static final BrokerProviderRegistry REGISTRY =
            new BrokerProviderRegistry(List.of(), List.of(), List.of(), List.of());

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

    private PortfolioBrokerHoldingImportWriter writer;

    private Portfolio portfolio;
    private PortfolioBrokerHoldingSnapshotItem onlyInBrokerItem;
    private PortfolioBrokerHoldingSnapshot latestSnapshot;

    @BeforeEach
    void setUp() {
        writer = new PortfolioBrokerHoldingImportWriter(
                portfolioRepository,
                portfolioBrokerHoldingSnapshotService,
                REGISTRY,
                assetListingService,
                tradeTransactionRepository,
                portfolioBrokerHoldingImportRepository,
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

        latestSnapshot = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                connection.getAccounts().getFirst(),
                LocalDateTime.of(2026, 9, 4, 9, 30),
                0,
                List.of(new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00")))
        );
        onlyInBrokerItem = latestSnapshot.getItems().getFirst();
        ReflectionTestUtils.setField(onlyInBrokerItem, "id", 55L);

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L)).thenReturn(latestSnapshot);
    }

    @Test
    void createsOpeningBalanceTransactionAndAuditRecordForOnlyInBrokerItem() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.ONLY_IN_BROKER));
        when(tradeTransactionRepository.save(any(TradeTransaction.class)))
                .thenAnswer(invocation -> {
                    TradeTransaction transaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(transaction, "id", 900L);
                    return transaction;
                });
        when(portfolioBrokerHoldingImportRepository.saveAndFlush(any(PortfolioBrokerHoldingImport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioBrokerHoldingImport result = writer.createOpeningBalanceImport(10L, 20L, 55L, 10L);

        assertThat(result.getTradeTransactionId()).isEqualTo(900L);
        assertThat(result.getQuantity()).isEqualByComparingTo("30");
        assertThat(result.getAveragePurchasePrice()).isEqualByComparingTo("20.00");
        assertThat(result.getSnapshotSyncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
        assertThat(result.getApprovedByMemberId()).isEqualTo(10L);
        assertThat(result.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 9, 6, 10, 0));

        ArgumentCaptor<TradeTransaction> captor = ArgumentCaptor.forClass(TradeTransaction.class);
        verify(tradeTransactionRepository).save(captor.capture());
        TradeTransaction saved = captor.getValue();
        assertThat(saved.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(saved.getSource()).isEqualTo(TradeTransactionSource.BROKER_OPENING_BALANCE);
        assertThat(saved.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getTradedAt()).isEqualTo(Instant.parse("2026-09-06T10:00:00Z"));

        verify(assetListingService).ensureActiveListingFromBrokerSnapshot(Market.US, "SOXL", "SOXL");
    }

    @Test
    void rejectsWhenSnapshotItemDoesNotBelongToLatestSnapshot() {
        assertThatThrownBy(() -> writer.createOpeningBalanceImport(10L, 20L, 999L, 10L))
                .isInstanceOf(BrokerHoldingSnapshotItemNotFoundException.class);

        verifyNoInteractions(tradeTransactionRepository, portfolioBrokerHoldingImportRepository);
    }

    @Test
    void rejectsWhenComparisonStatusIsMatched() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.MATCHED));

        assertThatThrownBy(() -> writer.createOpeningBalanceImport(10L, 20L, 55L, 10L))
                .isInstanceOf(BrokerHoldingImportConflictException.class);

        verifyNoInteractions(tradeTransactionRepository, portfolioBrokerHoldingImportRepository);
    }

    @Test
    void rejectsWhenComparisonStatusIsQuantityMismatch() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.QUANTITY_MISMATCH));

        assertThatThrownBy(() -> writer.createOpeningBalanceImport(10L, 20L, 55L, 10L))
                .isInstanceOf(BrokerHoldingImportConflictException.class);
    }

    /**
     * D1 회귀 테스트. 증권사가 KR 보유 종목을 정상적으로 돌려주고 스냅샷 비교도
     * {@code ONLY_IN_BROKER}이지만, 매매 원장에는 통화 필드가 없어 원화 금액을 표현할 수 없다.
     * 그래서 이 승인은 422로 거부되고, 자산 카탈로그 등록조차 시도하지 않는다. 반영을 막지
     * 못하면 원화 금액이 달러 원장에 섞이고 포트폴리오 평가까지 함께 무너진다.
     */
    @Test
    void rejectsOpeningBalanceForMarketTheLedgerCannotRepresentYet() {
        PortfolioBrokerHoldingSnapshot koreanSnapshot = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                latestSnapshot.getBrokerConnection(),
                latestSnapshot.getBrokerAccount(),
                LocalDateTime.of(2026, 9, 4, 9, 30),
                0,
                List.of(new BrokerHolding(Market.KR, "005930", "삼성전자",
                        new BigDecimal("10"), new BigDecimal("70000")))
        );
        PortfolioBrokerHoldingSnapshotItem koreanItem = koreanSnapshot.getItems().getFirst();
        ReflectionTestUtils.setField(koreanItem, "id", 66L);

        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L)).thenReturn(koreanSnapshot);
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(new BrokerHoldingPreview(
                        BrokerProvider.TOSS_SECURITIES,
                        1L,
                        "*****1234",
                        LocalDateTime.of(2026, 9, 4, 9, 30),
                        List.of(new BrokerHoldingPreviewItem(
                                Market.KR,
                                "005930",
                                "삼성전자",
                                new BigDecimal("10"),
                                new BigDecimal("70000"),
                                null,
                                BrokerHoldingComparison.ONLY_IN_BROKER,
                                66L
                        )),
                        0
                ));

        assertThatThrownBy(() -> writer.createOpeningBalanceImport(10L, 20L, 66L, 10L))
                .isInstanceOf(BrokerHoldingImportUnprocessableException.class)
                .satisfies(exception -> assertThat(
                        ((BrokerHoldingImportUnprocessableException) exception).getCode())
                        .isEqualTo(ApiErrorCode.BROKER_LEDGER_MARKET_UNSUPPORTED));

        verifyNoInteractions(assetListingService, tradeTransactionRepository,
                portfolioBrokerHoldingImportRepository);
    }

    /** 원장이 표현할 수 있는 US 종목은 같은 관문을 그대로 통과한다. */
    @Test
    void allowsOpeningBalanceForLedgerWritableMarket() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.ONLY_IN_BROKER));
        when(tradeTransactionRepository.save(any(TradeTransaction.class)))
                .thenAnswer(invocation -> {
                    TradeTransaction transaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(transaction, "id", 902L);
                    return transaction;
                });
        when(portfolioBrokerHoldingImportRepository.saveAndFlush(any(PortfolioBrokerHoldingImport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioBrokerHoldingImport result = writer.createOpeningBalanceImport(10L, 20L, 55L, 10L);

        assertThat(result.getMarket()).isEqualTo(Market.US);
        assertThat(result.getTradeTransactionId()).isEqualTo(902L);
    }

    @Test
    void rejectsWhenAssetListingIsNotActive() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.ONLY_IN_BROKER));
        when(assetListingService.ensureActiveListingFromBrokerSnapshot(Market.US, "SOXL", "SOXL"))
                .thenThrow(new IllegalArgumentException("비활성 상장 종목은 거래를 등록할 수 없습니다: US / SOXL"));

        assertThatThrownBy(() -> writer.createOpeningBalanceImport(10L, 20L, 55L, 10L))
                .isInstanceOf(BrokerHoldingImportUnprocessableException.class);

        verifyNoInteractions(tradeTransactionRepository, portfolioBrokerHoldingImportRepository);
    }

    /**
     * 자산 기준정보 생성이 증권사 스냅샷 항목의 레코드와 유니크 제약에서 동시에
     * 경합하면(같은 market/ticker를 다른 포트폴리오가 동시에 개시 잔고로 반영하는
     * 경우 등) DataIntegrityViolationException이 그대로 전파돼야 한다. 이 예외는
     * 여기서 삼키지 않고 호출자인 {@link PortfolioBrokerHoldingImportService}가
     * 이미 가진 재시도 복구 로직(같은 스냅샷 항목을 다시 조회)에 맡긴다.
     */
    @Test
    void propagatesAssetListingUniqueConstraintRaceWithoutSwallowingIt() {
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(comparisonWith(BrokerHoldingComparison.ONLY_IN_BROKER));
        when(assetListingService.ensureActiveListingFromBrokerSnapshot(Market.US, "SOXL", "SOXL"))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> writer.createOpeningBalanceImport(10L, 20L, 55L, 10L))
                .isInstanceOf(DataIntegrityViolationException.class);

        verifyNoInteractions(tradeTransactionRepository, portfolioBrokerHoldingImportRepository);
    }

    /**
     * 실제 버그 재현: 증권사 스냅샷에는 PFE가 있지만 로컬 자산 카탈로그(asset_listing)에는
     * 아직 등록되지 않은 상태. 사용자가 PFE를 사전에 등록할 필요는 없다 — 연결된
     * 증권사가 정상 보유 종목으로 반환한 market/ticker/displayName 자체가 신뢰
     * 가능한 입력이므로, 외부 종목 검색을 전혀 호출하지 않고도 카탈로그를 채우고
     * 개시 잔고 반영이 성공해야 한다(구현은 한때 외부 검색 성공 여부에 의존해
     * 이 경우 무조건 422를 반환했다). {@code verifyNoInteractions}로 외부 검색이
     * 전혀 호출되지 않음을 확인해, 검색 제공자 장애/빈 응답과도 무관함을 증명한다.
     */
    @Test
    void importsBrokerOnlyTickerNotYetInLocalCatalogUsingBrokerSnapshotAsSourceOfTruth() {
        AssetListingRepository assetListingRepository = org.mockito.Mockito.mock(AssetListingRepository.class);
        AssetSearchProvider assetSearchProvider = org.mockito.Mockito.mock(AssetSearchProvider.class);
        AssetListingService realAssetListingService = new AssetListingService(
                assetListingRepository,
                assetSearchProvider,
                new AssetSearchCache()
        );

        PortfolioBrokerHoldingImportWriter pfeWriter = new PortfolioBrokerHoldingImportWriter(
                portfolioRepository,
                portfolioBrokerHoldingSnapshotService,
                REGISTRY,
                realAssetListingService,
                tradeTransactionRepository,
                portfolioBrokerHoldingImportRepository,
                FIXED_CLOCK
        );

        PortfolioBrokerHoldingSnapshot pfeSnapshot = new PortfolioBrokerHoldingSnapshot(
                portfolio,
                latestSnapshot.getBrokerConnection(),
                latestSnapshot.getBrokerAccount(),
                LocalDateTime.of(2026, 9, 4, 9, 30),
                0,
                List.of(new BrokerHolding(Market.US, "PFE", "Pfizer Inc.", new BigDecimal("50"), new BigDecimal("28.50")))
        );
        PortfolioBrokerHoldingSnapshotItem pfeItem = pfeSnapshot.getItems().getFirst();
        ReflectionTestUtils.setField(pfeItem, "id", 77L);

        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L)).thenReturn(pfeSnapshot);
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L))
                .thenReturn(new BrokerHoldingPreview(
                        BrokerProvider.TOSS_SECURITIES,
                        1L,
                        "*****1234",
                        LocalDateTime.of(2026, 9, 4, 9, 30),
                        List.of(new BrokerHoldingPreviewItem(
                                Market.US,
                                "PFE",
                                "Pfizer Inc.",
                                new BigDecimal("50"),
                                new BigDecimal("28.50"),
                                null,
                                BrokerHoldingComparison.ONLY_IN_BROKER,
                                77L
                        )),
                        0
                ));
        when(assetListingRepository.findByMarketAndTicker(Market.US, "PFE")).thenReturn(Optional.empty());
        when(assetListingRepository.save(any(AssetListing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(tradeTransactionRepository.save(any(TradeTransaction.class)))
                .thenAnswer(invocation -> {
                    TradeTransaction transaction = invocation.getArgument(0);
                    ReflectionTestUtils.setField(transaction, "id", 901L);
                    return transaction;
                });
        when(portfolioBrokerHoldingImportRepository.saveAndFlush(any(PortfolioBrokerHoldingImport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioBrokerHoldingImport result = pfeWriter.createOpeningBalanceImport(10L, 20L, 77L, 10L);

        assertThat(result.getTradeTransactionId()).isEqualTo(901L);
        assertThat(result.getQuantity()).isEqualByComparingTo("50");

        ArgumentCaptor<AssetListing> listingCaptor = ArgumentCaptor.forClass(AssetListing.class);
        verify(assetListingRepository).save(listingCaptor.capture());
        assertThat(listingCaptor.getValue().getTicker()).isEqualTo("PFE");
        assertThat(listingCaptor.getValue().getDisplayName()).isEqualTo("Pfizer Inc.");
        verifyNoInteractions(assetSearchProvider);
    }


    private BrokerHoldingPreview comparisonWith(BrokerHoldingComparison comparisonStatus) {
        return new BrokerHoldingPreview(
                BrokerProvider.TOSS_SECURITIES,
                1L,
                "*****1234",
                LocalDateTime.of(2026, 9, 4, 9, 30),
                List.of(new BrokerHoldingPreviewItem(
                        Market.US,
                        "SOXL",
                        "SOXL",
                        new BigDecimal("30"),
                        new BigDecimal("20.00"),
                        comparisonStatus == BrokerHoldingComparison.ONLY_IN_BROKER ? null : new BigDecimal("30"),
                        comparisonStatus,
                        55L
                )),
                0
        );
    }
}
