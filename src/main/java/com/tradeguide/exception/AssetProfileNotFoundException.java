package com.tradeguide.exception;

import com.tradeguide.domain.trade.Market;

public class AssetProfileNotFoundException extends RuntimeException {

    public AssetProfileNotFoundException(Market market, String ticker) {
        super("전략 프로필을 찾을 수 없습니다: " + market + " / " + ticker);
    }
}