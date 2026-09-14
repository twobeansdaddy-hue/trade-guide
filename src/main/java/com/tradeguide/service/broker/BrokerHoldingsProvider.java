package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerHoldingSnapshot;
import com.tradeguide.domain.broker.BrokerProvider;

/**
 * 증권사 보유 종목을 읽기 전용으로 조회하는 제공자 계약이다.
 * 자격 증명과 계좌 일련번호는 호출 시점에만 전달되는 복호화 값이며,
 * 구현체는 이를 저장하거나 로그에 남기지 않는다.
 *
 * <p>자격 증명은 제공자마다 항목 수가 달라 {@link BrokerCredentials}로 받는다. 계좌 일련번호는
 * 자격 증명이 아니라 계좌 식별자이므로 분리된 인자로 남긴다.
 */
public interface BrokerHoldingsProvider {

    BrokerProvider getProvider();

    BrokerHoldingSnapshot fetchHoldings(BrokerCredentials credentials, String accountSequence);
}
