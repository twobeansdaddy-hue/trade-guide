package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerProvider;

/**
 * 증권사에 실제로 주문을 제출하는 제공자 계약이다.
 *
 * <p>이 슬라이스는 이 인터페이스만 정의한다 - Toss 구현체는 별도 후속 계약
 * (`docs/agent-tasks/claude-broker-order-execution-dry-run-implementation-20260917.md`
 * "다음 슬라이스" 참고)이 만든다. {@code BrokerOrderExecutionService}는
 * `tradeguide.broker.order-execution.live-enabled`가 꺼져 있는 한(기본값) 이
 * 인터페이스를 호출하지 않는다.
 *
 * <p>{@link BrokerOrderHistoryProvider}와 같은 원칙 - 자격 증명과 계좌 일련번호는
 * 호출 시점에만 전달되는 복호화 값이며, 구현체는 이를 저장·로그·예외 메시지에
 * 남기지 않는다.
 */
public interface BrokerOrderSubmissionProvider {

    BrokerProvider getProvider();

    BrokerOrderSubmissionResult submit(BrokerCredentials credentials, BrokerOrderSubmissionRequest request);
}
