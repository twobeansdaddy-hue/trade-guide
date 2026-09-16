package com.tradeguide.domain.risk;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 포트폴리오 범위의 종목별 손절 기준 재정의다. 이 값이 있으면 해당 종목의 보유 종목
 * 가이드는 포트폴리오 기본 {@link com.tradeguide.domain.risk.PortfolioRiskPolicy#getStopLossRatio()}
 * 대신 이 값을 사용한다. 다른 포트폴리오나 후보 가이드, 전역 자산 프로필은 이 값의 영향을
 * 받지 않으며, 이 값은 참고용일 뿐 자동으로 매도 주문을 발생시키지 않는다.
 */
@Entity
@Table(
        name = "portfolio_asset_risk_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_portfolio_asset_risk_overrides_portfolio_market_ticker",
                columnNames = {"portfolio_id", "market", "ticker"}
        )
)
public class PortfolioAssetRiskOverride {

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

    @Column(name = "stop_loss_ratio", nullable = false)
    private BigDecimal stopLossRatio;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PortfolioAssetRiskOverride() {
    }

    public PortfolioAssetRiskOverride(
            Portfolio portfolio,
            Market market,
            String ticker,
            BigDecimal stopLossRatio,
            LocalDateTime createdAt
    ) {
        if (portfolio == null || market == null || ticker == null || ticker.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("포트폴리오 종목 손절 기준 재정의 정보가 올바르지 않습니다.");
        }

        requireValidRatio(stopLossRatio);

        this.portfolio = portfolio;
        this.market = market;
        this.ticker = ticker;
        this.stopLossRatio = stopLossRatio;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public void changeStopLossRatio(BigDecimal stopLossRatio, LocalDateTime updatedAt) {
        if (updatedAt == null) {
            throw new IllegalArgumentException("포트폴리오 종목 손절 기준 재정의 정보가 올바르지 않습니다.");
        }

        requireValidRatio(stopLossRatio);

        this.stopLossRatio = stopLossRatio;
        this.updatedAt = updatedAt;
    }

    private static void requireValidRatio(BigDecimal stopLossRatio) {
        if (stopLossRatio == null
                || stopLossRatio.compareTo(BigDecimal.ZERO) <= 0
                || stopLossRatio.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException("손절 기준 비율은 0보다 크고 1보다 작아야 합니다.");
        }
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

    public BigDecimal getStopLossRatio() {
        return stopLossRatio;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
