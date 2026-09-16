package com.tradeguide.service.strategy;

import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.market.UsEquityTradingCalendar;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.*;

class PremarketGuideSchedulerTest {

    @Test
    void generatesGuideForEachPortfolioOnWeekday() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        PortfolioRepository repository = mock(PortfolioRepository.class);
        PremarketGuideService service = mock(PremarketGuideService.class);
        Portfolio portfolio = mock(Portfolio.class);
        Member member = mock(Member.class);
        when(portfolio.getId()).thenReturn(10L);
        when(portfolio.getMember()).thenReturn(member);
        when(member.getId()).thenReturn(1L);
        when(repository.findAllWithMember()).thenReturn(List.of(portfolio));

        PremarketGuideScheduler scheduler = new PremarketGuideScheduler(
                clock, repository, service, new UsEquityTradingCalendar());
        scheduler.generateDailyGuides();

        verify(service).generateToday(1L, 10L, false);
    }

    @Test
    void skipsWeekend() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
        PortfolioRepository repository = mock(PortfolioRepository.class);
        PremarketGuideService service = mock(PremarketGuideService.class);

        PremarketGuideScheduler scheduler = new PremarketGuideScheduler(
                clock, repository, service, new UsEquityTradingCalendar());
        scheduler.generateDailyGuides();

        verifyNoInteractions(repository, service);
    }

    @Test
    void continuesWithOtherPortfoliosWhenOneGenerationFails() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
        PortfolioRepository repository = mock(PortfolioRepository.class);
        PremarketGuideService service = mock(PremarketGuideService.class);
        Portfolio failedPortfolio = mock(Portfolio.class);
        Portfolio successfulPortfolio = mock(Portfolio.class);
        Member failedMember = mock(Member.class);
        Member successfulMember = mock(Member.class);
        when(failedPortfolio.getId()).thenReturn(10L);
        when(failedPortfolio.getMember()).thenReturn(failedMember);
        when(failedMember.getId()).thenReturn(1L);
        when(successfulPortfolio.getId()).thenReturn(20L);
        when(successfulPortfolio.getMember()).thenReturn(successfulMember);
        when(successfulMember.getId()).thenReturn(2L);
        when(repository.findAllWithMember()).thenReturn(List.of(failedPortfolio, successfulPortfolio));
        doThrow(new RuntimeException("시세 제공자 오류"))
                .when(service).generateToday(1L, 10L, false);

        PremarketGuideScheduler scheduler = new PremarketGuideScheduler(
                clock, repository, service, new UsEquityTradingCalendar());
        scheduler.generateDailyGuides();

        verify(service).generateToday(1L, 10L, false);
        verify(service).generateToday(2L, 20L, false);
    }
}
