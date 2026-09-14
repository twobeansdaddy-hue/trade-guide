package com.tradeguide.service.strategy;

import com.tradeguide.domain.market.CandleInterval;
import com.tradeguide.domain.market.MarketCandle;
import com.tradeguide.domain.member.Member;
import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.domain.strategy.*;
import com.tradeguide.domain.trade.Market;
import com.tradeguide.exception.MarketDataUnavailableException;
import com.tradeguide.repository.strategy.AssetProfileRepository;
import com.tradeguide.repository.strategy.PortfolioCandidateAssetRepository;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.holding.HoldingService;
import com.tradeguide.service.market.CompletedWeeklyCandleCache;
import com.tradeguide.service.market.CompletedWeeklyCandleFilter;
import com.tradeguide.service.market.MarketHistoryService;
import com.tradeguide.service.market.WeeklyCandleFreshnessValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 전역 {@link AssetProfile} 카탈로그와 실제 {@link StrategyGuideService}를 함께 조립해,
 * 포트폴리오 후보로만 등록된 티커(전역 카탈로그에는 없는 티커)도 후보 가이드 API가
 * 404 전략 프로필 오류 없이 가이드 또는 외부 시세 오류 상태를 반환하는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PortfolioCandidateStrategyGuideServiceIntegrationTest {

    private static final int WEEKLY_CANDLE_OUTPUT_SIZE = 101;

    @Mock
    private HoldingService holdingService;
    @Mock
    private AssetProfileRepository assetProfileRepository;
    @Mock
    private PortfolioCandidateAssetRepository portfolioCandidateAssetRepository;
    @Mock
    private PortfolioRepository portfolioRepository;
    @Mock
    private MarketHistoryService marketHistoryService;
    @Mock
    private StrategySelector strategySelector;
    @Mock
    private TradingStrategy tradingStrategy;
    @Mock
    private CompletedWeeklyCandleFilter completedWeeklyCandleFilter;
    @Mock
    private WeeklyCandleFreshnessValidator weeklyCandleFreshnessValidator;
    @Mock
    private CompletedWeeklyCandleCache completedWeeklyCandleCache;
    @Mock
    private StrategyDecisionMaker strategyDecisionMaker;

    private PortfolioCandidateStrategyGuideService portfolioCandidateStrategyGuideService;

    @BeforeEach
    void setUp() {
        StrategyGuideService strategyGuideService = new StrategyGuideService(
                assetProfileRepository,
                marketHistoryService,
                strategySelector,
                completedWeeklyCandleFilter,
                weeklyCandleFreshnessValidator,
                completedWeeklyCandleCache
        );

        portfolioCandidateStrategyGuideService = new PortfolioCandidateStrategyGuideService(
                holdingService,
                assetProfileRepository,
                portfolioCandidateAssetRepository,
                strategyGuideService,
                strategyDecisionMaker,
                portfolioRepository
        );
    }

    private PortfolioCandidateAsset tqqqCandidate() {
        Member member = new Member("owner@example.com", "owner");
        Portfolio portfolio = new Portfolio(member, "테스트 포트폴리오");

        return new PortfolioCandidateAsset(
                portfolio,
                Market.US,
                "TQQQ",
                "프로셰어스 울트라프로 QQQ",
                InvestmentTrack.TRACK_A,
                LocalDateTime.of(2026, 9, 1, 0, 0)
        );
    }

    @Test
    void returnsGuideForPortfolioOnlyCandidateAbsentFromGlobalAssetProfileCatalog() {
        when(holdingService.getHoldings(1L, 10L)).thenReturn(List.of());
        when(portfolioCandidateAssetRepository
                .findAllByPortfolio_IdAndInvestmentTrack(10L, InvestmentTrack.TRACK_A))
                .thenReturn(List.of(tqqqCandidate()));

        List<MarketCandle> fetchedCandles = List.of();
        List<MarketCandle> completedCandles = List.of();

        when(marketHistoryService.getCandles(
                Market.US,
                "TQQQ",
                CandleInterval.WEEKLY,
                WEEKLY_CANDLE_OUTPUT_SIZE
        )).thenReturn(fetchedCandles);

        when(completedWeeklyCandleCache.getOrLoad(
                eq(Market.US),
                eq("TQQQ"),
                eq(WEEKLY_CANDLE_OUTPUT_SIZE),
                any()
        )).thenAnswer(invocation -> invocation
                .<Supplier<List<MarketCandle>>>getArgument(3)
                .get()
        );

        when(completedWeeklyCandleFilter.filter(fetchedCandles))
                .thenReturn(completedCandles);

        when(strategySelector.select(InvestmentTrack.TRACK_A))
                .thenReturn(tradingStrategy);

        StrategySignal signal = new StrategySignal(
                new BigDecimal("90"),
                "테스트 시장 신호",
                new StrategyMetadata(
                        "test-strategy",
                        "test-v1",
                        LocalDate.of(2026, 8, 10)
                ),
                StrategyTrend.ABOVE_LONG_AVERAGE,
                StrategySignalEvent.NONE,
                2
        );

        when(tradingStrategy.decide(any(AssetProfile.class), eq(completedCandles)))
                .thenReturn(signal);

        StrategyDecision decision = new StrategyDecision(
                StrategyAction.BUY,
                "테스트 후보 판단",
                signal
        );

        when(strategyDecisionMaker.decideForCandidate(signal))
                .thenReturn(decision);

        StrategyGuideBatch result = portfolioCandidateStrategyGuideService
                .getCandidateStrategyGuides(1L, 10L);

        assertThat(result.getGuides()).hasSize(1);
        assertThat(result.getGuides().get(0).getTicker()).isEqualTo("TQQQ");
        assertThat(result.getGuides().get(0).getStrategyDecision()).isSameAs(decision);
        assertThat(result.getUnavailableAssets()).isEmpty();

        verify(assetProfileRepository, never())
                .findByMarketAndTicker(any(), any());
    }

    @Test
    void returnsMarketDataUnavailableWhenExternalQuoteFailsForCandidateAbsentFromGlobalCatalog() {
        when(holdingService.getHoldings(1L, 10L)).thenReturn(List.of());
        when(portfolioCandidateAssetRepository
                .findAllByPortfolio_IdAndInvestmentTrack(10L, InvestmentTrack.TRACK_A))
                .thenReturn(List.of(tqqqCandidate()));

        when(completedWeeklyCandleCache.getOrLoad(
                eq(Market.US),
                eq("TQQQ"),
                eq(WEEKLY_CANDLE_OUTPUT_SIZE),
                any()
        )).thenAnswer(invocation -> invocation
                .<Supplier<List<MarketCandle>>>getArgument(3)
                .get()
        );

        when(marketHistoryService.getCandles(
                Market.US,
                "TQQQ",
                CandleInterval.WEEKLY,
                WEEKLY_CANDLE_OUTPUT_SIZE
        )).thenThrow(new MarketDataUnavailableException(
                "시장 데이터 조회에 실패했습니다."
        ));

        StrategyGuideBatch result = portfolioCandidateStrategyGuideService
                .getCandidateStrategyGuides(1L, 10L);

        assertThat(result.getGuides()).isEmpty();
        assertThat(result.getUnavailableAssets()).hasSize(1);
        assertThat(result.getUnavailableAssets().get(0).getTicker()).isEqualTo("TQQQ");
        assertThat(result.getUnavailableAssets().get(0).getMessage())
                .isEqualTo("시장 데이터 조회에 실패했습니다.");
        assertThat(result.getUnavailableAssets().get(0).getReason())
                .isEqualTo(StrategyGuideUnavailableReason.MARKET_DATA_UNAVAILABLE);

        verify(assetProfileRepository, never())
                .findByMarketAndTicker(any(), any());
    }
}
