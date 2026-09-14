package com.tradeguide.domain.strategy;

import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.trade.Market;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * 사용자가 이 포트폴리오에 직접 추가한 Track A 후보 종목이다. 후보 가이드 조회는 이 값이 있으면
 * 전역 {@link AssetProfile} 후보군보다 이 포트폴리오 후보군을 우선 사용한다. 다른 회원의
 * 포트폴리오나 전역 카탈로그는 이 값의 영향을 받지 않는다.
 *
 * <p>현재는 Track A만 후보로 등록할 수 있다. Track B 후보 탐색이나 투자 추천 순위는 범위 밖이다.
 */
@Entity
@Table(
        name = "portfolio_candidate_assets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_portfolio_candidate_assets_portfolio_market_ticker",
                columnNames = {"portfolio_id", "market", "ticker"}
        )
)
public class PortfolioCandidateAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_track", nullable = false)
    private InvestmentTrack investmentTrack;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PortfolioCandidateAsset() {
    }

    public PortfolioCandidateAsset(
            Portfolio portfolio,
            Market market,
            String ticker,
            String displayName,
            InvestmentTrack investmentTrack,
            LocalDateTime createdAt
    ) {
        if (portfolio == null
                || market == null
                || ticker == null
                || ticker.isBlank()
                || displayName == null
                || displayName.isBlank()
                || investmentTrack != InvestmentTrack.TRACK_A
                || createdAt == null) {
            throw new IllegalArgumentException("포트폴리오 후보 종목 정보가 올바르지 않습니다.");
        }

        this.portfolio = portfolio;
        this.market = market;
        this.ticker = ticker;
        this.displayName = displayName;
        this.investmentTrack = investmentTrack;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public String getDisplayName() {
        return displayName;
    }

    public InvestmentTrack getInvestmentTrack() {
        return investmentTrack;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
