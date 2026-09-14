package com.tradeguide.domain.asset;

/**
 * 자산 기준정보(AssetListing) 한 건이 최초로 만들어진 근거다.
 *
 * <p>{@code EXTERNAL_SEARCH}는 사용자가 수동으로 매매를 등록할 때 외부 종목
 * 검색에서 정확히 일치하는 결과를 찾아 생성한 경우다. {@code BROKER_SNAPSHOT}은
 * 연결된 증권사가 정상 보유 종목으로 반환한 market/ticker/displayName을 그대로
 * 신뢰해 생성한 경우다. 두 출처 모두 생성 이후에는 동등하게 거래에 사용할 수
 * 있는 활성 상장 종목이며, 이 값은 추적성을 위한 기록일 뿐 이후 조회·거래 로직을
 * 분기하지 않는다.
 */
public enum AssetListingSource {
    EXTERNAL_SEARCH,
    BROKER_SNAPSHOT
}
