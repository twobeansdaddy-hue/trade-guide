package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustmentStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshotItem;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeTransactionSource;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.exception.BrokerConnectionDeletionBlockedException;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.broker.BrokerConnectionService;
import com.tradeguide.service.broker.BrokerCredentialCipher;
import com.tradeguide.service.broker.BrokerCredentialLoader;
import com.tradeguide.service.broker.BrokerProviderRegistry;
import com.tradeguide.service.broker.EncryptedBrokerCredential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 증권사 연결 삭제가 실제 외래키 제약 아래에서 어떤 행을 지우고 어떤 행을 남기는지
 * 검증한다. 단위 테스트의 목(mock)으로는 확인할 수 없는 스냅샷·항목·링크·감사 이력
 * 사이의 참조 무결성이 대상이다.
 */
@DataJpaTest
class BrokerConnectionDeletionJpaTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Autowired
    private PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    @Autowired
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

    @Autowired
    private PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private TestEntityManager entityManager;

    private BrokerConnectionService brokerConnectionService;

    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
        brokerConnectionService = new BrokerConnectionService(
                memberRepository,
                brokerConnectionRepository,
                portfolioBrokerLinkRepository,
                portfolioBrokerHoldingSnapshotRepository,
                portfolioBrokerHoldingImportRepository,
                portfolioBrokerHoldingAdjustmentRepository,
                new UnusedBrokerCredentialCipher(),
                new BrokerCredentialLoader(new UnusedBrokerCredentialCipher()),
                new BrokerProviderRegistry(List.of(), List.of(), List.of())
        );

        member = memberRepository.save(new Member("broker@example.com", "broker-user"));
        portfolio = portfolioRepository.save(new Portfolio(member, "성장 포트폴리오"));

        BrokerConnection newConnection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        newConnection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "encrypted-client-id", "client-id-iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "encrypted-client-secret", "client-secret-iv", 1)
        ));
        newConnection.reconcileVerifiedAccounts(List.of(
                new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1)
        ));
        newConnection.markConnected("*****1234");
        connection = brokerConnectionRepository.saveAndFlush(newConnection);
        account = connection.getAccounts().getFirst();
    }

    @Test
    void deletesConnectionWithHistoricalSnapshotsWithoutForeignKeyFailure() {
        PortfolioBrokerHoldingSnapshot older = saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "AAPL");
        PortfolioBrokerHoldingSnapshot latest = saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "SOXL");
        portfolioBrokerLinkRepository.saveAndFlush(new PortfolioBrokerLink(
                portfolio, connection, account, LocalDateTime.of(2026, 9, 1, 9, 0)
        ));
        Long olderId = older.getId();
        Long latestId = latest.getId();
        Long olderItemId = older.getItems().getFirst().getId();

        brokerConnectionService.deleteBrokerConnection(member.getId(), connection.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(brokerConnectionRepository.findById(connection.getId())).isEmpty();
        assertThat(portfolioBrokerHoldingSnapshotRepository.findById(olderId)).isEmpty();
        assertThat(portfolioBrokerHoldingSnapshotRepository.findById(latestId)).isEmpty();
        assertThat(entityManager.find(PortfolioBrokerHoldingSnapshotItem.class, olderItemId)).isNull();
        assertThat(entityManager.find(BrokerAccount.class, account.getId())).isNull();
        assertThat(portfolioBrokerLinkRepository.findAllByPortfolio_IdOrderByLinkedAtAsc(portfolio.getId()))
                .isEmpty();
    }

    @Test
    void keepsManualTradeTransactionsWhenConnectionIsDeleted() {
        saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "SOXL");
        TradeTransaction manual = tradeTransactionRepository.saveAndFlush(new TradeTransaction(
                portfolio,
                Market.US,
                "AAPL",
                TradeType.BUY,
                new BigDecimal("10"),
                new BigDecimal("140.00"),
                BigDecimal.ZERO,
                Instant.parse("2026-08-20T13:30:00Z")
        ));

        brokerConnectionService.deleteBrokerConnection(member.getId(), connection.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(tradeTransactionRepository.findById(manual.getId())).isPresent();
        assertThat(tradeTransactionRepository.findAllByPortfolio_IdOrderByTradedAtAsc(portfolio.getId()))
                .extracting(TradeTransaction::getTicker)
                .containsExactly("AAPL");
    }

    @Test
    void blocksDeleteAndKeepsEverythingWhenActiveOpeningBalanceImportExists() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "SOXL");
        PortfolioBrokerHoldingSnapshotItem item = snapshot.getItems().getFirst();
        TradeTransaction openingBalance = tradeTransactionRepository.saveAndFlush(new TradeTransaction(
                portfolio,
                item.getMarket(),
                item.getTicker(),
                TradeType.BUY,
                item.getQuantity(),
                item.getAveragePurchasePrice(),
                BigDecimal.ZERO,
                Instant.parse("2026-09-04T00:30:00Z"),
                TradeTransactionSource.BROKER_OPENING_BALANCE
        ));
        portfolioBrokerHoldingImportRepository.saveAndFlush(new PortfolioBrokerHoldingImport(
                portfolio,
                item,
                snapshot.getSyncedAt(),
                openingBalance.getId(),
                member.getId(),
                LocalDateTime.of(2026, 9, 4, 9, 40)
        ));

        assertThatThrownBy(() ->
                brokerConnectionService.deleteBrokerConnection(member.getId(), connection.getId()))
                .isInstanceOf(BrokerConnectionDeletionBlockedException.class)
                .hasMessageContaining("개시 잔고 매매 기록이 1건 남아 있어 연결을 삭제할 수 없습니다.");

        entityManager.clear();
        assertThat(brokerConnectionRepository.findById(connection.getId())).isPresent();
        assertThat(portfolioBrokerHoldingSnapshotRepository.findById(snapshot.getId())).isPresent();
        assertThat(tradeTransactionRepository.findById(openingBalance.getId())).isPresent();
    }

    @Test
    void keepsRevokedImportHistoryAfterConnectionIsDeleted() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "SOXL");
        PortfolioBrokerHoldingSnapshotItem item = snapshot.getItems().getFirst();
        PortfolioBrokerHoldingImport importRecord = new PortfolioBrokerHoldingImport(
                portfolio,
                item,
                snapshot.getSyncedAt(),
                4242L,
                member.getId(),
                LocalDateTime.of(2026, 9, 4, 9, 40)
        );
        // 취소 시 원장 행은 이미 삭제되고 감사 이력만 남는다.
        importRecord.revoke();
        Long importId = portfolioBrokerHoldingImportRepository.saveAndFlush(importRecord).getId();

        brokerConnectionService.deleteBrokerConnection(member.getId(), connection.getId());
        entityManager.flush();
        entityManager.clear();

        PortfolioBrokerHoldingImport preserved = portfolioBrokerHoldingImportRepository.findById(importId)
                .orElseThrow();
        assertThat(preserved.getSnapshotItem()).isNull();
        assertThat(preserved.getTicker()).isEqualTo("SOXL");
        assertThat(preserved.getQuantity()).isEqualByComparingTo("30");
        assertThat(preserved.getAveragePurchasePrice()).isEqualByComparingTo("20.00");
        assertThat(preserved.getSnapshotSyncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
        assertThat(preserved.getTradeTransactionId()).isEqualTo(4242L);
        assertThat(brokerConnectionRepository.findById(connection.getId())).isEmpty();
        assertThat(portfolioBrokerHoldingSnapshotRepository.findById(snapshot.getId())).isEmpty();
    }

    private PortfolioBrokerHoldingSnapshot saveSnapshot(LocalDateTime syncedAt, String ticker) {
        return portfolioBrokerHoldingSnapshotRepository.saveAndFlush(new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                account,
                syncedAt,
                0,
                List.of(new BrokerHolding(Market.US, ticker, new BigDecimal("30"), new BigDecimal("20.00")))
        ));
    }

    /** 삭제 경로는 자격 증명을 복호화하지 않으므로 호출되면 테스트가 실패해야 한다. */
    private static final class UnusedBrokerCredentialCipher implements BrokerCredentialCipher {
        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public EncryptedBrokerCredential encrypt(String plaintext) {
            throw new AssertionError("증권사 연결 삭제는 자격 증명을 암호화하지 않는다.");
        }

        @Override
        public String decrypt(EncryptedBrokerCredential encryptedCredential) {
            throw new AssertionError("증권사 연결 삭제는 자격 증명을 복호화하지 않는다.");
        }
    }
}
