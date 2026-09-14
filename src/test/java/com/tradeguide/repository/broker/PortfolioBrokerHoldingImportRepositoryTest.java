package com.tradeguide.repository.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerConnectionSecretValue;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImport;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingImportStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 개시 잔고 승인 감사 이력의 조회 경로를 검증한다. 증권사 연결 삭제로 스냅샷 항목
 * 참조가 끊긴 취소 이력도 조회에서 빠지거나 로딩에 실패하지 않아야 한다.
 */
@DataJpaTest
class PortfolioBrokerHoldingImportRepositoryTest {

    /** 서비스가 사용하는 감사 이력 정렬과 같다. 승인 시각이 같아도 순서가 결정돼야 한다. */
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
    private PortfolioBrokerHoldingImportRepository portfolioBrokerHoldingImportRepository;

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
    void persistsRevokedImportWithoutSnapshotItemReference() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "SOXL");
        PortfolioBrokerHoldingImport importRecord =
                importRecord(snapshot, LocalDateTime.of(2026, 9, 4, 9, 40), 4242L);
        importRecord.revoke();
        importRecord.detachSnapshotItem();
        Long importId = portfolioBrokerHoldingImportRepository.saveAndFlush(importRecord).getId();

        entityManager.clear();

        PortfolioBrokerHoldingImport reloaded = portfolioBrokerHoldingImportRepository.findById(importId)
                .orElseThrow();
        assertThat(reloaded.getSnapshotItem()).isNull();
        assertThat(reloaded.getStatus()).isEqualTo(PortfolioBrokerHoldingImportStatus.REVOKED);
        assertThat(reloaded.getMarket()).isEqualTo(Market.US);
        assertThat(reloaded.getTicker()).isEqualTo("SOXL");
        assertThat(reloaded.getDisplayName()).isEqualTo("SOXL");
        assertThat(reloaded.getQuantity()).isEqualByComparingTo("30");
        assertThat(reloaded.getAveragePurchasePrice()).isEqualByComparingTo("20.00");
        assertThat(reloaded.getSnapshotSyncedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 30));
        assertThat(reloaded.getTradeTransactionId()).isEqualTo(4242L);
        assertThat(reloaded.getApprovedByMemberId()).isEqualTo(member.getId());
        assertThat(reloaded.getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 40));
    }

    @Test
    void listsDetachedRevokedImportAlongsideActiveImportInApprovedAtOrder() {
        PortfolioBrokerHoldingSnapshot detachedSnapshot =
                saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "SOXL");
        PortfolioBrokerHoldingImport detached =
                importRecord(detachedSnapshot, LocalDateTime.of(2026, 9, 1, 9, 40), 4242L);
        detached.revoke();
        detached.detachSnapshotItem();
        portfolioBrokerHoldingImportRepository.saveAndFlush(detached);

        PortfolioBrokerHoldingSnapshot activeSnapshot =
                saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "AAPL");
        portfolioBrokerHoldingImportRepository.saveAndFlush(
                importRecord(activeSnapshot, LocalDateTime.of(2026, 9, 4, 9, 40), 4343L)
        );
        Long activeItemId = activeSnapshot.getItems().getFirst().getId();

        entityManager.clear();

        List<PortfolioBrokerHoldingImport> history = portfolioBrokerHoldingImportRepository
                .findAllByPortfolio_Id(portfolio.getId(), firstPage(10))
                .getContent();

        assertThat(history)
                .extracting(PortfolioBrokerHoldingImport::getTicker)
                .containsExactly("AAPL", "SOXL");
        assertThat(history)
                .extracting(PortfolioBrokerHoldingImport::getStatus)
                .containsExactly(
                        PortfolioBrokerHoldingImportStatus.ACTIVE,
                        PortfolioBrokerHoldingImportStatus.REVOKED
                );
        assertThat(history.getFirst().getSnapshotItem()).isNotNull();
        assertThat(history.getFirst().getSnapshotItem().getId()).isEqualTo(activeItemId);
        // 참조가 끊긴 취소 이력도 목록에서 빠지지 않고 널 참조 그대로 읽힌다.
        assertThat(history.getLast().getSnapshotItem()).isNull();
        assertThat(history.getLast().getQuantity()).isEqualByComparingTo("30");
        assertThat(history.getLast().getTradeTransactionId()).isEqualTo(4242L);
    }

    @Test
    void allowsMultipleDetachedImportsBecauseUniqueConstraintIgnoresNullSnapshotItem() {
        PortfolioBrokerHoldingImport first = importRecord(
                saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "SOXL"),
                LocalDateTime.of(2026, 9, 1, 9, 40),
                4242L
        );
        first.revoke();
        first.detachSnapshotItem();
        portfolioBrokerHoldingImportRepository.saveAndFlush(first);

        PortfolioBrokerHoldingImport second = importRecord(
                saveSnapshot(LocalDateTime.of(2026, 9, 2, 9, 30), "AAPL"),
                LocalDateTime.of(2026, 9, 2, 9, 40),
                4343L
        );
        second.revoke();
        second.detachSnapshotItem();
        portfolioBrokerHoldingImportRepository.saveAndFlush(second);

        entityManager.clear();

        assertThat(portfolioBrokerHoldingImportRepository
                .findAllByPortfolio_Id(portfolio.getId(), firstPage(10)).getContent())
                .hasSize(2)
                .allSatisfy(preserved -> assertThat(preserved.getSnapshotItem()).isNull());
    }

    /** 증권사 주문 이력 승인의 기준점 조회다. 취소된 개시 잔고는 기준점 후보에서 빠져야 한다. */
    @Test
    void findsTheMostRecentActiveOpeningBalanceAsTheOrderHistoryBaseline() {
        PortfolioBrokerHoldingImport revokedOlder = importRecord(
                saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "SOXL"),
                LocalDateTime.of(2026, 9, 1, 9, 40), 4242L);
        revokedOlder.revoke();
        portfolioBrokerHoldingImportRepository.saveAndFlush(revokedOlder);

        portfolioBrokerHoldingImportRepository.saveAndFlush(importRecord(
                saveSnapshot(LocalDateTime.of(2026, 9, 2, 9, 30), "AAPL"),
                LocalDateTime.of(2026, 9, 2, 9, 40), 4343L));
        PortfolioBrokerHoldingImport mostRecentActive = portfolioBrokerHoldingImportRepository.saveAndFlush(
                importRecord(saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "TSLA"),
                        LocalDateTime.of(2026, 9, 4, 9, 40), 4444L));
        entityManager.clear();

        Optional<PortfolioBrokerHoldingImport> baseline = portfolioBrokerHoldingImportRepository
                .findFirstByPortfolio_IdAndStatusOrderByApprovedAtDesc(
                        portfolio.getId(), PortfolioBrokerHoldingImportStatus.ACTIVE);

        assertThat(baseline).isPresent();
        assertThat(baseline.get().getId()).isEqualTo(mostRecentActive.getId());
        assertThat(baseline.get().getApprovedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 9, 40));
    }

    @Test
    void doesNotMatchDetachedImportWhenSearchingBySnapshotItemId() {
        PortfolioBrokerHoldingSnapshot snapshot = saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "SOXL");
        Long itemId = snapshot.getItems().getFirst().getId();
        PortfolioBrokerHoldingImport importRecord =
                importRecord(snapshot, LocalDateTime.of(2026, 9, 4, 9, 40), 4242L);
        importRecord.revoke();
        importRecord.detachSnapshotItem();
        portfolioBrokerHoldingImportRepository.saveAndFlush(importRecord);

        entityManager.clear();

        assertThat(portfolioBrokerHoldingImportRepository.findBySnapshotItem_Id(itemId)).isEmpty();
    }

    @Test
    void readsImportHistoryOnePageAtATimeWithTotalCountAndNextPageFlag() {
        for (int index = 0; index < 5; index++) {
            portfolioBrokerHoldingImportRepository.saveAndFlush(importRecord(
                    saveSnapshot(LocalDateTime.of(2026, 9, 1 + index, 9, 30), "TICK" + index),
                    LocalDateTime.of(2026, 9, 1 + index, 9, 40),
                    4200L + index
            ));
        }
        entityManager.clear();

        Page<PortfolioBrokerHoldingImport> firstPage =
                portfolioBrokerHoldingImportRepository.findAllByPortfolio_Id(portfolio.getId(), firstPage(2));

        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.getContent())
                .extracting(PortfolioBrokerHoldingImport::getTicker)
                .containsExactly("TICK4", "TICK3");

        Page<PortfolioBrokerHoldingImport> lastPage = portfolioBrokerHoldingImportRepository
                .findAllByPortfolio_Id(portfolio.getId(), PageRequest.of(2, 2, HISTORY_SORT));

        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.getContent())
                .extracting(PortfolioBrokerHoldingImport::getTicker)
                .containsExactly("TICK0");
    }

    /**
     * 일괄 반영은 여러 승인 이력이 같은 승인 시각을 갖는다. id 동점 기준이 없으면 페이지
     * 경계에서 같은 행이 두 번 나오거나 빠질 수 있으므로, 같은 시각에서도 순서가 결정돼야 한다.
     */
    @Test
    void keepsPageBoundariesStableWhenApprovalTimestampsAreIdentical() {
        LocalDateTime sameApprovedAt = LocalDateTime.of(2026, 9, 6, 10, 0);
        List<Long> savedIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            savedIds.add(portfolioBrokerHoldingImportRepository.saveAndFlush(importRecord(
                    saveSnapshot(LocalDateTime.of(2026, 9, 4, 9, 30), "SAME" + index),
                    sameApprovedAt,
                    4300L + index
            )).getId());
        }
        entityManager.clear();

        List<Long> paged = new ArrayList<>();
        for (int page = 0; page < 2; page++) {
            portfolioBrokerHoldingImportRepository
                    .findAllByPortfolio_Id(portfolio.getId(), PageRequest.of(page, 2, HISTORY_SORT))
                    .forEach(record -> paged.add(record.getId()));
        }

        assertThat(paged).doesNotHaveDuplicates();
        assertThat(paged).containsExactlyElementsOf(savedIds.reversed());
    }

    @Test
    void findsExistingImportHistoryByTickerForBatchApprovalScreening() {
        PortfolioBrokerHoldingImport revoked = importRecord(
                saveSnapshot(LocalDateTime.of(2026, 9, 1, 9, 30), "SOXL"),
                LocalDateTime.of(2026, 9, 1, 9, 40), 4242L);
        revoked.revoke();
        portfolioBrokerHoldingImportRepository.saveAndFlush(revoked);
        portfolioBrokerHoldingImportRepository.saveAndFlush(importRecord(
                saveSnapshot(LocalDateTime.of(2026, 9, 2, 9, 30), "AAPL"),
                LocalDateTime.of(2026, 9, 2, 9, 40), 4343L));
        entityManager.clear();

        List<PortfolioBrokerHoldingImport> matched = portfolioBrokerHoldingImportRepository
                .findAllByPortfolio_IdAndTickerIn(portfolio.getId(), List.of("SOXL", "TSLA"));

        assertThat(matched)
                .extracting(PortfolioBrokerHoldingImport::getTicker)
                .containsExactly("SOXL");
        assertThat(matched.getFirst().getStatus())
                .isEqualTo(PortfolioBrokerHoldingImportStatus.REVOKED);
    }

    private PageRequest firstPage(int size) {
        return PageRequest.of(0, size, HISTORY_SORT);
    }

    private PortfolioBrokerHoldingImport importRecord(
            PortfolioBrokerHoldingSnapshot snapshot,
            LocalDateTime approvedAt,
            long tradeTransactionId
    ) {
        return new PortfolioBrokerHoldingImport(
                portfolio,
                snapshot.getItems().getFirst(),
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
                List.of(new BrokerHolding(Market.US, ticker, new BigDecimal("30"), new BigDecimal("20.00")))
        ));
    }
}
