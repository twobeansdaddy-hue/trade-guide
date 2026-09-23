package com.tradeguide.domain.strategy;

/** 장전 가이드 후보군을 어디서 가져왔는지 나타낸다. */
public enum PremarketGuideCandidateSource {
    /** 포트폴리오에 사용자가 등록한 후보. */
    PORTFOLIO,
    /** 포트폴리오 후보가 없어 전역 {@link AssetProfile} TRACK_A 카탈로그로 대체한 경우. */
    GLOBAL_CATALOG
}
