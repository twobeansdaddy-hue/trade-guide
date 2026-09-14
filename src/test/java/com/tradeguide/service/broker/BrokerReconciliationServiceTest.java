package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHoldingComparison;
import com.tradeguide.domain.broker.BrokerHoldingPreview;
import com.tradeguide.domain.broker.BrokerHoldingPreviewItem;
import com.tradeguide.domain.broker.BrokerOrderImportRunStatus;
import com.tradeguide.domain.broker.BrokerOrderLedgerLinkStatus;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.BrokerReconciliationOverallStatus;
import com.tradeguide.domain.broker.BrokerReconciliationReasonCode;
import com.tradeguide.domain.broker.BrokerReconciliationRun;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.BrokerReconciliationRunNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.broker.BrokerOrderLedgerLinkRepository;
import com.tradeguide.repository.broker.BrokerReconciliationRunRepository;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingImportRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 사용자 주도 원장 정합성 점검 서비스를 검증한다.
 *
 * <p>이 서비스는 증권사를 호출하지 않는다. 그래서 이 테스트에는 증권사 어댑터가 없고,
 * 스냅샷·주문 이력·개시 잔고 이력은 전부 저장된 값을 흉내 낸 목이다. 검증 대상은
 * "저장된 데이터만으로 종목별 차이와 사유 후보를 계산해 실행 한 건으로 남기는가"다.
 */
