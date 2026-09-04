package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.repository.broker.BrokerConnectionRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioBrokerLinkServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private BrokerConnectionRepository brokerConnectionRepository;

    @Mock
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    private PortfolioBrokerLinkService portfolioBrokerLinkService;

    private Member member;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolioBrokerLinkService = new PortfolioBrokerLinkService(
                portfolioRepository,
                brokerConnectionRepository,
                portfolioBrokerLinkRepository,
                FIXED_CLOCK
        );

        member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);
    }

    @Test
    void listsOnlyVerifiedConnectionAccountsAsCandidates() {
        BrokerConnection verified = connectionWithAccounts(1L, "검증 완료", 100L);
        BrokerConnection unverified = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "검증 전");
        ReflectionTestUtils.setField(unverified, "id", 2L);

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(brokerConnectionRepository.findAllByMember_IdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(verified, unverified));

        List<PortfolioBrokerLinkService.BrokerLinkCandidate> candidates =
                portfolioBrokerLinkService.getLinkCandidates(10L, 20L);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.getFirst().connection().getId()).isEqualTo(1L);
        assertThat(candidates.getFirst().account().getMaskedAccountNumber()).isEqualTo("*****1234");
    }

    @Test
    void createsLinkForVerifiedAccount() {
        BrokerConnection connection = connectionWithAccounts(1L, "개인 토스증권", 100L);

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(brokerConnectionRepository.findByMember_IdAndId(10L, 1L)).thenReturn(Optional.of(connection));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.empty());
        when(portfolioBrokerLinkRepository.save(any(PortfolioBrokerLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioBrokerLink link = portfolioBrokerLinkService.linkBrokerAccount(10L, 20L, 1L, 100L);

        assertThat(link.getPortfolio()).isSameAs(portfolio);
        assertThat(link.getBrokerConnection()).isSameAs(connection);
        assertThat(link.getBrokerAccount().getId()).isEqualTo(100L);
        assertThat(link.getLinkedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 0, 0));
    }

    @Test
    void replacesExistingLinkBecauseV1KeepsOneLinkPerPortfolio() {
        BrokerConnection connection = connectionWithAccounts(1L, "개인 토스증권", 100L, 101L);
        BrokerAccount firstAccount = connection.getAccounts().getFirst();
        PortfolioBrokerLink existing = new PortfolioBrokerLink(
                portfolio, connection, firstAccount, LocalDateTime.of(2026, 9, 1, 0, 0)
        );

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(brokerConnectionRepository.findByMember_IdAndId(10L, 1L)).thenReturn(Optional.of(connection));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.of(existing));
        when(portfolioBrokerLinkRepository.save(any(PortfolioBrokerLink.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioBrokerLink link = portfolioBrokerLinkService.linkBrokerAccount(10L, 20L, 1L, 101L);

        assertThat(link).isSameAs(existing);
        assertThat(link.getBrokerAccount().getId()).isEqualTo(101L);
        assertThat(link.getLinkedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(link.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 4, 0, 0));
    }

    @Test
    void rejectsLinkingUnverifiedConnection() {
        BrokerConnection unverified = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "검증 전");
        ReflectionTestUtils.setField(unverified, "id", 2L);

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(brokerConnectionRepository.findByMember_IdAndId(10L, 2L)).thenReturn(Optional.of(unverified));

        assertThatThrownBy(() -> portfolioBrokerLinkService.linkBrokerAccount(10L, 20L, 2L, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("검증되지 않은 증권사 연결은 포트폴리오에 연결할 수 없습니다.");

        verify(portfolioBrokerLinkRepository, never()).save(any(PortfolioBrokerLink.class));
    }

    @Test
    void rejectsAccountThatBelongsToAnotherConnection() {
        BrokerConnection connection = connectionWithAccounts(1L, "개인 토스증권", 100L);

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(brokerConnectionRepository.findByMember_IdAndId(10L, 1L)).thenReturn(Optional.of(connection));

        assertThatThrownBy(() -> portfolioBrokerLinkService.linkBrokerAccount(10L, 20L, 1L, 999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("증권사 계좌를 찾을 수 없습니다.");

        verify(portfolioBrokerLinkRepository, never()).save(any(PortfolioBrokerLink.class));
    }

    @Test
    void rejectsBrokerConnectionOwnedByAnotherMember() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(brokerConnectionRepository.findByMember_IdAndId(10L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerLinkService.linkBrokerAccount(10L, 20L, 7L, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("증권사 연결 정보를 찾을 수 없습니다.");

        verify(portfolioBrokerLinkRepository, never()).save(any(PortfolioBrokerLink.class));
    }

    @Test
    void rejectsPortfolioOwnedByAnotherMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerLinkService.linkBrokerAccount(99L, 20L, 1L, 100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.");

        verify(brokerConnectionRepository, never()).findByMember_IdAndId(any(), any());
    }

    @Test
    void unlinksExistingLink() {
        BrokerConnection connection = connectionWithAccounts(1L, "개인 토스증권", 100L);
        PortfolioBrokerLink existing = new PortfolioBrokerLink(
                portfolio, connection, connection.getAccounts().getFirst(), LocalDateTime.of(2026, 9, 1, 0, 0)
        );

        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_IdAndBrokerConnection_Id(20L, 1L))
                .thenReturn(Optional.of(existing));

        portfolioBrokerLinkService.unlinkBrokerAccount(10L, 20L, 1L);

        verify(portfolioBrokerLinkRepository).delete(existing);
    }

    private BrokerConnection connectionWithAccounts(Long connectionId, String displayName, Long... accountIds) {
        BrokerConnection connection = new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, displayName);
        ReflectionTestUtils.setField(connection, "id", connectionId);

        List<BrokerAccount> accounts = List.of(accountIds).stream()
                .map(accountId -> {
                    BrokerAccount account = new BrokerAccount(
                            "encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1
                    );
                    ReflectionTestUtils.setField(account, "id", accountId);
                    return account;
                })
                .toList();

        connection.replaceAccounts(accounts);
        connection.markConnected("*****1234");
        return connection;
    }
}
