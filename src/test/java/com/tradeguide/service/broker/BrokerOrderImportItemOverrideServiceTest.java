package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHistoryPage;
import com.tradeguide.domain.broker.BrokerHistoryPageRequest;
import com.tradeguide.domain.broker.BrokerOrderImportItemOverride;
import com.tradeguide.domain.broker.BrokerOrderImportRun;
import com.tradeguide.domain.broker.BrokerOrderOverrideDecision;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.dto.ApiErrorCode;
import com.tradeguide.exception.BrokerOrderImportNotFoundException;
import com.tradeguide.exception.BrokerOrderOverrideConflictException;
import com.tradeguide.exception.PortfolioNotFoundException;
import com.tradeguide.repository.broker.BrokerOrderImportItemOverrideRepository;
import com.tradeguide.repository.broker.BrokerOrderImportRunRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrokerOrderImportItemOverrideServiceTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private BrokerOrderImportRunRepository brokerOrderImportRunRepository;

    @Mock
    private BrokerOrderImportItemOverrideRepository brokerOrderImportItemOverrideRepository;

    @Mock
    private BrokerOrderImportItemOverrideWriter brokerOrderImportItemOverrideWriter;

    private BrokerOrderImportItemOverrideService service;

    @BeforeEach
    void setUp() {
        service = new BrokerOrderImportItemOverrideService(
                portfolioRepository,
                brokerOrderImportRunRepository,
                brokerOrderImportItemOverrideRepository,
                brokerOrderImportItemOverrideWriter
        );
    }

    /**
     * 같은 항목에 대한 동시 요청 중 진 쪽은 유니크 제약 위반으로 롤백된다. 이긴 쪽의 기록을 기준으로
     * 한 번만 다시 판단해, 같은 결정이면 그 기록을 돌려준다.
     */
    @Test
    void retriesOnceWhenAConcurrentOverrideWinsTheUniqueConstraintRace() {
        BrokerOrderImportItemOverrideWriter.OverrideResult existing =
                new BrokerOrderImportItemOverrideWriter.OverrideResult(mock(BrokerOrderImportItemOverride.class), false);
        when(brokerOrderImportItemOverrideWriter.create(
                10L, 20L, 5L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "사유"))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenReturn(existing);

        BrokerOrderImportItemOverrideWriter.OverrideResult result = service.createOverride(
                10L, 20L, 5L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "사유");

        assertThat(result).isSameAs(existing);
        verify(brokerOrderImportItemOverrideWriter, times(2)).create(
                10L, 20L, 5L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "사유");
    }

    /** 경합에서 이긴 쪽이 다른 결정이었다면 재시도는 충돌로 끝난다. 덮어쓰지 않는다. */
    @Test
    void reportsAConflictWhenTheConcurrentWinnerChoseADifferentDecision() {
        when(brokerOrderImportItemOverrideWriter.create(
                10L, 20L, 5L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "사유"))
                .thenThrow(new DataIntegrityViolationException("duplicate key"))
                .thenThrow(new BrokerOrderOverrideConflictException(
                        "이미 다른 결정으로 재판정됐습니다.", ApiErrorCode.ORDER_IMPORT_OVERRIDE_CONFLICT));

        assertThatThrownBy(() -> service.createOverride(
                10L, 20L, 5L, 102L, BrokerOrderOverrideDecision.ALLOW_LEDGER_WRITE, "사유"))
                .isInstanceOf(BrokerOrderOverrideConflictException.class);
    }

    @Test
    void readsTheOverrideHistoryNewestFirstWithinTheOwnedRun() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(mock(Portfolio.class)));
        BrokerOrderImportRun run = mock(BrokerOrderImportRun.class);
        when(run.getId()).thenReturn(5L);
        when(brokerOrderImportRunRepository.findByPortfolio_IdAndId(20L, 5L)).thenReturn(Optional.of(run));
        BrokerOrderImportItemOverride override = mock(BrokerOrderImportItemOverride.class);
        when(brokerOrderImportItemOverrideRepository.findAllByRun_Id(eq(5L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(override)));

        BrokerHistoryPage<BrokerOrderImportItemOverride> page =
                service.getOverrides(10L, 20L, 5L, BrokerHistoryPageRequest.of(0, 20));

        assertThat(page.items()).containsExactly(override);
        assertThat(page.totalElements()).isEqualTo(1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(brokerOrderImportItemOverrideRepository).findAllByRun_Id(eq(5L), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    @Test
    void rejectsHistoryAccessToAPortfolioNotOwnedByTheMember() {
        when(portfolioRepository.findByMember_IdAndId(99L, 20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOverrides(99L, 20L, 5L, BrokerHistoryPageRequest.of(0, 20)))
                .isInstanceOf(PortfolioNotFoundException.class);

        verifyNoInteractions(brokerOrderImportRunRepository, brokerOrderImportItemOverrideRepository);
    }

    @Test
    void reportsHistoryOfARunOutsideThePortfolioAsNotFound() {
        when(portfolioRepository.findByMember_IdAndId(10L, 20L)).thenReturn(Optional.of(mock(Portfolio.class)));
        when(brokerOrderImportRunRepository.findByPortfolio_IdAndId(20L, 6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOverrides(10L, 20L, 6L, BrokerHistoryPageRequest.of(0, 20)))
                .isInstanceOf(BrokerOrderImportNotFoundException.class);

        verifyNoInteractions(brokerOrderImportItemOverrideRepository);
    }
}
