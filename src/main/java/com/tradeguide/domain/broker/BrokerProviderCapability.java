package com.tradeguide.domain.broker;

/**
 * 증권사 제공자가 지원할 수 있는 개별 기능 단위다.
 * 이 enum 자체는 동적 UI가 알아야 하는 전체 기능 목록만 정의한다.
 *
 * <p>어떤 기능이 실제로 열려 있는지는 두 조건이 함께 정한다.
 * {@link BrokerProvider#getSupportedCapabilities()}의 선언과, 그 기능의 어댑터가 이 인스턴스에
 * 등록돼 있는지다. 둘의 교집합을
 * {@link com.tradeguide.service.broker.BrokerProviderRegistry#availableCapabilities}가 계산하고,
 * 제공자 카탈로그 응답의 {@code availableCapabilities}로 나간다.
 */
public enum BrokerProviderCapability {
    /** 자격 증명을 검증하고 연결 가능한 계좌 목록을 조회한다. */
    CONNECTION_VERIFICATION,
    /** 연결된 계좌의 읽기 전용 보유 종목 스냅샷을 조회한다. */
    HOLDING_SNAPSHOT,
    /**
     * 주문 이력을 가져와 명시적 승인 뒤 Trade Guide 매매 원장에 반영한다.
     * 가져오기 자체는 원장을 건드리지 않고, 반영은 항상 별도의 승인 경로를 지난다.
     */
    TRANSACTION_HISTORY_IMPORT,
    /**
     * 예수금/현금 잔고를 조회한다. 아직 어댑터 계약 자체가 없으므로, 제공자가 이 기능을
     * 선언하더라도 {@code BrokerProviderRegistry.availableCapabilities}에는 나타나지 않는다.
     */
    CASH_BALANCE,
    /**
     * 실제로 매수/매도 주문을 제출한다. opt-in 동의(`BrokerOrderExecutionGrant`)와
     * 서킷브레이커를 통과한 신호만 이 경로를 탄다 - `tradeguide.broker.order-execution.
     * live-enabled`가 꺼져 있으면(기본값) 어댑터가 등록돼 있어도 호출되지 않는다.
     * 아직 어떤 증권사도 어댑터를 등록하지 않았으므로, 제공자가 이 기능을 선언하더라도
     * {@code BrokerProviderRegistry.availableCapabilities}에는 나타나지 않는다
     * ({@code CASH_BALANCE}와 동일한 이유).
     */
    ORDER_SUBMISSION
}
