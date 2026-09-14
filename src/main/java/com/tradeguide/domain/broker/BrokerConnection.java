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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /**
     * 제공자 명세가 요구하는 자격 증명 항목의 암호문들이다. 제공자마다 필드 수가 다르므로
     * 고정 열이 아니라 행 집합으로 담는다.
     */
    @OneToMany(
            mappedBy = "brokerConnection",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<BrokerConnectionSecretValue> secretValues = new ArrayList<>();

    @OneToMany(mappedBy = "brokerConnection", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BrokerAccount> accounts = new ArrayList<>();

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

    /**
     * 이 연결의 자격 증명 암호문을 통째로 교체한다.
     *
     * <p>항목 단위 부분 갱신을 두지 않는 이유는 자격 증명이 한 벌로만 의미가 있기 때문이다.
     * 절반만 새 값으로 바뀐 상태는 연결 검증에서야 실패하고, 그때는 어느 항목이 낡았는지
     * 알 수 없다. 같은 키가 두 번 들어오면 어느 값을 쓸지 정해지지 않으므로 거부한다.
     * DB의 {@code uk_broker_connection_secret_values_field}가 같은 규칙을 한 겹 더 지킨다.
     *
     * <p>키가 제공자 명세 안의 값인지는 여기서 판정하지 않는다. 명세 대조는
     * {@link com.tradeguide.service.broker.BrokerConnectionService}가 저장 전에 수행한다.
     */
    public void replaceSecretValues(List<BrokerConnectionSecretValue> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("암호화된 증권사 자격 증명이 필요합니다.");
        }

        Set<String> fieldKeys = new HashSet<>();
        values.forEach(value -> {
            if (!fieldKeys.add(value.getFieldKey())) {
                throw new IllegalArgumentException("같은 자격 증명 항목이 중복되었습니다: " + value.getFieldKey());
            }
        });

        this.secretValues.clear();
        values.forEach(value -> {
            value.assignBrokerConnection(this);
            this.secretValues.add(value);
        });
        this.updatedAt = LocalDateTime.now();
    }

    public void markConnected(String maskedAccountLabel) {
        this.status = BrokerConnectionStatus.CONNECTED;
        this.maskedAccountLabel = maskedAccountLabel;
        this.lastVerifiedAt = LocalDateTime.now();
        this.updatedAt = lastVerifiedAt;
    }

    /**
     * 검증 결과에 맞춰 계좌 목록을 정리한다.
     *
     * <p>계좌 행은 절대 삭제하지 않는다. 과거 보유 종목 스냅샷과 개시 잔고 승인 이력이
     * 계좌 식별자를 참조하므로, 행을 지우면 참조 무결성이 깨지고 재검증이 실패한다.
     * 이번 검증에서 증권사가 반환하지 않은 계좌는 {@link BrokerAccountStatus#DETACHED}로
     * 표시해 후보에서만 제외하고, 처음 보는 계좌만 새 행으로 추가한다.
     *
     * @param providerAccounts 이번 검증에서 증권사가 반환한 계좌들. 다시 발견된 기존 계좌는
     *                         이미 최신 값으로 갱신된 같은 인스턴스여야 한다.
     */
    public void reconcileVerifiedAccounts(List<BrokerAccount> providerAccounts) {
        if (providerAccounts == null) {
            throw new IllegalArgumentException("증권사 계좌 검증 결과가 필요합니다.");
        }

        LocalDateTime reconciledAt = LocalDateTime.now();

        this.accounts.stream()
                .filter(existing -> providerAccounts.stream().noneMatch(current -> current == existing))
                .forEach(detached -> detached.detach(reconciledAt));

        List<BrokerAccount> discovered = providerAccounts.stream()
                .filter(current -> this.accounts.stream().noneMatch(existing -> existing == current))
                .toList();
        discovered.forEach(account -> {
            account.assignBrokerConnection(this);
            this.accounts.add(account);
        });

        this.updatedAt = reconciledAt;
    }

    /** 암호문만 반환한다. 복호화는 {@code BrokerCredentialLoader} 한곳에서만 수행한다. */
    public List<BrokerConnectionSecretValue> getSecretValues() {
        return List.copyOf(secretValues);
    }

    /** 분리된 계좌를 포함한 전체 계좌다. 과거 이력 조회와 연결 삭제 경로에서만 사용한다. */
    public List<BrokerAccount> getAccounts() { return List.copyOf(accounts); }

    /** 가장 최근 검증에서 증권사가 반환한 계좌만 반환한다. 연결 후보와 신규 링크는 이 목록만 사용한다. */
    public List<BrokerAccount> getActiveAccounts() {
        return accounts.stream().filter(BrokerAccount::isActive).toList();
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
