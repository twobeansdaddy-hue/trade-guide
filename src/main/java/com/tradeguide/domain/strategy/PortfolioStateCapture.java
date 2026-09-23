package com.tradeguide.domain.strategy;

/** 장전 가이드 생성 시 포트폴리오 결정 입력을 어떤 상태로 고정했는지 나타낸다. */
public enum PortfolioStateCapture {
    /** 상태 스냅샷을 기록하지 않았다(기존 가이드 등). */
    NOT_CAPTURED,
    /** 생성 시작 시 읽은 상태와 저장 직전 상태가 같다. */
    CAPTURED,
    /** 생성 도중 포트폴리오 상태가 바뀌었다. 가이드는 시작 시 상태로 일관되게 계산됐지만 저장 시점과 다르다. */
    CHANGED_DURING_GENERATION
}