@ExtendWith(MockitoExtension.class)
class BrokerReconciliationServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-08T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioBrokerHoldingSnapshotService portfolioBrokerHoldingSnapshotService;

    @Mock
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

    @Mock
    private BrokerOrderImportRunRepository brokerOrderImportRunRepository;

    @Mock
    private BrokerOrderLedgerLinkRepository brokerOrderLedgerLinkRepository;

    @Mock
    private BrokerReconciliationRunRepository brokerReconciliationRunRepository;

    private BrokerReconciliationService service;

    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
        service = new BrokerReconciliationService(
                portfolioRepository,
                portfolioBrokerHoldingSnapshotService,
                portfolioBrokerHoldingImportRepository,
                brokerOrderImportRunRepository,
                brokerOrderLedgerLinkRepository,
                brokerReconciliationRunRepository,
                new BrokerReconciliationReasonResolver(new BrokerProviderRegistry(List.of(), List.of(), List.of())),
                FIXED_CLOCK
        );

        member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);

        connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        ReflectionTestUtils.setField(connection, "id", 1L);
        connection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1)
        ));
        account = new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1);
        ReflectionTestUtils.setField(account, "id", 100L);
        connection.reconcileVerifiedAccounts(List.of(account));
        connection.markConnected("*****1234");
    }

    @Test
    void createsAReconciliationRunFromTheStoredSnapshotWithoutCallingTheBroker() {
        givenPortfolioExists();
        PortfolioBrokerHoldingSnapshot snapshot = snapshotSyncedAt(LocalDateTime.of(2026, 9, 8, 8, 0));
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshot(10L, 20L)).thenReturn(snapshot);
        when(portfolioBrokerHoldingSnapshotService.getLatestSnapshotComparison(10L, 20L)).thenReturn(
                new BrokerHoldingPreview(
                        BrokerProvider.TOSS_SECURITIES, 1L, "*****1234",
                        snapshot.getSyncedAt(),
                        List.of(
                                new BrokerHoldingPreviewItem(
                                        Market.US, "AAPL", "Apple Inc.",
                                        new BigDecimal("10"), new BigDecimal("150.00"),
                                        new BigDecimal("10"), BrokerHoldingComparison.MATCHED),
                                new BrokerHoldingPreviewItem(
                                        Market.KR, "005930", "삼성전자",
                                        new BigDecimal("10"), new BigDecimal("70000"),
                                        null, BrokerHoldingComparison.ONLY_IN_BROKER)
                        ),
                        1
                )
        );
        givenNoOrderHistoryOrBaseline();
        when(brokerReconciliationRunRepository.saveAndFlush(any(BrokerReconciliationRun.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BrokerReconciliationRun run = service.createReconciliation(10L, 20L);

        assertThat(run.getOverallStatus()).isEqualTo(BrokerReconciliationOverallStatus.DIFFERENCES_FOUND);
        assertThat(run.getMatchedCount()).isEqualTo(1);
        assertThat(run.getOnlyInBrokerCount()).isEqualTo(1);
        assertThat(run.getSnapshotId()).isEqualTo(snapshot.getId());
        assertThat(run.getExecutedAt()).isEqualTo(LocalDateTime.of(2026, 9, 8, 9, 0));

        assertThat(run.getLines()).extracting("ticker", "comparison").containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("AAPL", BrokerHoldingComparison.MATCHED),
                org.assertj.core.groups.Tuple.tuple("005930", BrokerHoldingComparison.ONLY_IN_BROKER)
        );

        var krLine = run.getLines().stream().filter(line -> line.getTicker().equals("005930")).findFirst().orElseThrow();
        assertThat(krLine.getReasonCandidates()).containsExactlyInAnyOrder(
                BrokerReconciliationReasonCode.LEDGER_MARKET_UNSUPPORTED,
                BrokerReconciliationReasonCode.OUT_OF_PERIOD_HISTORY
        );

        var matchedLine = run.getLines().stream().filter(line -> line.getTicker().equals("AAPL")).findFirst().orElseThrow();
        assertThat(matchedLine.getReasonCandidates()).isEmpty();

        ArgumentCaptor<BrokerReconciliationRun> captor = ArgumentCaptor.forClass(BrokerReconciliationRun.class);
        verify(brokerReconciliationRunRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).isSameAs(run);
    }

    @Test
    void failsToCreateWhenThePortfolioBelongsToAnotherMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createReconciliation(99L, 20L))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verifyNoInteractions(portfolioBrokerHoldingSnapshotService, brokerReconciliationRunRepository);
    }

    @Test
    void listsRunsForTheOwningPortfolioOnly() {
        givenPortfolioExists();
        when(brokerReconciliationRunRepository.findAllByPortfolio_Id(any(), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var page = service.getRuns(10L, 20L, com.tradeguide.domain.broker.BrokerHistoryPageRequest.of(null, null));

        assertThat(page.items()).isEmpty();
    }

    @Test
    void failsDetailLookupWhenRunDoesNotBelongToThePortfolio() {
        givenPortfolioExists();
        when(brokerReconciliationRunRepository.findByPortfolio_IdAndId(20L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRun(10L, 20L, 999L))
                .isInstanceOf(BrokerReconciliationRunNotFoundException.class)
                .hasMessage("요청한 정합성 점검 실행을 찾을 수 없습니다.");
    }

    private void givenPortfolioExists() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
    }

    private void givenNoOrderHistoryOrBaseline() {
        lenient().when(portfolioBrokerHoldingImportRepository.findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(
                        20L, PortfolioBrokerHoldingImportStatus.ACTIVE))
                .thenReturn(Optional.empty());
        lenient().when(brokerOrderImportRunRepository.findFirstByBrokerAccount_IdAndStatusOrderByStartedAtDesc(
                        100L, BrokerOrderImportRunStatus.STAGED))
                .thenReturn(Optional.empty());
        lenient().when(brokerOrderLedgerLinkRepository.findAllByBrokerAccount_IdAndStatus(
                        100L, BrokerOrderLedgerLinkStatus.ACTIVE))
                .thenReturn(List.of());
    }

    private PortfolioBrokerHoldingSnapshot snapshotSyncedAt(LocalDateTime syncedAt) {
        PortfolioBrokerHoldingSnapshot snapshot = new PortfolioBrokerHoldingSnapshot(
                portfolio, connection, account, syncedAt, 1, List.of());
        ReflectionTestUtils.setField(snapshot, "id", 500L);
        return snapshot;
    }
}
