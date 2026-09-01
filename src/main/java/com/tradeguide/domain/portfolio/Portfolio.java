package com.tradeguide.domain.portfolio;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.risk.PortfolioRiskPolicy;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "portfolios")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    private String name;
    private LocalDateTime createdAt;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(
                    name = "maxLossPerTradeRatio",
                    column = @Column(name = "max_loss_per_trade_ratio",
                                     precision = 8,
                                     scale = 6)
            ),
            @AttributeOverride(
                    name = "maxSingleAssetExposureRatio",
                    column = @Column(name = "max_single_asset_exposure_ratio",
                                     precision = 8,
                                     scale = 6)
            )
    })

    private PortfolioRiskPolicy riskPolicy;

    protected Portfolio() {
    }

    public Portfolio(Member member, String name) {
        this.member = member;
        this.name = name;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public String getName() {
        return name;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public PortfolioRiskPolicy getRiskPolicy() {
        return riskPolicy;
    }

    public void changeRiskPolicy(PortfolioRiskPolicy riskPolicy) {
        if (riskPolicy == null) {
            throw new IllegalArgumentException("위험 한도 정책은 필수입니다.");
        }

        this.riskPolicy = riskPolicy;
    }
}
