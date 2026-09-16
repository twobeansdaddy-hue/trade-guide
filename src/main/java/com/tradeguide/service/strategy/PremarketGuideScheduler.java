package com.tradeguide.service.strategy;

import com.tradeguide.domain.portfolio.Portfolio;
import com.tradeguide.repository.portfolio.PortfolioRepository;
import com.tradeguide.service.market.UsEquityTradingCalendar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 운영 환경에서 장전 가이드 생성을 시작하는 얇은 스케줄러다.
 *
 * <p>기본값은 비활성이다. 외부 시세 제공자 호출량과 거래소 휴장일 정책을 운영 환경에서
 * 확인한 뒤 {@code TRADEGUIDE_PREMARKET_SCHEDULER_ENABLED=true}로 켠다. 수동 생성 API는
 * 이 설정과 무관하게 사용할 수 있다.
 */
@Component
@ConditionalOnProperty(
        name = "tradeguide.premarket.scheduler.enabled",
        havingValue = "true"
)
public class PremarketGuideScheduler {

    private static final Logger log = LoggerFactory.getLogger(PremarketGuideScheduler.class);
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private final Clock clock;
    private final PortfolioRepository portfolioRepository;
    private final PremarketGuideService premarketGuideService;
    private final UsEquityTradingCalendar tradingCalendar;

    public PremarketGuideScheduler(
            Clock clock,
            PortfolioRepository portfolioRepository,
            PremarketGuideService premarketGuideService,
            UsEquityTradingCalendar tradingCalendar
    ) {
        this.clock = clock;
        this.portfolioRepository = portfolioRepository;
        this.premarketGuideService = premarketGuideService;
        this.tradingCalendar = tradingCalendar;
    }

    @Scheduled(
            cron = "${tradeguide.premarket.scheduler.cron:0 0 8 * * MON-FRI}",
            zone = "America/New_York"
    )
    public void generateDailyGuides() {
        LocalDate marketDate = clock.instant().atZone(MARKET_ZONE).toLocalDate();
        if (!tradingCalendar.isTradingDay(marketDate)) {
            return;
        }

        portfolioRepository.findAllWithMember().forEach(portfolio -> {
            try {
                premarketGuideService.generateToday(
                        portfolio.getMember().getId(),
                        portfolio.getId(),
                        false
                );
            } catch (RuntimeException exception) {
                // 한 포트폴리오의 데이터 제공자 오류가 다른 포트폴리오 실행을 막지 않게 한다.
                log.warn(
                        "장전 가이드 생성에 실패했습니다. portfolioId={}",
                        portfolio.getId(),
                        exception
                );
            }
        });
    }
}
