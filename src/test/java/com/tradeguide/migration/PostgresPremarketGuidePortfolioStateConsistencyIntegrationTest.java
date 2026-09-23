package com.tradeguide.migration;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.InvestmentTrack;
import com.tradeguide.domain.strategy.PortfolioCandidateAsset;
import com.tradeguide.domain.strategy.PremarketGuideItem;
import com.tradeguide.domain.strategy.PremarketGuideScope;
import com.tradeguide.domain.strategy.StrategyMetadata;
import com.tradeguide.domain.strategy.StrategySignal;
import com.tradeguide.domain.strategy.StrategySignalEvent;
import com.tradeguide.domain.strategy.StrategyTrend;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.domain.trade.TradeTransaction;
import com.tradeguide.domain.trade.TradeType;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.PortfolioCandidateAssetRepository;
import com.tradeguide.repository.strategy.PremarketGuideSnapshotRepository;
import com.tradeguide.repository.trade.TradeTransactionRepository;
import com.tradeguide.service.strategy.PremarketGuideService;
import com.tradeguide.service.strategy.StrategyGuideService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 장전 가이드 생성 중 원장이 바뀌어도 한 가이드 안의 보유/후보 구분이 한 시점의 상태로 일관되는지 검증한다.
 *
 * <p>C-2 제안(`docs/design/PORTFOLIO_STATE_SNAPSHOT_PROPOSAL.md`) 3절 2번의 재현 테스트다. 보유 배치와
 * 후보 배치가 원장을 각각 다시 읽으므로, 그 사이에 다른 트랜잭션이 커밋한 매수가 후보 배치에만 보이면
 * 해당 종목이 보유에도 후보에도 나오지 않는다. 외부 시세는 {@link StrategyGuideService}를 대체해 호출하지
 * 않는다. Docker가 필요하며 기본 {@code test} 태스크에서 제외된다.
 */
