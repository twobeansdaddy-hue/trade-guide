package com.tradeguide.domain.portfolio;

import com.tradeguide.domain.member.Member;
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
}
