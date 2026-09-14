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
 * 포트폴리오 범위의 투자 트랙 재정의다. 이 값이 있으면 해당 포트폴리오의 보유 종목 가이드는
 * 전역 {@link AssetProfile}보다 이 값을 우선 사용한다. 다른 회원의 포트폴리오나 후보 가이드는
 * 이 값의 영향을 받지 않는다.
 */
@Entity
@Table(
        name = "portfolio_asset_strategy_profiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_portfolio_asset_strategy_profiles_portfolio_market_ticker",
                columnNames = {"portfolio_id", "market", "ticker"}
        )
)
public class PortfolioAssetStrategyProfile {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_track", nullable = false)
    private InvestmentTrack investmentTrack;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PortfolioAssetStrategyProfile() {
    }

    public PortfolioAssetStrategyProfile(
            Portfolio portfolio,
            Market market,
            String ticker,
            InvestmentTrack investmentTrack,
            LocalDateTime createdAt
    ) {
        if (portfolio == null
                || market == null
                || ticker == null
                || ticker.isBlank()
                || investmentTrack == null
                || createdAt == null) {
            throw new IllegalArgumentException("포트폴리오 전략 프로필 정보가 올바르지 않습니다.");
        }

        this.portfolio = portfolio;
        this.market = market;
        this.ticker = ticker;
        this.investmentTrack = investmentTrack;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void changeInvestmentTrack(InvestmentTrack investmentTrack, LocalDateTime updatedAt) {
        if (investmentTrack == null || updatedAt == null) {
            throw new IllegalArgumentException("포트폴리오 전략 프로필 정보가 올바르지 않습니다.");
        }

        this.investmentTrack = investmentTrack;
        this.updatedAt = updatedAt;
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

    public InvestmentTrack getInvestmentTrack() {
        return investmentTrack;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
