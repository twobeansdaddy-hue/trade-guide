package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustment;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingAdjustmentStatus;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 증권사 잔고 조정 승인 감사 이력의 조회 경로를 검증한다. 증권사 연결 삭제로 스냅샷
 * 항목 참조가 끊긴 취소 이력도 조회에서 빠지거나 로딩에 실패하지 않아야 한다.
 */
@DataJpaTest
class PortfolioBrokerHoldingAdjustmentRepositoryTest {

    private static final Sort HISTORY_SORT =
            Sort.by(Sort.Order.desc("approvedAt"), Sort.Order.desc("id"));

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private BrokerConnectionRepository brokerConnectionRepository;

    @Autowired
    private PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    @Autowired
    private PortfolioBrokerHoldingAdjustmentRepository portfolioBrokerHoldingAdjustmentRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Member member;
    private Portfolio portfolio;
    private BrokerConnection connection;
    private BrokerAccount account;

    @BeforeEach
    void setUp() {
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
    void persistsAndReloadsActiveAdjustment() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 11, 9, 30), "SOXL");
        Long adjustmentId = portfolioBrokerHoldingAdjustmentRepository
                .saveAndFlush(adjustment(snapshot, LocalDateTime.of(2026, 9, 11, 9, 40), 4242L))
                .getId();

        entityManager.clear();

        PortfolioBrokerHoldingAdjustment reloaded = portfolioBrokerHoldingAdjustmentRepository
                .findById(adjustmentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PortfolioBrokerHoldingAdjustmentStatus.ACTIVE);
        assertThat(reloaded.getMarket()).isEqualTo(Market.US);
        assertThat(reloaded.getTicker()).isEqualTo("SOXL");
        assertThat(reloaded.getDeltaQuantity()).isEqualByComparingTo("5");
        assertThat(reloaded.getUnitPrice()).isEqualByComparingTo("160.00");
        assertThat(reloaded.getBrokerQuantity()).isEqualByComparingTo("15");
        assertThat(reloaded.getBrokerAveragePurchasePrice()).isEqualByComparingTo("120.00");
        assertThat(reloaded.getLedgerQuantityBefore()).isEqualByComparingTo("10");
        assertThat(reloaded.getLedgerAveragePurchasePriceBefore()).isEqualByComparingTo("100.00");
        assertThat(reloaded.getTradeTransactionId()).isEqualTo(4242L);
        assertThat(reloaded.getApprovedByMemberId()).isEqualTo(member.getId());
    }

    @Test
    void persistsRevokedAdjustmentWithoutSnapshotItemReference() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 11, 9, 30), "SOXL");
        PortfolioBrokerHoldingAdjustment record =
                adjustment(snapshot, LocalDateTime.of(2026, 9, 11, 9, 40), 4242L);
        record.revoke();
        record.detachSnapshotItem();
        Long adjustmentId = portfolioBrokerHoldingAdjustmentRepository.saveAndFlush(record).getId();

        entityManager.clear();

        PortfolioBrokerHoldingAdjustment reloaded = portfolioBrokerHoldingAdjustmentRepository
                .findById(adjustmentId).orElseThrow();
        assertThat(reloaded.getSnapshotItem()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(PortfolioBrokerHoldingAdjustmentStatus.REVOKED);
        assertThat(reloaded.getTicker()).isEqualTo("SOXL");
        assertThat(reloaded.getDeltaQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void allowsMultipleDetachedAdjustmentsBecauseUniqueConstraintIgnoresNullSnapshotItem() {
        PortfolioBrokerHoldingAdjustment first = adjustment(
                saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "SOXL"),
                LocalDateTime.of(2026, 9, 1, 9, 40), 4242L);
        first.revoke();
        first.detachSnapshotItem();
        portfolioBrokerHoldingAdjustmentRepository.saveAndFlush(first);

        PortfolioBrokerHoldingAdjustment second = adjustment(
                saveSnapshot(LocalDateTime.of(2026, 9, 2, 9, 30), "AAPL"),
                LocalDateTime.of(2026, 9, 2, 9, 40), 4343L);
        second.revoke();
        second.detachSnapshotItem();
        portfolioBrokerHoldingAdjustmentRepository.saveAndFlush(second);

        entityManager.clear();

        assertThat(portfolioBrokerHoldingAdjustmentRepository
                .findAllByPortfolio_Id(portfolio.getId(), firstPage(10)).getContent())
                .hasSize(2)
                .allSatisfy(preserved -> assertThat(preserved.getSnapshotItem()).isNull());
    }

    @Test
    void doesNotMatchDetachedAdjustmentWhenSearchingBySnapshotItemId() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 11, 9, 30), "SOXL");
        Long itemId = snapshot.getItems().getFirst().getId();
        PortfolioBrokerHoldingAdjustment record =
                adjustment(snapshot, LocalDateTime.of(2026, 9, 11, 9, 40), 4242L);
        record.revoke();
        record.detachSnapshotItem();
        portfolioBrokerHoldingAdjustmentRepository.saveAndFlush(record);

        entityManager.clear();

        assertThat(portfolioBrokerHoldingAdjustmentRepository.findBySnapshotItem_Id(itemId)).isEmpty();
    }

    @Test
    void readsAdjustmentHistoryOnePageAtATimeWithTotalCountAndNextPageFlag() {
        for (int index = 0; index < 5; index++) {
            portfolioBrokerHoldingAdjustmentRepository.saveAndFlush(adjustment(
                    saveSnapshot(LocalDateTime.of(2026, 9, 1 + index, 9, 30), "TICK" + index),
                    LocalDateTime.of(2026, 9, 1 + index, 9, 40),
                    4200L + index
            ));
        }
        entityManager.clear();

        Page<PortfolioBrokerHoldingAdjustment> firstPage =
                portfolioBrokerHoldingAdjustmentRepository.findAllByPortfolio_Id(portfolio.getId(), firstPage(2));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.getContent())
                .extracting(PortfolioBrokerHoldingAdjustment::getTicker)
                .containsExactly("TICK4", "TICK3");

        Page<PortfolioBrokerHoldingAdjustment> lastPage = portfolioBrokerHoldingAdjustmentRepository
                .findAllByPortfolio_Id(portfolio.getId(), PageRequest.of(2, 2, HISTORY_SORT));

        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.getContent())
                .extracting(PortfolioBrokerHoldingAdjustment::getTicker)
                .containsExactly("TICK0");
    }

    @Test
    void findsAdjustmentByPortfolioAndId() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 11, 9, 30), "SOXL");
        Long adjustmentId = portfolioBrokerHoldingAdjustmentRepository
                .saveAndFlush(adjustment(snapshot, LocalDateTime.of(2026, 9, 11, 9, 40), 4242L))
                .getId();

        entityManager.clear();

        assertThat(portfolioBrokerHoldingAdjustmentRepository
                .findByPortfolio_IdAndId(portfolio.getId(), adjustmentId))
                .isPresent();
        assertThat(portfolioBrokerHoldingAdjustmentRepository
                .findByPortfolio_IdAndId(portfolio.getId() + 999, adjustmentId))
                .isEmpty();
    }

    private PageRequest firstPage(int size) {
        return PageRequest.of(0, size, HISTORY_SORT);
    }

    private PortfolioBrokerHoldingAdjustment adjustment(
            PortfolioBrokerHoldingSnapshot snapshot,
            LocalDateTime approvedAt,
            long tradeTransactionId
    ) {
        return new PortfolioBrokerHoldingAdjustment(
                portfolio,
                snapshot.getItems().getFirst(),
                new BigDecimal("5"),
                new BigDecimal("160.00"),
                new BigDecimal("15"),
                new BigDecimal("120.00"),
                new BigDecimal("10"),
                new BigDecimal("100.00"),
                snapshot.getSyncedAt(),
                tradeTransactionId,
                member.getId(),
                approvedAt
        );
    }

    private PortfolioBrokerHoldingSnapshot saveSnapshot(LocalDateTime syncedAt, String ticker) {
        return portfolioBrokerHoldingSnapshotRepository.saveAndFlush(new PortfolioBrokerHoldingSnapshot(
                portfolio,
                connection,
                account,
                syncedAt,
                0,
                List.of(new BrokerHolding(Market.US, ticker, new BigDecimal("15"), new BigDecimal("120.00")))
        ));
    }
}
