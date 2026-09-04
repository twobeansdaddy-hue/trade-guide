package com.tradeguide.domain.broker;

import com.tradeguide.domain.member.Member;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "broker_connections")
public class BrokerConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private BrokerProvider provider;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BrokerConnectionStatus status;

    @Column(name = "masked_account_label", length = 255)
    private String maskedAccountLabel;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToOne(
            mappedBy = "brokerConnection",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY,
            optional = false
    )
    private BrokerConnectionSecret secret;

    protected BrokerConnection() {
    }

    public BrokerConnection(Member member, BrokerProvider provider, String displayName) {
        if (member == null || provider == null || displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("증권사 연결 정보가 올바르지 않습니다.");
        }

        this.member = member;
        this.provider = provider;
        this.displayName = displayName;
        this.status = BrokerConnectionStatus.UNVERIFIED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = createdAt;
    }

    public void attachSecret(BrokerConnectionSecret secret) {
        if (secret == null) {
            throw new IllegalArgumentException("암호화된 증권사 자격 증명이 필요합니다.");
        }

        this.secret = secret;
        secret.assignBrokerConnection(this);
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public BrokerProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BrokerConnectionStatus getStatus() {
        return status;
    }

    public String getMaskedAccountLabel() {
        return maskedAccountLabel;
    }

    public LocalDateTime getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
