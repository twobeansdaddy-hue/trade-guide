package com.tradeguide.dto.strategy;

import com.tradeguide.domain.trade.Market;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 포트폴리오 후보 종목 등록 요청이다. 등록 가능한 필드는 시장, 티커, 표시명뿐이며 투자
 * 트랙은 항상 Track A로 고정된다.
 */
public class PortfolioCandidateAssetCreateRequest {

    @NotNull(message = "시장은 필수입니다.")
    private final Market market;

    @NotBlank(message = "티커는 필수입니다.")
    private final String ticker;

    @NotBlank(message = "표시명은 필수입니다.")
    private final String displayName;

    public PortfolioCandidateAssetCreateRequest(
            Market market,
            String ticker,
            String displayName
    ) {
        this.market = market;
        this.ticker = ticker;
        this.displayName = displayName;
    }

    public Market getMarket() {
        return market;
    }

    public String getTicker() {
        return ticker;
    }

    public String getDisplayName() {
        return displayName;
    }
}
