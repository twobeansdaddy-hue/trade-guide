package com.tradeguide.domain.portfolio;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 포트폴리오가 소유하는 선택적 증권사 계좌 연결이다.
 * 링크는 읽기 전용 조회 대상만 지정하며, 주문이나 매매 기록 생성과는 무관하다.
 * 데이터베이스는 포트폴리오당 여러 링크를 허용하고, v1에서 하나만 유지하는 제약은
 * 서비스 계층이 검증한다.
 */
@Entity
@Table(name = "portfolio_broker_links")
public class PortfolioBrokerLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_connection_id", nullable = false)
    private BrokerConnection brokerConnection;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broker_account_id", nullable = false)
    private BrokerAccount brokerAccount;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PortfolioBrokerLink() {
    }

    public PortfolioBrokerLink(
            Portfolio portfolio,
            BrokerConnection brokerConnection,
            BrokerAccount brokerAccount,
            LocalDateTime linkedAt
    ) {
        if (portfolio == null || brokerConnection == null || brokerAccount == null || linkedAt == null) {
            throw new IllegalArgumentException("증권사 계좌 연결 정보가 올바르지 않습니다.");
        }

        this.portfolio = portfolio;
        this.brokerConnection = brokerConnection;
        this.brokerAccount = brokerAccount;
        this.linkedAt = linkedAt;
        this.updatedAt = linkedAt;
    }

    public void changeAccount(
            BrokerConnection brokerConnection,
            BrokerAccount brokerAccount,
            LocalDateTime updatedAt
    ) {
        if (brokerConnection == null || brokerAccount == null || updatedAt == null) {
            throw new IllegalArgumentException("증권사 계좌 연결 정보가 올바르지 않습니다.");
        }

        this.brokerConnection = brokerConnection;
        this.brokerAccount = brokerAccount;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public BrokerConnection getBrokerConnection() {
        return brokerConnection;
    }

    public BrokerAccount getBrokerAccount() {
        return brokerAccount;
    }

    public LocalDateTime getLinkedAt() {
        return linkedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
