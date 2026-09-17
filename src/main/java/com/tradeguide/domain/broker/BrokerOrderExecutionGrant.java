package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
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
 * 회원 한 명이 증권사 연결 한 건에 대해 자동 주문 실행을 명시적으로 opt-in한 동의다
 * (`docs/BROKER_AND_PROVIDER_ARCHITECTURE.md` Purpose 절, 2026-09-17 개정).
 *
 * <p>이 행은 브로커 자격 증명을 담지 않는다 - {@link BrokerConnection}을 참조만 하고,
 * 실제 복호화·주문 호출은 별도 계약(이 슬라이스 범위 밖)에서 수행한다. 이 슬라이스는
 * "동의를 저장하고 켜고 끄는" 것까지만 구현한다 - 실제 주문 제출 로직은 없다.
 *
 * <p>{@code strategyId}는 `research/STRATEGY_ENGINE_POLICY.md`가 정의한 검증된 전략
 * 화이트리스트 개념을 따른다. 이번 슬라이스는 빈 값만 거부하고, 실제 전략 레지스트리
 * 대조는 이후 슬라이스로 미룬다.
 *
 * <p>포지션 한도·일일 주문 한도는 서비스 계층이 시스템 하드 상한을 넘지 않는지 검증한
 * 뒤에만 이 생성자가 호출된다 - 엔티티 자체는 음수·0 이하 값만 방어한다.
 */
@Entity
@Table(
        name = "broker_order_execution_grants",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_broker_order_execution_grants_connection",
                columnNames = "broker_connection_id"
        )
)
public class BrokerOrderExecutionGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_connection_id", nullable = false)
    private BrokerConnection brokerConnection;

    @Column(name = "strategy_id", nullable = false, length = 100)
    private String strategyId;

    @Column(name = "max_position_size_per_order_percent", nullable = false, precision = 5, scale = 4)
    private BigDecimal maxPositionSizePerOrderPercent;

    @Column(name = "max_daily_order_count", nullable = false)
    private int maxDailyOrderCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BrokerOrderExecutionGrantStatus status;

    @Column(name = "consented_at", nullable = false)
    private LocalDateTime consentedAt;

    @Column(name = "consent_version", nullable = false, length = 50)
    private String consentVersion;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected BrokerOrderExecutionGrant() {
    }

    public BrokerOrderExecutionGrant(
            Member member,
            BrokerConnection brokerConnection,
            String strategyId,
            BigDecimal maxPositionSizePerOrderPercent,
            int maxDailyOrderCount,
            String consentVersion
    ) {
        if (member == null || brokerConnection == null) {
            throw new IllegalArgumentException("회원과 증권사 연결 정보가 필요합니다.");
        }
        if (strategyId == null || strategyId.isBlank()) {
            throw new IllegalArgumentException("전략 ID는 필수입니다.");
        }
        if (maxPositionSizePerOrderPercent == null
                || maxPositionSizePerOrderPercent.signum() <= 0) {
            throw new IllegalArgumentException("포지션 한도는 0보다 커야 합니다.");
        }
        if (maxDailyOrderCount <= 0) {
            throw new IllegalArgumentException("일일 주문 한도는 0보다 커야 합니다.");
        }
        if (consentVersion == null || consentVersion.isBlank()) {
            throw new IllegalArgumentException("동의 버전 정보가 필요합니다.");
        }

        this.member = member;
        this.brokerConnection = brokerConnection;
        this.strategyId = strategyId;
        this.maxPositionSizePerOrderPercent = maxPositionSizePerOrderPercent;
        this.maxDailyOrderCount = maxDailyOrderCount;
        this.status = BrokerOrderExecutionGrantStatus.ACTIVE;
        this.consentedAt = LocalDateTime.now();
        this.consentVersion = consentVersion;
        this.createdAt = consentedAt;
        this.updatedAt = consentedAt;
    }

    /** 사용자가 언제든 누를 수 있는 킬스위치다. 이미 PAUSED/REVOKED여도 안전하게 다시 호출할 수 있다. */
    public void pause() {
        if (status == BrokerOrderExecutionGrantStatus.REVOKED) {
            throw new IllegalStateException("철회된 동의는 일시정지할 수 없습니다.");
        }
        this.status = BrokerOrderExecutionGrantStatus.PAUSED;
        this.updatedAt = LocalDateTime.now();
    }

    /** 철회는 종단 상태다 - 같은 행을 다시 ACTIVE로 되돌릴 수 없다. */
    public void revoke() {
        this.status = BrokerOrderExecutionGrantStatus.REVOKED;
        this.updatedAt = LocalDateTime.now();
    }

    /** PAUSED에서만 재개할 수 있다. REVOKED는 재동의(신규 행)가 필요하다. */
    public void reactivate() {
        if (status != BrokerOrderExecutionGrantStatus.PAUSED) {
            throw new IllegalStateException("일시정지 상태에서만 재개할 수 있습니다.");
        }
        this.status = BrokerOrderExecutionGrantStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public BrokerConnection getBrokerConnection() {
        return brokerConnection;
    }

    public String getStrategyId() {
        return strategyId;
    }

    public BigDecimal getMaxPositionSizePerOrderPercent() {
        return maxPositionSizePerOrderPercent;
    }

    public int getMaxDailyOrderCount() {
        return maxDailyOrderCount;
    }

    public BrokerOrderExecutionGrantStatus getStatus() {
        return status;
    }

    public LocalDateTime getConsentedAt() {
        return consentedAt;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
