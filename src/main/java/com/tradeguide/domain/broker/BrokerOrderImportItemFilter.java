package com.tradeguide.domain.broker;

import java.util.Arrays;
import java.util.Locale;

/**
 * 실행 한 건의 주문 항목을 좁혀 읽기 위한 조건이다.
 *
 * <p>거르기는 서버에서 한다. 전부 내려보내고 화면에서 거르면 페이징을 나눈 의미가 사라진다.
 * 사용자가 "AAPL만" 또는 "반영 후보만" 보려 할 때도 여전히 수천 건이 오간다.
 *
 * <p>두 조건 모두 비워 둘 수 있고, 비면 그 조건은 적용하지 않는다. 알 수 없는 상태 값은
 * 조용히 무시하지 않고 잘못된 요청으로 알린다. 무시하면 호출자는 자기가 건 조건이 적용된
 * 결과를 받았다고 오해한다.
 */
public record BrokerOrderImportItemFilter(BrokerOrderStagingStatus stagingStatus, String ticker) {

    public static final BrokerOrderImportItemFilter NONE = new BrokerOrderImportItemFilter(null, null);

    /**
     * 요청 파라미터를 조건으로 바꾼다. 종목 코드는 대소문자를 가리지 않도록 대문자로 맞춰 두고
     * 저장소도 같은 형태로 비교한다.
     */
    public static BrokerOrderImportItemFilter of(String status, String symbol) {
        return new BrokerOrderImportItemFilter(parseStatus(status), normalizeSymbol(symbol));
    }

    private static BrokerOrderStagingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return Arrays.stream(BrokerOrderStagingStatus.values())
                .filter(value -> value.name().equalsIgnoreCase(status.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 주문 항목 상태입니다."));
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
