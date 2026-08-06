package com.tradeguide.exception;

import com.tradeguide.domain.trade.Market;

public class AssetProfileAlreadyExistsException extends RuntimeException {

    public AssetProfileAlreadyExistsException(
            Market market,
            String ticker
    ) {
        super("이미 등록된 전략 프로필입니다: "
                + market + " / " + ticker);
    }
}