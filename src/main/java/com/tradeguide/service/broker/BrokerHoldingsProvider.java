package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;

/**
 * 증권사 보유 종목을 읽기 전용으로 조회하는 제공자 계약이다.
 * 자격 증명과 계좌 일련번호는 호출 시점에만 전달되는 복호화 값이며,
 * 구현체는 이를 저장하거나 로그에 남기지 않는다.
 */
public interface BrokerHoldingsProvider {

    BrokerProvider getProvider();

    BrokerHoldingSnapshot fetchHoldings(String clientId, String clientSecret, String accountSequence);
}