@Tag("postgres")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PostgresPremarketGuidePortfolioStateConsistencyIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @MockitoBean
    private StrategyGuideService strategyGuideService;

    @Autowired
    private PremarketGuideService premarketGuideService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private TradeTransactionRepository tradeTransactionRepository;

    @Autowired
    private PortfolioCandidateAssetRepository candidateAssetRepository;

    @Autowired
    private PremarketGuideSnapshotRepository snapshotRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ledgerCommitDuringGenerationDoesNotDropAssetFromBothScopes() {
        Member member = memberRepository.save(new Member("pg-state-consistency@example.com", "state-user"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio(member, "상태 일관성 검증"));
        Long portfolioId = portfolio.getId();
        tradeTransactionRepository.save(new TradeTransaction(portfolio, Market.US, "SOXL", TradeType.BUY,
                new BigDecimal("5"), new BigDecimal("100"), BigDecimal.ZERO,
                Instant.parse("2026-09-01T14:00:00Z")));
        candidateAssetRepository.save(new PortfolioCandidateAsset(portfolio, Market.US, "TQQQ",
                "TQQQ", InvestmentTrack.TRACK_A, LocalDateTime.of(2026, 9, 1, 9, 0)));

        TransactionTemplate concurrentWriter = new TransactionTemplate(transactionManager);
        concurrentWriter.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // 보유 종목(SOXL) 신호를 계산하는 동안 다른 트랜잭션이 후보 종목(TQQQ) 매수를 커밋한다.
        when(strategyGuideService.getStrategySignal(eq(portfolioId), eq(Market.US), eq("SOXL")))
                .thenAnswer(invocation -> {
                    concurrentWriter.executeWithoutResult(status -> tradeTransactionRepository.save(
                            new TradeTransaction(portfolioRepository.getReferenceById(portfolioId),
                                    Market.US, "TQQQ", TradeType.BUY, new BigDecimal("2"),
                                    new BigDecimal("80"), BigDecimal.ZERO,
                                    Instant.parse("2026-09-02T14:00:00Z"))));
                    return signal();
                });
        when(strategyGuideService.getStrategySignal(anyLong(), any(Market.class), any(String.class),
                any(InvestmentTrack.class))).thenReturn(signal());

        premarketGuideService.generateToday(member.getId(), portfolioId, true);

        // 한 시점의 상태를 썼다면 TQQQ는 후보(생성 시작 시점) 또는 보유(커밋 이후 시점) 중 정확히 한쪽에 있어야 한다.
        assertThat(scopesOf(portfolioId, "TQQQ")).hasSize(1);
        // 가이드는 시작 시 상태로 계산됐지만 저장 시점과 다르므로 감사 기록에 변경 사유가 남고 참조는 비어 있다.
        assertThat(jdbcTemplate.queryForMap(
                "SELECT a.missing_reasons, a.portfolio_state_ref, s.ledger_transaction_count "
                        + "FROM premarket_guide_input_audits a "
                        + "JOIN premarket_guide_snapshots g ON g.id = a.guide_snapshot_id "
                        + "JOIN premarket_guide_portfolio_states s ON s.guide_snapshot_id = g.id "
                        + "WHERE g.portfolio_id = ?", portfolioId))
                .containsEntry("portfolio_state_ref", null)
                .containsEntry("ledger_transaction_count", 1)
                .hasEntrySatisfying("missing_reasons", reasons -> assertThat((String) reasons)
                        .contains("PORTFOLIO_STATE_CHANGED_DURING_GENERATION"));
    }

    /** 대조군: 생성 중 원장 변경이 없으면 같은 구성에서 TQQQ는 후보에 한 번 나온다. */
    @Test
    void withoutConcurrentLedgerChangeCandidateAppearsOnce() {
        Member member = memberRepository.save(new Member("pg-state-control@example.com", "state-control"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio(member, "상태 일관성 대조군"));
        Long portfolioId = portfolio.getId();
        tradeTransactionRepository.save(new TradeTransaction(portfolio, Market.US, "SOXL", TradeType.BUY,
                new BigDecimal("5"), new BigDecimal("100"), BigDecimal.ZERO,
                Instant.parse("2026-09-01T14:00:00Z")));
        candidateAssetRepository.save(new PortfolioCandidateAsset(portfolio, Market.US, "TQQQ",
                "TQQQ", InvestmentTrack.TRACK_A, LocalDateTime.of(2026, 9, 1, 9, 0)));
        when(strategyGuideService.getStrategySignal(eq(portfolioId), eq(Market.US), eq("SOXL")))
                .thenReturn(signal());
        when(strategyGuideService.getStrategySignal(anyLong(), any(Market.class), any(String.class),
                any(InvestmentTrack.class))).thenReturn(signal());

        premarketGuideService.generateToday(member.getId(), portfolioId, true);

        assertThat(scopesOf(portfolioId, "TQQQ")).containsExactly(PremarketGuideScope.CANDIDATE);
        assertThat(scopesOf(portfolioId, "SOXL")).containsExactly(PremarketGuideScope.HELD);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT a.portfolio_state_ref FROM premarket_guide_input_audits a "
                        + "JOIN premarket_guide_snapshots g ON g.id = a.guide_snapshot_id WHERE g.portfolio_id = ?",
                String.class, portfolioId)).startsWith("portfolio-state:v1:");
    }

    private List<PremarketGuideScope> scopesOf(Long portfolioId, String ticker) {
        return new TransactionTemplate(transactionManager).execute(status ->
                snapshotRepository.findAll().stream()
                        .filter(snapshot -> snapshot.getPortfolio().getId().equals(portfolioId))
                        .flatMap(snapshot -> snapshot.getItems().stream())
                        .filter(item -> item.getTicker().equals(ticker))
                        .map(PremarketGuideItem::getScope)
                        .toList());
    }

    private StrategySignal signal() {
        return new StrategySignal(new BigDecimal("100"), "재현 테스트용 고정 신호",
                new StrategyMetadata("TRACK_A_SMA", "1", LocalDate.of(2026, 9, 11)),
                StrategyTrend.ABOVE_LONG_AVERAGE, StrategySignalEvent.NONE, null);
    }
}
