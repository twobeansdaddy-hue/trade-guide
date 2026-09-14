package com.tradeguide.service.broker;

import com.tradeguide.domain.broker.BrokerOrderRecord;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 주문 내용의 지문을 만든다. 주문 식별자를 <b>대체</b>하는 키가 아니라 <b>교차 검증</b>하는 값이다.
 *
 * <p>제공자가 재조회마다 다른 주문 식별자를 주더라도 내용은 같다. 그래서 "식별자는 다른데 지문이
 * 같은" 주문을 찾으면 식별자 불안정을 원장이 오염되기 전에 잡아낼 수 있다. 반대로 지문을 키로
 * 쓰면 같은 초에 난 서로 다른 주문 두 건을 하나로 합쳐 버리므로, 키로는 쓰지 않는다.
 *
 * <p>수량·금액은 스케일이 달라도 같은 값이면 같은 지문이 나와야 한다. {@code 1.50}과 {@code 1.5}가
 * 다른 지문을 낳으면 제공자가 자릿수 표기만 바꿔도 전부 다른 주문으로 보인다.
 * 그래서 뒤 0을 떼고 정규화한 뒤 해싱한다.
 */
final class BrokerOrderFingerprint {

    private static final String ALGORITHM = "SHA-256";
    /** 값 안에 나타나지 않는 제어 문자라야 "AB|C"와 "A|BC"가 같은 지문이 되는 일이 없다. */
    private static final String FIELD_SEPARATOR = "\u001F";
    /** 빈 값과 빈 문자열을 구분한다. */
    private static final String NULL_MARKER = "\u0000";

    private BrokerOrderFingerprint() {
    }

    static String of(BrokerOrderRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("지문을 만들 주문이 필요합니다.");
        }

        String canonical = String.join(
                FIELD_SEPARATOR,
                text(record.side() == null ? null : record.side().name()),
                text(record.ticker()),
                text(record.currencyCode()),
                text(record.providerOrderType()),
                text(record.providerStatusCode()),
                number(record.filledQuantity()),
                number(record.averageFilledPrice()),
                number(record.filledAmount()),
                text(record.filledAt() == null ? null : record.filledAt().toString()),
                text(record.orderedAt() == null ? null : record.orderedAt().toString())
        );

        return hash(canonical);
    }

    private static String hash(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("주문 지문 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private static String text(String value) {
        return value == null ? NULL_MARKER : value;
    }

    private static String number(BigDecimal value) {
        if (value == null) {
            return NULL_MARKER;
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
