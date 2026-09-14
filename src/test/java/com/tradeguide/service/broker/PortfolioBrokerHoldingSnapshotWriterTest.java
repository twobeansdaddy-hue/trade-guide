package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerAccount;
import com.tradeguide.domain.broker.BrokerConnection;
import com.tradeguide.domain.broker.BrokerHolding;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;
import com.tradeguide.domain.broker.PortfolioBrokerHoldingSnapshot;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.portfolio.PortfolioBrokerLink;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerConnectionReverificationRequiredException;
import com.tradeguide.exception.PortfolioBrokerLinkNotFoundException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.PortfolioBrokerHoldingSnapshotRepository;
import com.tradeguide.repository.broker.PortfolioBrokerLinkRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 스냅샷 쓰기 트랜잭션의 경계를 고정한다.
 *
 * <p>이 라이터는 증권사 호출이 <b>끝난 뒤에만</b> 열리는 유일한 쓰기 지점이다. 그래서 조회
 * 시점과 저장 시점 사이에 벌어진 변화를 여기서 다시 확인해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioBrokerHoldingSnapshotWriterTest {

    private static final LocalDateTime SYNCED_AT = LocalDateTime.of(2026, 9, 4, 9, 30);

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private PortfolioBrokerLinkRepository portfolioBrokerLinkRepository;

    @Mock
    private PortfolioBrokerHoldingSnapshotRepository portfolioBrokerHoldingSnapshotRepository;

    private PortfolioBrokerHoldingSnapshotWriter portfolioBrokerHoldingSnapshotWriter;

    private Member member;
    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolioBrokerHoldingSnapshotWriter = new PortfolioBrokerHoldingSnapshotWriter(
                portfolioRepository,
                portfolioBrokerLinkRepository,
                portfolioBrokerHoldingSnapshotRepository
        );

        member = new Member("broker@example.com", "broker-user");
        ReflectionTestUtils.setField(member, "id", 10L);
        portfolio = new Portfolio(member, "성장 포트폴리오");
        ReflectionTestUtils.setField(portfolio, "id", 20L);
    }

    /**
     * 쓰기 트랜잭션은 선언으로만 존재한다. 애너테이션이 사라지면 스냅샷 헤더와 항목이 각각의
     * 트랜잭션으로 흩어지고, 항목 저장이 실패해도 헤더만 남는다.
     */
    @Test
    void declaresAWriteTransactionSoTheHeaderAndItemsCommitTogether() throws NoSuchMethodException {
        Transactional transactional = PortfolioBrokerHoldingSnapshotWriter.class
                .getMethod("save", PortfolioBrokerHoldingSnapshotWriter.SaveRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void persistsTheFetchedHoldingsAsOneSnapshot() {
        BrokerConnection connection = verifiedConnection();
        givenLink(connection);
        when(portfolioBrokerHoldingSnapshotRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PortfolioBrokerHoldingSnapshot saved = portfolioBrokerHoldingSnapshotWriter.save(saveRequest(
                1L,
                100L,
                new BrokerHoldingSnapshot(List.of(
                        new BrokerHolding(Market.US, "SOXL", new BigDecimal("30"), new BigDecimal("20.00"))
                ), 2)
        ));

        assertThat(saved.getPortfolio()).isSameAs(portfolio);
        assertThat(saved.getBrokerConnection()).isSameAs(connection);
        assertThat(saved.getBrokerAccount()).isSameAs(connection.getAccounts().getFirst());
        assertThat(saved.getSyncedAt()).isEqualTo(SYNCED_AT);
        assertThat(saved.getUnsupportedMarketCount()).isEqualTo(2);
        assertThat(saved.getItems()).extracting("ticker").containsExactly("SOXL");
    }

    /**
     * 조회하는 동안 링크가 다른 연결로 바뀌면 저장하지 않는다. 확인 없이 저장하면 A 연결에서
     * 가져온 보유 종목이 B 연결의 스냅샷으로 남고, 그 오염은 개시 잔고 반영까지 번진다.
     */
    @Test
    void refusesWhenTheLinkNowPointsToAnotherConnection() {
        givenLink(verifiedConnection());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotWriter.save(saveRequest(
                999L, 100L, new BrokerHoldingSnapshot(List.of(), 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("증권사 계좌 연결이 바뀌었습니다");

        verify(portfolioBrokerHoldingSnapshotRepository, never()).save(any());
    }

    @Test
    void refusesWhenTheLinkNowPointsToAnotherAccount() {
        givenLink(verifiedConnection());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotWriter.save(saveRequest(
                1L, 999L, new BrokerHoldingSnapshot(List.of(), 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("증권사 계좌 연결이 바뀌었습니다");

        verify(portfolioBrokerHoldingSnapshotRepository, never()).save(any());
    }

    /** 조회하는 동안 연결이 재검증 대기로 떨어졌다면, 그 응답을 스냅샷으로 남기지 않는다. */
    @Test
    void refusesWhenTheConnectionIsNoLongerVerified() {
        BrokerConnection connection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        ReflectionTestUtils.setField(connection, "id", 1L);
        BrokerAccount account = new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1);
        ReflectionTestUtils.setField(account, "id", 100L);
        connection.reconcileVerifiedAccounts(List.of(account));
        givenLink(connection);

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotWriter.save(saveRequest(
                1L, 100L, new BrokerHoldingSnapshot(List.of(), 0))))
                .isInstanceOf(BrokerConnectionReverificationRequiredException.class)
                .hasMessage("증권사 연결을 다시 검증해야 합니다.")
                .extracting(exception -> ((BrokerConnectionReverificationRequiredException) exception).getCode())
                .isEqualTo(ApiErrorCode.BROKER_CONNECTION_REVERIFICATION_REQUIRED);

        verify(portfolioBrokerHoldingSnapshotRepository, never()).save(any());
    }

    @Test
    void refusesWithNotFoundWhenThePortfolioDoesNotExist() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotWriter.save(saveRequest(
                1L, 100L, new BrokerHoldingSnapshot(List.of(), 0))))
                .isInstanceOf(PortfolioNotFoundException.class)
                .hasMessage("포트폴리오를 찾을 수 없습니다.")
                .extracting(exception -> ((PortfolioNotFoundException) exception).getCode())
                .isEqualTo(ApiErrorCode.PORTFOLIO_NOT_FOUND);

        verify(portfolioBrokerHoldingSnapshotRepository, never()).save(any());
    }

    @Test
    void refusesWhenThePortfolioLinkDisappearedWhileTheBrokerCallWasInFlight() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portfolioBrokerHoldingSnapshotWriter.save(saveRequest(
                1L, 100L, new BrokerHoldingSnapshot(List.of(), 0))))
                .isInstanceOf(PortfolioBrokerLinkNotFoundException.class)
                .hasMessage("포트폴리오에 연결된 증권사 계좌가 없습니다.")
                .extracting(exception -> ((PortfolioBrokerLinkNotFoundException) exception).getCode())
                .isEqualTo(ApiErrorCode.PORTFOLIO_BROKER_LINK_NOT_FOUND);

        verify(portfolioBrokerHoldingSnapshotRepository, never()).save(any());
    }

    @Test
    void rejectsAnIncompleteSaveRequest() {
        assertThatThrownBy(() -> new PortfolioBrokerHoldingSnapshotWriter.SaveRequest(
                10L, 20L, 1L, 100L, SYNCED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("증권사 보유 종목 스냅샷 저장 요청이 올바르지 않습니다.");
    }

    private PortfolioBrokerHoldingSnapshotWriter.SaveRequest saveRequest(
            Long brokerConnectionId,
            Long brokerAccountId,
            BrokerHoldingSnapshot fetched
    ) {
        return new PortfolioBrokerHoldingSnapshotWriter.SaveRequest(
                10L, 20L, brokerConnectionId, brokerAccountId, SYNCED_AT, fetched);
    }

    private void givenLink(BrokerConnection connection) {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(portfolio));
        when(portfolioBrokerLinkRepository.findByPortfolio_Id(20L)).thenReturn(Optional.of(
                new PortfolioBrokerLink(
                        portfolio,
                        connection,
                        connection.getAccounts().getFirst(),
                        LocalDateTime.of(2026, 9, 1, 0, 0)
                )
        ));
    }

    private BrokerConnection verifiedConnection() {
        BrokerConnection connection =
                new BrokerConnection(member, BrokerProvider.TOSS_SECURITIES, "개인 토스증권");
        ReflectionTestUtils.setField(connection, "id", 1L);
        BrokerAccount account = new BrokerAccount("encrypted-sequence", "sequence-iv", "*****1234", "위탁", 1);
        ReflectionTestUtils.setField(account, "id", 100L);
        connection.reconcileVerifiedAccounts(List.of(account));
        connection.markConnected("*****1234");
        return connection;
    }
}
