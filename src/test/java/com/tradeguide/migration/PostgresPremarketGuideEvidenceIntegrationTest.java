package com.tradeguide.migration;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.GuideInputEvidenceStatus;
import com.tradeguide.domain.strategy.PremarketGuideCandleEvidence;
import com.tradeguide.domain.strategy.PremarketGuideScope;
import com.tradeguide.domain.strategy.PremarketGuideSnapshot;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.repository.strategy.PremarketGuideSnapshotRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장전 가이드 입력 근거(V31~V33)를 실제 PostgreSQL에서 왕복 저장·조회한다.
 *
 * <p>H2 기반 {@code PremarketGuideInputAuditRepositoryTest}로는 확인할 수 없는 것은
 * 감사 기록의 {@code @MapsId} 1:1 키, 페이지 수신 기록의 {@code @OrderColumn} 순서,
 * {@code TIMESTAMP(6) WITH TIME ZONE}의 마이크로초 보존, 그리고 강제 재생성에서
 * 근거 행과 수신 기록 행을 외래키 순서에 맞게 지우는지다.
 *
 * <p>이 테스트는 어떤 시세·증권사 API도 호출하지 않는다. Docker가 필요하며 기본
 * {@code test} 태스크에서 제외된다.
 */
@Tag("postgres")
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class PostgresPremarketGuideEvidenceIntegrationTest {

    private static final Instant FIRST_PAGE = Instant.parse("2026-09-11T20:00:00.123456Z");
    private static final Instant SECOND_PAGE = Instant.parse("2026-09-11T20:00:01.654321Z");

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

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PremarketGuideSnapshotRepository snapshotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void persistsAuditAndOrderedPageReceiptsWithMicrosecondPrecision() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Portfolio portfolio = newPortfolio("pg-evidence-first@example.com");
        Instant recordedAt = Instant.parse("2026-09-14T13:00:00.000001Z");

        Long snapshotId = tx.execute(status -> {
            PremarketGuideSnapshot snapshot = newSnapshot(portfolio);
            snapshot.replaceCandleEvidence(List.of(
                    tossEvidence("SOXL", "a", List.of(FIRST_PAGE, SECOND_PAGE))));
            snapshot.recordUnverifiedInputs(recordedAt, MarketDataProvider.TOSS_SECURITIES);
            return snapshotRepository.save(snapshot).getId();
        });

        tx.executeWithoutResult(status -> {
            PremarketGuideSnapshot restored = snapshotRepository.findById(snapshotId).orElseThrow();
            assertThat(restored.getInputAudit().getEvidenceStatus())
                    .isEqualTo(GuideInputEvidenceStatus.UNVERIFIED);
            assertThat(restored.getInputAudit().getRecordedAt()).isEqualTo(recordedAt);
            assertThat(restored.getInputAudit().getMissingReasons()).isEqualTo(
                    "CANDLE_RECEIPT_NOT_CAPTURED,INPUT_DIGEST_NOT_CAPTURED,PORTFOLIO_STATE_NOT_CAPTURED");
            PremarketGuideCandleEvidence evidence = restored.getCandleEvidence().getFirst();
            assertThat(evidence.getPageReceivedAt()).containsExactly(FIRST_PAGE, SECOND_PAGE);
            assertThat(evidence.getAdjustedRequested()).isTrue();
        });

        assertThat(jdbcTemplate.queryForList(
                "SELECT guide_snapshot_id FROM premarket_guide_input_audits WHERE guide_snapshot_id = ?",
                Long.class, snapshotId)).containsExactly(snapshotId);
        assertThat(pageReceipts(snapshotId, "SOXL")).containsExactly(FIRST_PAGE, SECOND_PAGE);
        assertThat(jdbcTemplate.queryForList(
                "SELECT page_index FROM premarket_guide_candle_page_receipts r "
                        + "JOIN premarket_guide_candle_evidence e ON e.id = r.candle_evidence_id "
                        + "WHERE e.snapshot_id = ? ORDER BY r.page_index",
                Integer.class, snapshotId)).containsExactly(0, 1);
    }

    @Test
    void forcedRegenerationRemovesStaleAssetAndReplacesReceiptsInPlace() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        Portfolio portfolio = newPortfolio("pg-evidence-refresh@example.com");

        Long snapshotId = tx.execute(status -> {
            PremarketGuideSnapshot snapshot = newSnapshot(portfolio);
            snapshot.replaceCandleEvidence(List.of(
                    tossEvidence("SOXL", "a", List.of(FIRST_PAGE, SECOND_PAGE)),
                    tossEvidence("TQQQ", "b", List.of(FIRST_PAGE))));
            snapshot.recordUnverifiedInputs(Instant.parse("2026-09-14T13:00:00Z"),
                    MarketDataProvider.TOSS_SECURITIES);
            return snapshotRepository.save(snapshot).getId();
        });
        Long soxlEvidenceId = evidenceId(snapshotId, "SOXL");
        assertThat(evidenceTickers(snapshotId)).containsExactly("SOXL", "TQQQ");

        Instant refreshedPage = Instant.parse("2026-09-14T12:59:59.999999Z");
        Instant refreshedAt = Instant.parse("2026-09-14T13:05:00Z");
        tx.executeWithoutResult(status -> {
            PremarketGuideSnapshot snapshot = snapshotRepository.findById(snapshotId).orElseThrow();
            snapshot.replaceCandleEvidence(List.of(tossEvidence("SOXL", "c", List.of(refreshedPage))));
            snapshot.recordUnverifiedInputs(refreshedAt, MarketDataProvider.TOSS_SECURITIES);
        });

        assertThat(evidenceTickers(snapshotId)).containsExactly("SOXL");
        assertThat(evidenceId(snapshotId, "SOXL")).isEqualTo(soxlEvidenceId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT candle_sha256 FROM premarket_guide_candle_evidence WHERE id = ?",
                String.class, soxlEvidenceId)).isEqualTo("c".repeat(64));
        assertThat(pageReceipts(snapshotId, "SOXL")).containsExactly(refreshedPage);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM premarket_guide_candle_page_receipts r "
                        + "LEFT JOIN premarket_guide_candle_evidence e ON e.id = r.candle_evidence_id "
                        + "WHERE e.id IS NULL", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForList(
                "SELECT recorded_at FROM premarket_guide_input_audits WHERE guide_snapshot_id = ?",
                Timestamp.class, snapshotId))
                .extracting(Timestamp::toInstant).containsExactly(refreshedAt);
    }

    private Portfolio newPortfolio(String email) {
        Member member = memberRepository.save(new Member(email, email.substring(0, email.indexOf('@'))));
        return portfolioRepository.save(new Portfolio(member, "PostgreSQL 근거 검증"));
    }

    private PremarketGuideSnapshot newSnapshot(Portfolio portfolio) {
        return new PremarketGuideSnapshot(portfolio, LocalDate.of(2026, 9, 14),
                LocalDateTime.of(2026, 9, 14, 13, 0));
    }

    private PremarketGuideCandleEvidence tossEvidence(String ticker, String hashChar, List<Instant> pages) {
        return new PremarketGuideCandleEvidence(PremarketGuideScope.HELD, Market.US, ticker,
                MarketDataProvider.TOSS_SECURITIES, Instant.parse("2026-09-11T20:00:02Z"),
                hashChar.repeat(64), 101, LocalDate.of(2026, 9, 11), pages, true);
    }

    private List<String> evidenceTickers(Long snapshotId) {
        return jdbcTemplate.queryForList(
                "SELECT ticker FROM premarket_guide_candle_evidence WHERE snapshot_id = ? ORDER BY ticker",
                String.class, snapshotId);
    }

    private Long evidenceId(Long snapshotId, String ticker) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM premarket_guide_candle_evidence WHERE snapshot_id = ? AND ticker = ?",
                Long.class, snapshotId, ticker);
    }

    private List<Instant> pageReceipts(Long snapshotId, String ticker) {
        return jdbcTemplate.queryForList(
                        "SELECT r.response_received_at FROM premarket_guide_candle_page_receipts r "
                                + "JOIN premarket_guide_candle_evidence e ON e.id = r.candle_evidence_id "
                                + "WHERE e.snapshot_id = ? AND e.ticker = ? ORDER BY r.page_index",
                        Timestamp.class, snapshotId, ticker)
                .stream().map(Timestamp::toInstant).toList();
    }
}
