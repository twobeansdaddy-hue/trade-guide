package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerCredentials;
import com.tradeguide.domain.broker.BrokerOrderHistoryPage;
import com.tradeguide.domain.broker.BrokerOrderHistoryQuery;
import com.tradeguide.domain.broker.BrokerProvider;

/**
 * 증권사 주문 이력을 읽기 전용으로 조회하는 제공자 계약이다.
 * 주문 원장을 그대로 읽을 뿐이며, 주문 제출·정정·취소는 이 계약에 존재하지 않는다.
 * 매매 원장({@code TradeTransaction})을 만들거나 바꾸지도 않는다.
 *
 * <p>자격 증명과 계좌 일련번호는 호출 시점에만 전달되는 복호화 값이다.
 * 구현체는 이를 저장·로그·예외 메시지에 남기지 않는다. 증권사 원문 응답도 마찬가지다.
 * 자격 증명은 제공자마다 항목 수가 달라 {@link BrokerCredentials}로 받고, 계좌 일련번호는
 * 계좌 식별자이므로 분리된 인자로 남긴다.
 *
 * <p>구현체는 제공자 코드 체계만 알고, 표현할 수 없는 주문은 조용히 버리지 않고
 * {@link com.tradeguide.domain.broker.BrokerOrderExclusionCounts}로 사유별 건수를 보고한다.
 */
public interface BrokerOrderHistoryProvider {

    BrokerProvider getProvider();

    /** 한 페이지만 조회한다. 커서 순회와 기간 분할은 호출자의 책임이다. */
    BrokerOrderHistoryPage fetchOrders(
            BrokerCredentials credentials,
            String accountSequence,
            BrokerOrderHistoryQuery query
    );
}
