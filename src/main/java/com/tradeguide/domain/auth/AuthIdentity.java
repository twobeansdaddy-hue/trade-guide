package com.tradeguide.domain.auth;

import com.tradeguide.domain.member.Member;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "auth_identities", uniqueConstraints = @UniqueConstraint(
        name = "uk_auth_identities_provider_subject",
        columnNames = {"provider", "provider_subject"}
))
public class AuthIdentity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuthProvider provider;

    @Column(name = "provider_subject", nullable = false)
    private String providerSubject;

    @Column(name = "provider_email", nullable = false)
    private String providerEmail;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuthIdentity() {
    }

    public AuthIdentity(Member member, AuthProvider provider, String providerSubject, String providerEmail) {
        this.member = member;
        this.provider = provider;
        this.providerSubject = providerSubject;
        this.providerEmail = providerEmail;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Member getMember() { return member; }
    public AuthProvider getProvider() { return provider; }
    public String getProviderSubject() { return providerSubject; }
    public String getProviderEmail() { return providerEmail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
