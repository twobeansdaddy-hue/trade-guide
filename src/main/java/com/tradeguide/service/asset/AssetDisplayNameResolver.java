package com.tradeguide.service.asset;

import com.tradeguide.domain.trade.Market;
import com.tradeguide.repository.asset.AssetListingRepository;
import org.springframework.stereotype.Component;

/**
 * 전략 가이드·매매 계획 초안 응답에 종목의 한글 표시명을 함께 노출하기 위한 조회
 * 헬퍼다. {@code AssetListing}에 등록되지 않은 종목이면 티커를 그대로 표시명으로
 * 돌려주며, 이 조회 실패가 가이드·계획 자체의 계산이나 응답을 막지 않는다.
 */
@Component
public class AssetDisplayNameResolver {

    private final AssetListingRepository assetListingRepository;

    public AssetDisplayNameResolver(AssetListingRepository assetListingRepository) {
        this.assetListingRepository = assetListingRepository;
    }

    public String resolve(Market market, String ticker) {
        return assetListingRepository.findByMarketAndTicker(market, ticker)
                .map(listing -> listing.getDisplayName())
                .orElse(ticker);
    }
}
