package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerConnectionCandidateAccount;
import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;

import java.util.List;

/**
 * 증권사 자격 증명을 검증하고 연결 가능한 계좌 목록을 조회하는 제공자 계약이다.
 * 자격 증명은 호출 시점에만 전달되는 복호화 값이며,
 * 구현체는 이를 저장하거나 로그에 남기지 않는다.
 *
 * <p>자격 증명을 개별 인자가 아니라 {@link BrokerCredentials}로 받는다. 제공자마다 요구하는
 * 항목 수가 다르므로, 고정 인자를 쓰면 두 번째 증권사를 추가할 때 이 인터페이스와 모든
 * 구현체를 함께 고쳐야 한다. 필요한 항목은 구현체가 자기 제공자 명세의 키로 꺼낸다.
 */
public interface BrokerConnectionVerifier {

    BrokerProvider getProvider();

    List<BrokerConnectionCandidateAccount> verify(BrokerCredentials credentials);
}
