package com.tradeguide.domain.broker;

/**
 * 연결 화면이 자격 증명 입력 칸을 어떤 형태로 그려야 하는지 나타낸다.
 * 값 자체가 아니라 입력 방식만 설명하며, 저장된 자격 증명을 노출하지 않는다.
 */
public enum BrokerCredentialFieldType {
    /** 화면에 그대로 보여도 되는 식별자 입력이다. */
    TEXT,
    /** 화면에 표시하면 안 되는 비밀값 입력이다. 브라우저는 마스킹 입력으로 그린다. */
    SECRET
}
