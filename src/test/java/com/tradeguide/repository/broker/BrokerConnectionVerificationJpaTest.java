package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerAccountStatus;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionCandidateAccount;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.broker.BrokerConnectionService;
import com.tradeguide.service.broker.BrokerConnectionVerifier;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 증권사 연결 재검증이 실제 외래키 제약 아래에서 계좌 행을 어떻게 다루는지 검증한다.
 *
 * <p>과거에는 재검증이 계좌 행을 지우고 새로 만들었기 때문에, 그 계좌를 참조하는 보유 종목
 * 스냅샷이 남아 있으면 외래키 위반으로 재검증이 실패했다. 목(mock)으로는 확인할 수 없는
 * 참조 무결성과 계좌 생애주기가 이 테스트의 대상이다.
 */
@DataJpaTest
class BrokerConnectionVerificationJpaTest {

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
    private TestEntityManager entityManager;

    private final ConfigurableConnectionVerifier connectionVerifier = new ConfigurableConnectionVerifier();

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
                new ReversibleBrokerCredentialCipher(),
                new BrokerCredentialLoader(new ReversibleBrokerCredentialCipher()),
                new BrokerProviderRegistry(List.of(connectionVerifier), List.of(), List.of())
        );

        member = memberRepository.save(new Member("broker@example.com", "broker-user"));
        portfolio = portfolioRepository.save(new Portfolio(member, "성장 포트폴리오"));

        BrokerConnection newConnection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        newConnection.replaceSecretValues(List.of(
                new BrokerConnectionSecretValue("clientId", "enc:client-id", "iv", 1),
                new BrokerConnectionSecretValue("clientSecret", "enc:client-secret", "iv", 1)
        ));
        newConnection.reconcileVerifiedAccounts(List.of(
                new BrokerAccount("enc:account-seq-1", "iv", "*****1234", "위탁", 1)
        ));
        newConnection.markConnected("*****1234");
        connection = brokerConnectionRepository.saveAndFlush(newConnection);
        account = connection.getAccounts().getFirst();
    }

    @Test
    void keepsAccountRowAndHistoricalSnapshotWhenProviderStillReturnsTheAccount() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "AAPL");
        portfolioBrokerLinkRepository.saveAndFlush(new PortfolioBrokerLink(
                portfolio, connection, account, LocalDateTime.of(2026, 9, 1, 9, 0)
        ));
        Long accountId = account.getId();
        connectionVerifier.respondWith(new BrokerConnectionCandidateAccount("account-seq-1", "*****9999", "종합"));

        brokerConnectionService.verifyBrokerConnection(member.getId(), connection.getId());
        entityManager.flush();
        entityManager.clear();

        BrokerAccount reused = entityManager.find(BrokerAccount.class, accountId);
        // 같은 계좌는 새 행이 아니라 같은 행을 갱신한다. 그래서 스냅샷 참조가 그대로 유지된다.
        assertThat(reused.getStatus()).isEqualTo(BrokerAccountStatus.ACTIVE);
        assertThat(reused.getDetachedAt()).isNull();
        assertThat(reused.getMaskedAccountNumber()).isEqualTo("*****9999");
        assertThat(reused.getAccountType()).isEqualTo("종합");
        assertThat(portfolioBrokerHoldingSnapshotRepository.findById(snapshot.getId())).isPresent();
        assertThat(portfolioBrokerHoldingSnapshotRepository.findById(snapshot.getId()).orElseThrow()
                .getBrokerAccount().getId()).isEqualTo(accountId);
        // 여전히 조회 가능한 계좌의 링크는 유지한다.
        assertThat(portfolioBrokerLinkRepository.findAllByPortfolio_IdOrderByLinkedAtAsc(portfolio.getId()))
                .extracting(link -> link.getBrokerAccount().getId())
                .containsExactly(accountId);
        assertThat(entityManager.getEntityManager()
                .createQuery("select count(a) from BrokerAccount a", Long.class)
                .getSingleResult()).isEqualTo(1L);
    }

    @Test
    void detachesMissingAccountWithoutDeletingRowsReferencedByHistoricalSnapshots() {
        PortfolioBrokerHoldingSnapshot older = saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "AAPL");
        PortfolioBrokerHoldingSnapshot latest = saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "SOXL");
        portfolioBrokerLinkRepository.saveAndFlush(new PortfolioBrokerLink(
                portfolio, connection, account, LocalDateTime.of(2026, 9, 1, 9, 0)
        ));
        Long accountId = account.getId();
        // 증권사가 이제 다른 계좌만 반환한다. 예전 계좌 행을 지우면 스냅샷 외래키가 깨진다.
        connectionVerifier.respondWith(new BrokerConnectionCandidateAccount("account-seq-2", "*****5678", "위탁"));

        BrokerConnection verified = brokerConnectionService.verifyBrokerConnection(member.getId(), connection.getId());
        entityManager.flush();
        entityManager.clear();

        BrokerAccount detached = entityManager.find(BrokerAccount.class, accountId);
        assertThat(detached).isNotNull();
        assertThat(detached.getStatus()).isEqualTo(BrokerAccountStatus.DETACHED);
        assertThat(detached.getDetachedAt()).isNotNull();

        // 과거 스냅샷과 항목은 그대로 남고 여전히 예전 계좌 행을 가리킨다.
        assertThat(portfolioBrokerHoldingSnapshotRepository.findById(older.getId())).isPresent();
        assertThat(portfolioBrokerHoldingSnapshotRepository.findById(latest.getId())).isPresent();
        assertThat(portfolioBrokerHoldingSnapshotRepository
                .findFirstByPortfolio_IdOrderBySyncedAtDesc(portfolio.getId()).orElseThrow()
                .getBrokerAccount().getId()).isEqualTo(accountId);

        // 새 계좌는 별도의 행으로 추가되고, 활성 계좌 목록에는 이 계좌만 남는다.
        BrokerConnection reloaded = brokerConnectionRepository.findById(connection.getId()).orElseThrow();
        assertThat(reloaded.getAccounts()).hasSize(2);
        assertThat(reloaded.getActiveAccounts())
                .extracting(BrokerAccount::getMaskedAccountNumber)
                .containsExactly("*****5678");
        assertThat(verified.getMaskedAccountLabel()).isEqualTo("*****5678");

        // 더 이상 조회할 수 없는 계좌를 가리키던 링크만 사라진다.
        assertThat(portfolioBrokerLinkRepository.findAllByPortfolio_IdOrderByLinkedAtAsc(portfolio.getId()))
                .isEmpty();
    }

    @Test
    void detachesEveryAccountWithoutFailingWhenProviderReturnsNothing() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "AAPL");
        Long accountId = account.getId();
        connectionVerifier.respondWith();

        brokerConnectionService.verifyBrokerConnection(member.getId(), connection.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(BrokerAccount.class, accountId).getStatus())
                .isEqualTo(BrokerAccountStatus.DETACHED);
        assertThat(portfolioBrokerHoldingSnapshotRepository.findById(snapshot.getId())).isPresent();
        assertThat(brokerConnectionRepository.findById(connection.getId()).orElseThrow().getActiveAccounts())
                .isEmpty();
    }

    @Test
    void reactivatesDetachedAccountWhenProviderReturnsItAgain() {
        saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "AAPL");
        Long accountId = account.getId();
        connectionVerifier.respondWith();
        brokerConnectionService.verifyBrokerConnection(member.getId(), connection.getId());
        entityManager.flush();
        entityManager.clear();

        connectionVerifier.respondWith(new BrokerConnectionCandidateAccount("account-seq-1", "*****1234", "위탁"));
        brokerConnectionService.verifyBrokerConnection(member.getId(), connection.getId());
        entityManager.flush();
        entityManager.clear();

        BrokerAccount reactivated = entityManager.find(BrokerAccount.class, accountId);
        assertThat(reactivated.getStatus()).isEqualTo(BrokerAccountStatus.ACTIVE);
        assertThat(reactivated.getDetachedAt()).isNull();
        // 계좌가 돌아와도 새 행을 만들지 않으므로 과거 스냅샷은 계속 같은 계좌를 가리킨다.
        assertThat(entityManager.getEntityManager()
                .createQuery("select count(a) from BrokerAccount a", Long.class)
                .getSingleResult()).isEqualTo(1L);
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

    /** 테스트가 지정한 계좌 후보만 돌려주는 가짜 검증기다. 실제 증권사 호출은 하지 않는다. */
    private static final class ConfigurableConnectionVerifier implements BrokerConnectionVerifier {

        private List<BrokerConnectionCandidateAccount> candidates = new ArrayList<>();

        void respondWith(BrokerConnectionCandidateAccount... accounts) {
            this.candidates = List.of(accounts);
        }

        @Override
        public BrokerProvider getProvider() {
            return BrokerProvider.TOSS_SECURITIES;
        }

        @Override
        public List<BrokerConnectionCandidateAccount> verify(BrokerCredentials credentials) {
            return candidates;
        }
    }

    /**
     * 재검증은 기존 계좌 행을 계좌 일련번호로 대조하므로 복호화가 가능한 가짜 암호기가 필요하다.
     * 실제 암호화 대신 접두사만 붙여 왕복시킨다.
     */
    private static final class ReversibleBrokerCredentialCipher implements BrokerCredentialCipher {

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public EncryptedBrokerCredential encrypt(String plaintext) {
            return new EncryptedBrokerCredential("enc:" + plaintext, "iv", 1);
        }

        @Override
        public String decrypt(EncryptedBrokerCredential encryptedCredential) {
            return encryptedCredential.ciphertext().substring("enc:".length());
        }
    }
}
