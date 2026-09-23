package com.tradeguide.repository.strategy;

import com.tradeguide.domain.market.MarketDataProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.GuideInputEvidenceStatus;
import com.tradeguide.domain.strategy.PremarketGuideCandidateSource;
import com.tradeguide.domain.strategy.PremarketGuideCandleEvidence;
import com.tradeguide.domain.strategy.PremarketGuidePortfolioStateDigests;
import com.tradeguide.domain.strategy.PremarketGuideScope;
import com.tradeguide.domain.strategy.PremarketGuideSnapshot;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.member.MemberRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PremarketGuideInputAuditRepositoryTest {

    @Autowired private MemberRepository memberRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private PremarketGuideSnapshotRepository snapshotRepository;
    @Autowired private TestEntityManager entityManager;

    @Test
    void persistsUnverifiedAuditWithGuideAndRefreshesItInPlace() {
        Member member = memberRepository.save(new Member("audit@example.com", "audit-user"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio(member, "감사 테스트"));
        LocalDate date = LocalDate.of(2026, 9, 14);
        PremarketGuideSnapshot snapshot = new PremarketGuideSnapshot(
                portfolio, date, LocalDateTime.of(2026, 9, 14, 13, 0));
        Instant first = Instant.parse("2026-09-14T13:00:00Z");
        snapshot.recordUnverifiedInputs(first, MarketDataProvider.TWELVE_DATA);
        snapshotRepository.saveAndFlush(snapshot);
        Long id = snapshot.getId();
        entityManager.clear();

        PremarketGuideSnapshot restored = snapshotRepository.findById(id).orElseThrow();
        assertThat(restored.getInputAudit().getEvidenceStatus())
                .isEqualTo(GuideInputEvidenceStatus.UNVERIFIED);
        assertThat(restored.getInputAudit().getRecordedAt()).isEqualTo(first);
        assertThat(restored.getInputAudit().getResponseReceivedAt()).isNull();
        assertThat(restored.getInputAudit().getInputSha256()).isNull();

        Instant second = Instant.parse("2026-09-14T13:05:00Z");
        restored.recordUnverifiedInputs(second, MarketDataProvider.TOSS_SECURITIES);
        snapshotRepository.saveAndFlush(restored);
        entityManager.clear();

        PremarketGuideSnapshot refreshed = snapshotRepository.findById(id).orElseThrow();
        assertThat(refreshed.getInputAudit().getRecordedAt()).isEqualTo(second);
        assertThat(refreshed.getInputAudit().getMissingReasons())
                .contains("CANDLE_RECEIPT_NOT_CAPTURED");
    }

    @Test
    void legacyGuideWithoutAuditRemainsReadable() {
        Member member = memberRepository.save(new Member("legacy-audit@example.com", "legacy-user"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio(member, "기존 가이드"));
        PremarketGuideSnapshot snapshot = snapshotRepository.saveAndFlush(new PremarketGuideSnapshot(
                portfolio, LocalDate.of(2026, 9, 14), LocalDateTime.of(2026, 9, 14, 13, 0)));
        Long id = snapshot.getId();
        entityManager.clear();

        assertThat(snapshotRepository.findById(id).orElseThrow().getInputAudit()).isNull();
    }

    @Test
    void replacesCandleEvidenceInPlaceAndRemovesStaleAsset() {
        Member member = memberRepository.save(new Member("candle-audit@example.com", "candle-user"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio(member, "시세 근거"));
        PremarketGuideSnapshot snapshot = new PremarketGuideSnapshot(
                portfolio, LocalDate.of(2026, 9, 14), LocalDateTime.of(2026, 9, 14, 13, 0));
        snapshot.replaceCandleEvidence(java.util.List.of(candleEvidence("a".repeat(64))));
        snapshotRepository.saveAndFlush(snapshot);
        Long id = snapshot.getId();
        entityManager.clear();

        PremarketGuideSnapshot restored = snapshotRepository.findById(id).orElseThrow();
        assertThat(restored.getCandleEvidence()).hasSize(1);
        assertThat(restored.getCandleEvidence().get(0).getCandleSha256()).isEqualTo("a".repeat(64));

        restored.replaceCandleEvidence(java.util.List.of(candleEvidence("b".repeat(64))));
        snapshotRepository.saveAndFlush(restored);
        entityManager.clear();
        PremarketGuideSnapshot refreshed = snapshotRepository.findById(id).orElseThrow();
        assertThat(refreshed.getCandleEvidence()).hasSize(1);
        assertThat(refreshed.getCandleEvidence().get(0).getCandleSha256()).isEqualTo("b".repeat(64));

        refreshed.replaceCandleEvidence(java.util.List.of());
        snapshotRepository.saveAndFlush(refreshed);
        entityManager.clear();
        assertThat(snapshotRepository.findById(id).orElseThrow().getCandleEvidence()).isEmpty();
    }

    @Test
    void persistsOrderedPageReceiptsAndClearsThemOnRefresh() {
        Member member = memberRepository.save(new Member("page-audit@example.com", "page-user"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio(member, "응답 페이지 근거"));
        PremarketGuideSnapshot snapshot = new PremarketGuideSnapshot(
                portfolio, LocalDate.of(2026, 9, 14), LocalDateTime.of(2026, 9, 14, 13, 0));
        Instant first = Instant.parse("2026-09-11T20:00:00Z");
        Instant second = Instant.parse("2026-09-11T20:00:01Z");
        snapshot.replaceCandleEvidence(java.util.List.of(new PremarketGuideCandleEvidence(
                PremarketGuideScope.HELD, Market.US, "SOXL", MarketDataProvider.TOSS_SECURITIES,
                Instant.parse("2026-09-11T20:00:02Z"), "a".repeat(64), 40,
                LocalDate.of(2026, 9, 11), java.util.List.of(first, second), true)));
        snapshotRepository.saveAndFlush(snapshot);
        Long id = snapshot.getId();
        entityManager.clear();

        PremarketGuideSnapshot restored = snapshotRepository.findById(id).orElseThrow();
        assertThat(restored.getCandleEvidence().getFirst().getPageReceivedAt())
                .containsExactly(first, second);
        assertThat(restored.getCandleEvidence().getFirst().getAdjustedRequested()).isTrue();

        restored.replaceCandleEvidence(java.util.List.of(candleEvidence("b".repeat(64))));
        snapshotRepository.saveAndFlush(restored);
        entityManager.clear();

        PremarketGuideCandleEvidence refreshed = snapshotRepository.findById(id).orElseThrow()
                .getCandleEvidence().getFirst();
        assertThat(refreshed.getPageReceivedAt()).isEmpty();
        assertThat(refreshed.getAdjustedRequested()).isNull();
    }

    @Test
    void persistsResponseReceiptWithoutAssumingAdjustmentMode() {
        Member member = memberRepository.save(new Member("twelve-receipt@example.com", "twelve-user"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio(member, "Twelve 응답 근거"));
        PremarketGuideSnapshot snapshot = new PremarketGuideSnapshot(
                portfolio, LocalDate.of(2026, 9, 14), LocalDateTime.of(2026, 9, 14, 13, 0));
        Instant receivedAt = Instant.parse("2026-09-11T20:00:00Z");
        snapshot.replaceCandleEvidence(java.util.List.of(new PremarketGuideCandleEvidence(
                PremarketGuideScope.HELD, Market.US, "SOXL", MarketDataProvider.TWELVE_DATA,
                Instant.parse("2026-09-11T20:00:01Z"), "a".repeat(64), 40,
                LocalDate.of(2026, 9, 11), java.util.List.of(receivedAt), null)));
        snapshotRepository.saveAndFlush(snapshot);
        Long id = snapshot.getId();
        entityManager.clear();

        PremarketGuideCandleEvidence restored = snapshotRepository.findById(id).orElseThrow()
                .getCandleEvidence().getFirst();
        assertThat(restored.getPageReceivedAt()).containsExactly(receivedAt);
        assertThat(restored.getAdjustedRequested()).isNull();
    }

    @Test
    void persistsPortfolioStateDigestsAndRefreshesThemInPlace() {
        Member member = memberRepository.save(new Member("state-audit@example.com", "state-user"));
        Portfolio portfolio = portfolioRepository.save(new Portfolio(member, "포트폴리오 상태"));
        PremarketGuideSnapshot snapshot = new PremarketGuideSnapshot(
                portfolio, LocalDate.of(2026, 9, 14), LocalDateTime.of(2026, 9, 14, 13, 0));
        snapshot.recordUnverifiedInputs(Instant.parse("2026-09-14T13:00:00Z"), MarketDataProvider.TWELVE_DATA,
                stateDigests("a", 42L), false);
        snapshotRepository.saveAndFlush(snapshot);
        Long id = snapshot.getId();
        entityManager.clear();

        PremarketGuideSnapshot restored = snapshotRepository.findById(id).orElseThrow();
        assertThat(restored.getPortfolioState().getStateSha256()).isEqualTo("a".repeat(64));
        assertThat(restored.getPortfolioState().getCandidateSource())
                .isEqualTo(PremarketGuideCandidateSource.PORTFOLIO);
        assertThat(restored.getPortfolioState().getBrokerSnapshotId()).isEqualTo(42L);
        assertThat(restored.getInputAudit().getPortfolioStateRef())
                .isEqualTo("portfolio-state:v1:" + "a".repeat(64));
        assertThat(restored.getInputAudit().getMissingReasons()).doesNotContain("PORTFOLIO_STATE");

        restored.recordUnverifiedInputs(Instant.parse("2026-09-14T13:05:00Z"), MarketDataProvider.TWELVE_DATA,
                stateDigests("b", null), true);
        snapshotRepository.saveAndFlush(restored);
        entityManager.clear();

        PremarketGuideSnapshot refreshed = snapshotRepository.findById(id).orElseThrow();
        assertThat(refreshed.getPortfolioState().getStateSha256()).isEqualTo("b".repeat(64));
        assertThat(refreshed.getPortfolioState().getBrokerSnapshotId()).isNull();
        assertThat(refreshed.getInputAudit().getPortfolioStateRef()).isNull();
        assertThat(refreshed.getInputAudit().getMissingReasons())
                .contains("PORTFOLIO_STATE_CHANGED_DURING_GENERATION");

        refreshed.recordUnverifiedInputs(Instant.parse("2026-09-14T13:10:00Z"), MarketDataProvider.TWELVE_DATA);
        snapshotRepository.saveAndFlush(refreshed);
        entityManager.clear();

        PremarketGuideSnapshot withoutState = snapshotRepository.findById(id).orElseThrow();
        assertThat(withoutState.getPortfolioState()).isNull();
        assertThat(withoutState.getInputAudit().getMissingReasons()).contains("PORTFOLIO_STATE_NOT_CAPTURED");
    }

    private PremarketGuidePortfolioStateDigests stateDigests(String hashChar, Long brokerSnapshotId) {
        String hash = hashChar.repeat(64);
        return new PremarketGuidePortfolioStateDigests(1, Instant.parse("2026-09-14T12:59:00Z"),
                hash, 3, hash, hash, hash, PremarketGuideCandidateSource.PORTFOLIO, hash, hash,
                brokerSnapshotId, hash);
    }

    private PremarketGuideCandleEvidence candleEvidence(String sha256) {
        return new PremarketGuideCandleEvidence(PremarketGuideScope.HELD, Market.US, "SOXL",
                MarketDataProvider.TWELVE_DATA, Instant.parse("2026-09-11T21:00:00Z"),
                sha256, 40, LocalDate.of(2026, 9, 11));
    }
}
